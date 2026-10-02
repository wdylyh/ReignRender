package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import com.wdylyh.config.ConditionEngine;
import com.wdylyh.config.CoordinateFilter;
import com.wdylyh.config.FilterEngine;
import com.wdylyh.config.ReplacementEngine;
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
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderFilter {

    // Surrogate entities are cached per source entity and reused across frames.
    // Creating a fresh surrogate on every getAndUpdateRenderState call reset the
    // surrogate's age, RNG and animation state each frame, which made replaced
    // entities flicker. Weak keys let entries for unloaded / removed source
    // entities be collected automatically (the surrogate value never references
    // its source, so no retention cycle exists); the synchronized wrapper keeps
    // the cache safe if the render state extraction is ever touched outside the
    // render thread.
    private static final Map<Entity, Entity> surrogateBySource =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Source of the surrogate of the CURRENT getAndUpdateRenderState call,
    // handed from the HEAD ModifyVariable injector (replaceEntityWithSurrogate)
    // to the RETURN injector (fixState) on the same call. The pair cannot live
    // in a second map keyed by the surrogate: surrogate -> source strong values
    // combined with source -> surrogate values form a strong reference cycle
    // that neither WeakHashMap nor a manual cap could break. The ThreadLocal is
    // cleared at the start of every call and read-once-then-cleared at RETURN,
    // so a stale value can never leak into a later call, even when the target
    // method throws.
    private static final ThreadLocal<Entity> currentSource = new ThreadLocal<>();

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
        boolean kd = FilterEngine.revealDown();
        int bhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_ENTITIES, kd);

        // Force-hide mode + hotkey held: hide every entity (players, our own
        // model and falling blocks included) regardless of the individual
        // toggles, so it short-circuits before the toggle-gated checks below.
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_FORCE) {
            cbr.setReturnValue(false);
            return;
        }

        // Per-id render count/distance limits from the condition system,
        // gated by their own master switches and by the NORMAL hotkey
        // behavior of the entity's own reveal category (item drops and
        // falling blocks have their own categories): while the category is
        // suspended (BYPASS = release mode reveal hotkey held, INACTIVE =
        // enable mode with the hotkey up) the world must render as-is, so
        // the limits step aside exactly like the hide filters do. The count
        // limit consumes one unit of the per-frame budget; the distance
        // limit compares the entity position against the camera position
        // (the cx/cy/cz arguments of shouldRender). Item drops are matched
        // by their item id (the entity id of every ItemEntity is the same
        // "minecraft:item", so only the item id is meaningful).
        String limitId = null;
        if (ConditionEngine.limitsActive()) {
            int limitBhv = bhv;
            if (e instanceof FallingBlockEntity) {
                limitBhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_FALLING_BLOCKS, kd);
            } else if (e instanceof ItemEntity) {
                limitBhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_ITEM_ENTITIES, kd);
            }
            if (limitBhv == FilterEngine.HOTKEY_BEHAVIOR_NORMAL) {
                limitId = e instanceof ItemEntity ie && ie.getStack() != null && !ie.getStack().isEmpty()
                        ? FilterEngine.getItemId(ie.getStack().getItem())
                        : FilterEngine.getEntityId(e.getType());
            }
        }
        if (limitId != null && ConditionEngine.isCountExceeded(limitId, e.getX(), e.getY(), e.getZ())) {
            cbr.setReturnValue(false);
            return;
        }
        if (limitId != null && ConditionEngine.isDistanceExceeded(limitId,
                e.getX(), e.getY(), e.getZ(), cx, cy, cz)) {
            cbr.setReturnValue(false);
            return;
        }

        // Item entities (drops) and falling blocks have their own dedicated
        // toggles AND their own reveal categories, so the master "disable
        // entities" toggle must not swallow them (the OFF filter mode hides
        // every entity, which would otherwise drag item drops along with it).
        // Each of the three categories gets its own behavior code: FORCE hides
        // it while the enable-mode hotkey is held, BYPASS/INACTIVE restore it,
        // NORMAL applies the toggle as usual. The FallingBlockEntity check
        // comes first so it can short-circuit before the expensive
        // isEntityFiltered() lookup when both toggles are on.
        if (e instanceof FallingBlockEntity) {
            int bhvFb = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_FALLING_BLOCKS, kd);
            if (bhvFb == FilterEngine.HOTKEY_BEHAVIOR_FORCE ||
                    (bhvFb == FilterEngine.HOTKEY_BEHAVIOR_NORMAL &&
                            RenderConfig.Toggles.DISABLE_FALLING_BLOCKS.getBooleanValue())) {
                cbr.setReturnValue(false);
                return;
            }
        } else if (e instanceof ItemEntity) {
            int bhvIe = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_ITEM_ENTITIES, kd);
            if (bhvIe == FilterEngine.HOTKEY_BEHAVIOR_FORCE ||
                    (bhvIe == FilterEngine.HOTKEY_BEHAVIOR_NORMAL &&
                            RenderConfig.Toggles.DISABLE_ITEM_ENTITIES.getBooleanValue())) {
                cbr.setReturnValue(false);
                return;
            }
        } else {
            if (RenderConfig.Toggles.DISABLE_ENTITIES.getBooleanValue() &&
                    FilterEngine.isEntityFiltered(e.getType(), kd, bhv)) {
                cbr.setReturnValue(false);
                return;
            }

            if (shouldHidePlayer(e, kd)) {
                cbr.setReturnValue(false);
                return;
            }
        }

        // Coordinate filter: independent of the entity/player master
        // toggles, matched against the entity's absolute position.
        if (bhv == FilterEngine.HOTKEY_BEHAVIOR_NORMAL &&
                CoordinateFilter.isEntityHidden(e)) {
            cbr.setReturnValue(false);
        }
    }

    // Hides our own model and/or the other players' models. Identity comparison
    // makes remote players with the same display name still count as "other".
    // With the master "hide other players" toggle on, the player name filter
    // mode decides: OFF hides every other player, BLACKLIST hides the listed
    // names, WHITELIST only shows the listed names.
    private static boolean shouldHidePlayer(Entity e, boolean kd) {
        if (!(e instanceof PlayerEntity p)) {
            return false;
        }

        // The players category has its own reveal behavior: FORCE hides every
        // player while the enable-mode hotkey is held, BYPASS/INACTIVE restore
        // them, NORMAL applies the toggles below as usual. Only player
        // entities reach this point, so the extra behavior evaluation is not
        // paid for the common non-player path.
        int bhv = FilterEngine.hotkey_Behavior(FilterEngine.TYPE_PLAYERS, kd);
        if (bhv != FilterEngine.HOTKEY_BEHAVIOR_NORMAL) {
            return bhv == FilterEngine.HOTKEY_BEHAVIOR_FORCE;
        }

        boolean self = p == MinecraftClient.getInstance().player;

        if (self) {
            return RenderConfig.Toggles.HIDE_SELF.getBooleanValue();
        }

        if (!RenderConfig.Toggles.HIDE_OTHER_PLAYERS.getBooleanValue()) {
            return false;
        }

        return FilterEngine.isPlayerHidden(p.getGameProfile().name(), kd, bhv);
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
    private Entity replaceEntityWithSurrogate(Entity e) {
        // Reset the per-call source pairing first, so a value left behind by a
        // previous call that threw can never be picked up by fixState.
        currentSource.remove();
        // No master-switch gate here: the coordinate rules below are gated by
        // the coordinate replace toggle (global switch OFF) while the global
        // fallback checks the master switch internally — an outer gate would
        // make the two mutually exclusive paths impossible to combine.
        if (e == null || !ReplacementEngine.anyReplaceActive()) {
            // No replacement mechanism is active: nothing below can match, so
            // skip the per-entity reveal check and the replacement id lookups.
            return e;
        }
        // The reveal hotkey skips the replacement (shows the original entity).
        if (FilterEngine.isReplaceBlocked(FilterEngine.TYPE_ENTITIES)) {
            return e;
        }
        EntityType<?> srcT = e.getType();
        // Coordinate aware rules win over the global list: the entity's
        // current position decides, the global per-category list is only the
        // fallback.
        String srcId = FilterEngine.getEntityId(srcT);
        String tId = ReplacementEngine.getReplacementEntityAt(srcId, e.getX(), e.getY(), e.getZ(), FilterEngine.TYPE_ENTITIES);
        if (tId == null) {
            tId = ReplacementEngine.getReplacementEntity(srcId);
        }
        if (tId == null) {
            return e;
        }
        EntityType<?> t = ReplacementEngine.getEntityTypeTarget(tId);
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
        // which a freshly created entity would reset on every frame. The weak
        // keys recycle the entries of removed source entities automatically.
        Entity sur = surrogateBySource.get(e);

        if (sur == null || !sur.getType().equals(t)) {
            sur = t.create(w, SpawnReason.COMMAND);

            if (sur == null) {
                return e;
            }

            surrogateBySource.put(e, sur);
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
                // Unconditional: a slot that became empty on the source must
                // clear the surrogate's stale piece too (the surrogate is
                // cached and reused across frames, so a filtered-out empty
                // stack would leave the previous frame's item visible forever).
                tgt.equipStack(s, liv.getEquippedStack(s));
            }

            if (liv.isBaby() && sur instanceof MobEntity tMob) {
                tMob.setBaby(true);
            }
        }
        // Pair this exact call with its source for fixState at RETURN.
        currentSource.set(e);
        return sur;
    }

    // The render state finally produced by getAndUpdateRenderState is assembled
    // from the surrogate, so its height/eye-height fields carry the TARGET
    // type's dimensions (e.g. a pig replaced by a horse stands too high/low).
    // Restoring the source entity's real values keeps the replacement aligned
    // with the original footprint. The source is only known through the
    // currentSource ThreadLocal, which is empty whenever no replacement
    // happened in this call.
    @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void fixState(Entity e, float tickDelta,
                          CallbackInfoReturnable<EntityRenderState> cir) {
        // Read-once-then-clear: the value was set for THIS exact call at HEAD
        // (or is null), so no stale pairing can survive into a later call.
        Entity src = currentSource.get();
        currentSource.remove();

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
    private void cancelFireSubmit(OrderedRenderCommandQueue q, MatrixStack m,
                         EntityRenderState st, Quaternionf rot) {
        if (!FilterEngine.isHudElementHidden("fire")) {
            q.submitFire(m, st, rot);
        }
    }
}