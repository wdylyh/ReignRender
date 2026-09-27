package com.wdylyh.mixin;

import com.wdylyh.config.RenderConfig;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔闪光全局开关：当 "Disable Glint" 打开时，ItemStack.hasGlint() 一律返回
 * false。ItemStack 是物品渲染状态构建的统一入口，这个拦截覆盖所有 Glint 路径：
 * 手持物品、物品栏、盔甲（EquipmentRenderer 读取 hasGlint 后提交
 * armorEntityGlint 层）、鞘翅与盾牌，一处生效，不再依赖各渲染器各自的参数。
 */
@Mixin(ItemStack.class)
public class GlintDisable {

    @Inject(method = "hasGlint()Z", at = @At("HEAD"), cancellable = true)
    private void disableGlint(CallbackInfoReturnable<Boolean> cir) {
        if (RenderConfig.Toggles.DISABLE_GLINT.getBooleanValue()) {
            cir.setReturnValue(false);
        }
    }
}