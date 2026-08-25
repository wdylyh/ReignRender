package com.wdylyh.mixin;

import com.wdylyh.config.Cfg;
import com.wdylyh.config.FR;
import com.wdylyh.config.Rpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class ERDisp {

    // Surrogate entities are cached per source entity and reused across frames.
    // Creating a fresh surrogate on every getAndUpdateRenderState call reset the
    // surrogate's age, RNG and animation state each frame, which made replaced
    // entities flicker. A ConcurrentHashMap keeps the cache safe if the render
    // state extraction is ever touched outside the render thread; the map is
    // capped defensively so stray removals can never grow it without bound.
    private static final Map<Entity, Entity> SUR = new ConcurrentHashMap<>();
    private static final int SUR_MAX = 2048;

    // Surrogate -> source reverse lookup. The RETURN injector (fixState) reads
    // it to correct the render state with the source entity's real dimensions.
    // Keying by the surrogate identity (the entity argument at RETURN holds the
    // surrogate, not the source) makes the pairing exact per call: no stale
    // value can leak out of the ThreadLocal when the target method throws.
    private static final Map<Entity, Entity> SRC = new ConcurrentHashMap<>();

    // Per-type blacklist/whitelist filter (registry id based).
    // shouldRender() is called with the concrete Entity before the render state
    // is created, so filtered entities are skipped as early as possible.
    // The filter only applies while the master "disable entities" toggle is on;
    // when it is off every entity renders normally. The independent
    // "disable falling blocks" toggle also stops falling block entities here.
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void shouldRender(Entity e, Frustum f,
                              double cx, double cy, double cz,
                              CallbackInfoReturnable<Boolean> cbr) {
        // The native GLFW keyboard query is the most expensive part of the
        // filter chain, so it is captured once per entity and passed down to
        // every filter overload below. The behavior codes (which re-read the
        // hotkey mode config per type) are likewise evaluated once here instead
        // of once per filter call.
        boolean kd = FR.revealDown();
        int bhv = FR.bhv(FR.TYPE_ENTITIES, kd);

        // Force-hide mode + hotkey held: hide every entity (players, our own
        // model and falling blocks included) regardless of the individual
        // toggles, so it short-circuits before the toggle-gated checks below.
        if (bhv == FR.HOTKEY_BEHAVIOR_FORCE) {
            cbr.setReturnValue(false);
            return;
        }

        // The cheaper FallingBlockEntity check comes first so it can short-circuit
        // before the expensive isEntityFiltered() lookup when both toggles are on.
        // shouldRender() receives the camera's absolute position, so the distance
        // filter compares it against the entity's position.
        // Item entities (drops) and falling blocks have their own dedicated
        // toggles, so the master "disable entities" toggle must not swallow them
        // (the OFF filter mode hides every entity, which would otherwise drag
        // item drops along with it).
        boolean sep = e instanceof ItemEntity || e instanceof FallingBlockEntity;

        if ((Cfg.Off.DISABLE_FALLING_BLOCKS.getBooleanValue() &&
                e instanceof FallingBlockEntity) ||
                (Cfg.Off.DISABLE_ITEM_ENTITIES.getBooleanValue() &&
                        e instanceof ItemEntity) ||
                (Cfg.Off.DISABLE_ENTITIES.getBooleanValue() &&
                        !sep &&
                        FR.isEntityFiltered(e.getType(), kd, bhv)) ||
                hideP(e, kd) ||
                FR.isEntityBeyondDistance(e, cx, cy, cz, kd, bhv)) {
            cbr.setReturnValue(false);
            return;
        }

        // Entity count cap: only counts entities that pass every other filter.
        if (FR.isEntityLimitExceeded(kd, bhv)) {
            cbr.setReturnValue(false);
        }
    }

    // Hides our own model and/or the other players' models. Identity comparison
    // makes remote players with the same display name still count as "other".
    // With the master "hide other players" toggle on, the player name filter
    // mode decides: OFF hides every other player, BLACKLIST hides the listed
    // names, WHITELIST only shows the listed names.
    private static boolean hideP(Entity e, boolean kd) {
        if (!(e instanceof PlayerEntity p)) {
            return false;
        }

        boolean self = p == MinecraftClient.getInstance().player;

        if (self) {
            return Cfg.Off.HIDE_SELF.getBooleanValue();
        }

        if (!Cfg.Off.HIDE_OTHER_PLAYERS.getBooleanValue()) {
            return false;
        }

        return FR.isPlayerHidden(p.getGameProfile().name(), kd);
    }

    // Universal entity replacement: the render state is built from a surrogate
    // entity of the target type, so both the state extraction
    // (getAndUpdateRenderState) and the renderer lookup (which is based on the
    // render state, not the original entity) use the target type's visuals. The
    // surrogate is created by EntityType.create, which already attaches the
    // world, and the original position and rotations are copied over so the
    // replacement sits in the same place and orientation.
    @ModifyVariable(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private Entity repEnt(Entity e) {
        if (!Rpl.isReplaceEnabled() || e == null) {
            return e;
        }
        // The reveal hotkey skips the replacement (shows the original entity).
        if (FR.isReplaceBlocked(FR.TYPE_ENTITIES)) {
            return e;
        }
        EntityType<?> srcT = e.getType();
        String tId = Rpl.getReplacementEntity(FR.getEntityId(srcT));
        if (tId == null) {
            return e;
        }
        EntityType<?> t = Rpl.getEntityTypeTarget(tId);
        if (t == null || t == srcT) {
            return e;
        }
        World w = e.getEntityWorld();
        if (w == null) {
            return e;
        }

        // Reuse the cached surrogate for this source entity; only (re)create it
        // when the replacement target changed (e.g. the rule list was edited).
        // A persistent surrogate keeps its internal animation/RNG state alive,
        // which a freshly created entity would reset on every frame.
        Entity sur = SUR.get(e);

        if (sur == null || !sur.getType().equals(t)) {
            sur = t.create(w, SpawnReason.COMMAND);

            if (sur == null) {
                return e;
            }

            if (SUR.size() > SUR_MAX) {
                SUR.clear();
                SRC.clear();
            }

            SUR.put(e, sur);
            SRC.put(sur, e);
        }

        // Copy the source entity's full visual state onto the surrogate. The
        // render state is extracted from the surrogate by the target type's
        // renderer, so every visual detail it reads must come from the source.
        // Position and rotations are copied with their previous-frame values:
        // the last* fields drive the renderer's per-frame interpolation, and
        // leaving them at 0 makes the replacement snap/jitter between frames.
        sur.setPos(e.getX(), e.getY(), e.getZ());
        sur.lastX = e.lastX;
        sur.lastY = e.lastY;
        sur.lastZ = e.lastZ;
        // The render state's interpolated position is built from
        // lastRenderX/Y/Z, NOT from lastX/Y/Z. Leaving the surrogate's
        // lastRender* fields behind made it interpolate from (0,0,0) towards
        // the current position, jittering and offsetting the model every frame.
        sur.lastRenderX = e.lastRenderX;
        sur.lastRenderY = e.lastRenderY;
        sur.lastRenderZ = e.lastRenderZ;
        sur.setYaw(e.getYaw());
        sur.setPitch(e.getPitch());
        sur.lastYaw = e.lastYaw;
        sur.lastPitch = e.lastPitch;
        sur.fallDistance = e.fallDistance;
        sur.setOnGround(e.isOnGround());
        sur.setSneaking(e.isSneaking());
        sur.setSprinting(e.isSprinting());
        sur.setSwimming(e.isSwimming());
        sur.setGlowing(e.isGlowing());
        sur.setInvisible(e.isInvisible());
        sur.setCustomName(e.getCustomName());
        sur.setCustomNameVisible(e.isCustomNameVisible());
        sur.setFireTicks(e.getFireTicks());
        sur.setPose(e.getPose());
        sur.age = e.age;

        // Living entity extras: hurt flash timing, hand swing animation and the
        // equipped items. The equipped stacks are shared with the surrogate (the
        // renderer only reads them), so the replacement wears exactly what the
        // source wears. updateLimbs() feeds the surrogate's limb animator from
        // the source entity so legs/pelvis animation stays alive.
        if (e instanceof LivingEntity liv && sur instanceof LivingEntity tgt) {
            tgt.hurtTime = liv.hurtTime;
            tgt.maxHurtTime = liv.maxHurtTime;
            tgt.deathTime = liv.deathTime;
            tgt.handSwingTicks = liv.handSwingTicks;
            tgt.handSwinging = liv.handSwinging;
            tgt.handSwingProgress = liv.handSwingProgress;
            tgt.lastHandSwingProgress = liv.lastHandSwingProgress;
            // Head and body rotation live on LivingEntity and include their own
            // previous-frame fields, which the renderer interpolates every frame.
            tgt.headYaw = liv.headYaw;
            tgt.lastHeadYaw = liv.lastHeadYaw;
            tgt.bodyYaw = liv.bodyYaw;
            tgt.lastBodyYaw = liv.lastBodyYaw;
            // The limb animator's internal phase is private, but the walking
            // speed it accumulates is readable, so the surrogate's legs at least
            // move at the same rhythm as the source's.
            tgt.limbAnimator.setSpeed(liv.limbAnimator.getSpeed());

            for (EquipmentSlot s : EquipmentSlot.values()) {
                ItemStack eq = liv.getEquippedStack(s);

                if (!eq.isEmpty()) {
                    tgt.equipStack(s, eq);
                }
            }

            if (liv.isBaby() && sur instanceof MobEntity tMob) {
                tMob.setBaby(true);
            }
        }
        return sur;
    }

    // The render state finally produced by getAndUpdateRenderState is assembled
    // from the surrogate, so its height/eye-height fields carry the TARGET
    // type's dimensions (e.g. a pig replaced by a horse stands too high/low).
    // Restoring the source entity's real values keeps the replacement aligned
    // with the original footprint. The source is only known through SRC, which
    // is null whenever no replacement happened this pass.
    @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void fixState(Entity e, float tickDelta,
                          CallbackInfoReturnable<EntityRenderState> cir) {
        // At RETURN the entity argument is the surrogate (it was replaced at
        // HEAD), so the reverse lookup finds the source for THIS exact call.
        Entity src = SRC.get(e);

        if (src == null) {
            return;
        }

        EntityRenderState st = cir.getReturnValue();

        if (st != null) {
            st.width = src.getWidth();
            st.height = src.getHeight();
            st.standingEyeHeight = src.getStandingEyeHeight();
        }
    }

    // Upstream fire animation kill switch. The entity fire commands are
    // submitted here (EntityRenderManager.render reads EntityRenderState.onFire
    // and submits the fire command), before they ever reach the batched fire
    // list consumed by FireCommandRenderer. Together with
    // FireCommandRendererMixin (which cancels the final draw call) this covers
    // the whole fire render pipeline: nothing is submitted and nothing is
    // drawn while the toggle is on.
    @Redirect(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitFire(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"))
    private void fireSub(OrderedRenderCommandQueue q, MatrixStack m,
                         EntityRenderState st, Quaternionf rot) {
        if (!Cfg.Off.DISABLE_FIRE_ANIMATION.getBooleanValue()) {
            q.submitFire(m, st, rot);
        }
    }
}