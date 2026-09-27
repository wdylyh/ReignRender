package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 附魔闪光控制：当 "Disable Glint" 开关打开时，把物品渲染时的 Glint（附魔
 * 闪光）参数替换为 NONE，从而取消所有物品（手中、盔甲、物品栏等）的附魔
 * 光泽。renderItem 为静态方法，最后一个参数（变量索引 8）即 Glint。
 */
@Mixin(ItemRenderer.class)
public class ItemGlintDisable {

    @ModifyVariable(method = "renderItem", at = @At("HEAD"), ordinal = 0, index = 8, argsOnly = true)
    private static ItemRenderState.Glint disableGlint(ItemRenderState.Glint glint) {
        if (RenderConfig.Toggles.DISABLE_GLINT.getBooleanValue()) {
            return ItemRenderState.Glint.NONE;
        }
        return glint;
    }
}