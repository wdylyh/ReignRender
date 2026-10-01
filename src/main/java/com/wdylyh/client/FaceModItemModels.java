package com.wdylyh.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wdylyh.config.FaceModPacks;
import com.wdylyh.config.RenderConfig;
import com.wdylyh.mixin.ItemRenderStateAccessor;
import com.wdylyh.mixin.LayerRenderStateAccessor;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedSimpleModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.model.GeometryBakedModel;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Client model hook backing the held / dropped item face modification.
 *
 * <p>A held item and the same item as a dropped entity share one baked item
 * model, so their textures cannot differ through the resource pack alone.
 * To split them, the face-mod pack carries "shadow" block models under
 * {@code reignrender:item/shadow/<held|dropped>/<item>} whose textures point
 * at {@code reignrender:shadow/<kind>/...} (editable in the face-mod GUI).</p>
 *
 * <p>On every model reload this plugin registers the needed shadow models as
 * extra models (so they resolve and bake like any other model) and wraps the
 * baked {@link ItemModel} of every listed item in a {@link ShadowItemModel}.
 * While rendering, the wrapper first fills the state with the vanilla model
 * and then swaps the layer quads for the shadow geometry when the display
 * context matches (GROUND = dropped, first/third person = held); GUI and
 * other contexts keep the vanilla appearance. The shadow quads come out of
 * the normal baker, so their texture coordinates are already correct for the
 * shadow sprites - no manual UV remapping is needed.</p>
 */
public class FaceModItemModels implements ModelLoadingPlugin {

    private static final Map<Identifier, ExtraModelKey<BlockStateModel>> EXTRA_KEYS = new HashMap<>();

    private static final Map<Identifier, ExtraModelKey<BlockStateModel>> REGION_KEYS = new HashMap<>();

    private FaceModItemModels() {}

    /** Registers the plugin; call once during client init, before any model reload. */
    public static void register() {
        ModelLoadingPlugin.register(new FaceModItemModels());
    }

    @Override
    public void initialize(Context ctx) {
        // (Re)generate the shadow model/texture resources for the current id
        // lists and register them as extra models so they resolve and bake
        // with the rest of the models.
        FaceModPacks.ensureShadowItemResources();

        for (Identifier mid : FaceModPacks.shadowModelIds()) {
            ExtraModelKey<BlockStateModel> key = EXTRA_KEYS.computeIfAbsent(mid, k -> ExtraModelKey.create(k::toString));
            ctx.addModel(key, SimpleUnbakedExtraModel.blockStateModel(mid));
        }

        // Region face-mod item shadow models: generated for every item id with
        // region edits (index-driven) and baked as extra models the same way.
        com.wdylyh.config.RegionFacePacks.ensureResources();

        java.util.Set<Identifier> regionAvailable = new java.util.HashSet<>();

        for (Identifier mid : com.wdylyh.config.RegionFacePacks.regionItemModelIds()) {
            regionAvailable.add(mid);
            ExtraModelKey<BlockStateModel> key = REGION_KEYS.computeIfAbsent(mid, k -> ExtraModelKey.create(k::toString));
            ctx.addModel(key, SimpleUnbakedExtraModel.blockStateModel(mid));
        }

        // Wrap the baked item model of every item that has a shadow model.
        java.util.Set<Identifier> available = new java.util.HashSet<>(FaceModPacks.shadowModelIds());

        ctx.modifyItemModelAfterBake().register(ModelModifier.WRAP_PHASE, (model, c) -> {
            boolean globalOn = RenderConfig.General.ENABLE_FACE_MOD.getBooleanValue() && FaceModPacks.isPackActive();
            String sid = c.itemId().toString();
            Baker baker = c.bakeContext().blockModelBaker();

            // The available shadow models decide the wrap, not the id lists: an
            // id that was dropped from a list but still carries edits keeps its
            // shadow, so a block-category edit can never fall back into the
            // held / dropped rendering of the item.
            GeometryBakedModel held = globalOn && available.contains(FaceModPacks.shadowModelId(sid, "held"))
                    ? bakeShadow(baker, sid, "held") : null;
            GeometryBakedModel dropped = globalOn && available.contains(FaceModPacks.shadowModelId(sid, "dropped"))
                    ? bakeShadow(baker, sid, "dropped") : null;

            // Region face-mod item shadow: wrapped whenever the item id
            // carries region edits (available shadow model); at runtime the
            // region lookup and the carrier position (published by the
            // render-path mixins) decide where the region model renders.
            GeometryBakedModel region = com.wdylyh.config.RegionFacePacks.active()
                    && regionAvailable.contains(com.wdylyh.config.RegionFacePacks.regionItemModelId(sid))
                    ? bakeRegion(baker, sid) : null;

            if (held == null && dropped == null && region == null) {
                return model;
            }

            return new ShadowItemModel(model, held, dropped, region);
        });
    }

    /**
     * Bakes the shadow block model {@code reignrender:item/shadow/<kind>/<item>}
     * through the normal baker, so its quads carry texture coordinates that
     * are already correct for the shadow sprites. Returns null when the
     * shadow resources are missing (e.g. the id was just added but no reload
     * happened yet).
     */
    private static GeometryBakedModel bakeShadow(Baker baker, String sid, String kind) {
        Identifier mid = FaceModPacks.shadowModelId(sid, kind);

        if (mid == null) {
            return null;
        }

        return bakeGeometry(baker, mid);
    }

    /** Bakes the region item shadow model {@code reignrender:item/rface/<item>}. */
    private static GeometryBakedModel bakeRegion(Baker baker, String sid) {
        Identifier mid = com.wdylyh.config.RegionFacePacks.regionItemModelId(sid);

        if (mid == null) {
            return null;
        }

        return bakeGeometry(baker, mid);
    }

    /** Bakes one model json through the normal baker; null when it is missing (no reload yet). */
    private static GeometryBakedModel bakeGeometry(Baker baker, Identifier mid) {
        try {
            BakedSimpleModel bsm = baker.getModel(mid);
            ModelTextures tex = bsm.getTextures();

            return new GeometryBakedModel(
                    bsm.bakeGeometry(tex, baker, ModelRotation.IDENTITY),
                    bsm.getAmbientOcclusion(),
                    BakedSimpleModel.getParticleTexture(tex, baker, bsm));
        } catch (Exception e) {
            FaceModPacks.LOGGER.debug("[ReignRender] shadow model {} not available yet", mid, e);
            return null;
        }
    }

    /**
     * The wrapped {@link ItemModel}: renders with the vanilla model, then
     * replaces the layer quads with the shadow geometry for the matching
     * display context. GUI, item frames and other contexts keep the vanilla
     * look.
     */
    private record ShadowItemModel(ItemModel wrapped, GeometryBakedModel held, GeometryBakedModel dropped,
                                   GeometryBakedModel region)
            implements ItemModel {

        @Override
        public void update(ItemRenderState state, ItemStack stack, ItemModelManager resolver,
                           ItemDisplayContext displayContext, ClientWorld world,
                           net.minecraft.util.HeldItemContext heldContext, int seed) {
            wrapped.update(state, stack, resolver, displayContext, world, heldContext, seed);

            GeometryBakedModel shadow;

            boolean bodyContext = displayContext == ItemDisplayContext.GROUND
                    || displayContext.isFirstPerson()
                    || displayContext.name().startsWith("THIRD_PERSON");

            if (!bodyContext) {
                return; // GUI, item frames and other contexts keep the vanilla look
            }

            // Region face-mod wins over the global shadow: the carrier position
            // is published by the render-path mixins around the whole update /
            // render call, so it is always consistent here.
            double[] pos = com.wdylyh.client.RegionFaceItemPos.pos();

            if (region != null && pos != null
                    && com.wdylyh.config.RegionFacePacks.active()
                    && com.wdylyh.config.RegionFaceEngine.isItemFaceAt(
                            net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString(),
                            pos[0], pos[1], pos[2])) {
                shadow = region;
            } else if (displayContext == ItemDisplayContext.GROUND) {
                shadow = dropped;
            } else if (displayContext.isFirstPerson()
                    || displayContext.name().startsWith("THIRD_PERSON")) {
                shadow = held;
            } else {
                return;
            }

            if (shadow == null) {
                return;
            }

            replaceQuads(state, shadow);
        }
    }

    /**
     * Swaps the baked quads of the render state for the shadow geometry. The
     * first non-empty vanilla layer carries the quads and is replaced; any
     * additional layers are emptied so the vanilla geometry does not show
     * through underneath the shadow. Tints, transformations, glint and the
     * render layer set by the vanilla model are kept.
     */
    private static void replaceQuads(ItemRenderState state, GeometryBakedModel shadow) {
        List<BakedQuad> shadowQuads = shadow.getQuads(null);

        if (shadowQuads == null || shadowQuads.isEmpty()) {
            return;
        }

        ItemRenderState.LayerRenderState[] layers = ((ItemRenderStateAccessor) state).getLayers();
        int count = ((ItemRenderStateAccessor) state).getLayerCount();
        List<BakedQuad> empty = List.of();
        boolean replaced = false;

        for (int i = 0; i < Math.min(count, layers.length); i++) {
            ItemRenderState.LayerRenderState layer = layers[i];
            List<BakedQuad> current = layer.getQuads();

            if (current == null || current.isEmpty()) {
                continue;
            }

            if (!replaced) {
                ((LayerRenderStateAccessor) layer).setQuads(shadowQuads);
                layer.setParticle(shadow.particleSprite());
                replaced = true;
            } else {
                ((LayerRenderStateAccessor) layer).setQuads(empty);
            }
        }
    }
}
