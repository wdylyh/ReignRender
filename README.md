# ReignRender

一个全面管理 Minecraft 渲染的模组

---

## 兼容性

- **游戏版本**：Fabric 1.21.11  
- **前置模组**：malilib 0.27.16 或更高版本

---

## 当前功能

1. 禁止粒子渲染  
2. 禁止生物渲染  
3. 禁止方块渲染  
4. 禁止方块实体渲染  
5. 禁止下落方块渲染  
6. 禁止盔甲渲染  
7. 禁止迷雾渲染
8. 可以禁止告示牌渲染的情况下渲染告示牌文字  
---

## 利用的机制

| 功能 | 对应拦截点 |
|------|------------|
| 方块 | `ChunkRendererRegion.getBlockState` |
| 流体 | `FluidRenderer.render`、`BlockRenderManager.renderFluid` |
| 方块实体 | `BlockEntityRenderManager.getRenderState`、`SignBlockEntityRenderer.renderSign` |
| 实体 | `EntityRenderManager.shouldRender` |
| 粒子 | `WorldRenderer.renderParticles`、`ParticleManager.addParticle` |
| 盔甲 | `ArmorFeatureRenderer.render` |
| 雾效 | `FogRenderer.applyFog` |

---

## 性能提升

> 无较大提升，单纯视觉效果

> **注意**：本模组仅控制渲染，计算依然会进行（实体 AI、方块更新等不受影响）。

---

## 备注

- 本模组由 AI 协助制作，欢迎提出问题和建议！