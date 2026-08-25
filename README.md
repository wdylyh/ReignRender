# ReignRender

一个全面管理 Minecraft 渲染的模组

---

## 兼容性

- **游戏版本**：Fabric 1.21.11  
- **前置模组**：malilib 0.27.16 或更高版本

---

## 当前功能

### 渲染禁用

1. 禁用粒子渲染
2. 禁用生物渲染
3. 禁用方块渲染
4. 禁用流体渲染
5. 禁用方块实体渲染
6. 禁用下落方块渲染
7. 禁用盔甲渲染
8. 禁用手持物品渲染
9. 禁用鞘翅渲染
10. 禁用迷雾渲染
11. 隐藏自己 / 隐藏其他玩家
12. 禁用命名牌
13. 禁用天空
14. 禁用云
15. 禁用天气
16. 禁用传送门
17. 禁用掉落物
18. 禁用燃烧火焰动画
19. 禁用 HUD 元素
20. 禁用方块轮廓
21. 禁用附魔闪光
### 通用选项

- **云渲染距离**：超出范围的云不渲染

- **替换渲染**：渲染替换系统

### 过滤与替换

- **输入模式**：所有过滤与替换名单支持两种输入方式，由通用选项卡中的「过滤输入模式」全局切换
  - **视图选择**：可视化选择
  - **手动输入**：手动输入id
- **黑白名单过滤**：可选择禁用渲染
- **通用渲染替换系统**：视觉上改变目标渲染
  - 粒子（如 `minecraft:flame=minecraft:heart`）
  - 方块（如 `minecraft:stone=minecraft:diamond_block`）
  - 实体（如 `minecraft:zombie=minecraft:skeleton`）
  - 迷雾（可为迷雾类型 / 生物群系 ID / 维度 ID）
  - 盔甲（如 `minecraft:iron_helmet=minecraft:diamond_helmet`）
  - 命名牌文字（如 `Notch=Herobrine`）
  - 玩家名字（如 `wdylyh=Steve`）
  - 流体（如 `minecraft:water=minecraft:lava`）
  - 方块实体（如 `minecraft:chest=minecraft:barrel`）
  - 下落方块（如 `minecraft:sand=minecraft:gravel`）

### 快捷键

- **打开配置界面**（默认 `X,R`）
- **按住临时显示**（默认 `H`）：
- **选取工具**：准星对准实体 / 粒子 / 流体 / 方块直接加入（或移出）对应过滤名单；潜行时选取当前生物群系加入迷雾过滤名单

### 命令

`/reignrender` 客户端命令支持在聊天栏直接管理过滤与替换列表：

```
/reignrender add <类别> <ID>             —— 将 ID 加入过滤列表
/reignrender remove <类别> <ID>          —— 将 ID 移出过滤列表
/reignrender mode <类别> <模式>          —— 切换过滤模式（off / blacklist / whitelist）
/reignrender list <类别>                 —— 查看当前过滤列表
/reignrender replace add <类别> <源=目标> —— 添加一个替换条目
/reignrender replace remove <类别> <源=目标> —— 移除一个替换条目
```

---

## 利用的机制

| 功能 | 对应拦截点 |
|------|------------|
| 方块 | `ChunkRendererRegion.getBlockState`、`SectionBuilder` |
| 流体 | `FluidRenderer.render`、`BlockRenderManager.renderFluid` |
| 方块实体 | `BlockEntityRenderManager.getRenderState`、`SignBlockEntityRenderer.renderSign` |
| 实体 | `EntityRenderManager.shouldRender`、`getAndUpdateRenderState` |
| 粒子 | `WorldRenderer.renderParticles`、`ParticleManager.addParticle` |
| 盔甲 | `ArmorFeatureRenderer.render` |
| 雾效 | `FogRenderer.applyFog`、`getFogColor` |
| 天空 | `WorldRenderer.renderSkyDark`、`renderCelestialBodies`、`renderEndSky`、`renderTopSky` |
| 云 | `CloudRenderer.renderClouds` |
| 天气 | `WeatherRendering.renderPrecipitation`、`addParticlesAndSound` |
| 传送门 | `EndPortalBlockEntityRenderer.render`、`InGameHud.renderPortalOverlay`、`renderNauseaOverlay` |
| 掉落物 | `ItemEntityRenderer` |
| 燃烧火焰 | `FireCommandRenderer.render` |
| 碰撞箱 | `DebugRenderer.render` |
| HUD 元素 | `InGameHud.renderBossBarHud` 等 13 个渲染方法 |
| 附魔闪光 | `ItemRenderer.renderItem` |
| 方块轮廓 | `WorldRenderer.drawBlockOutline` |
| 南瓜头迷雾 | `InGameHud.renderOverlay` |

---

## 性能提升

> 无较大提升，单纯视觉效果

> **注意**：本模组仅控制渲染，计算依然会进行（实体 AI、方块更新等不受影响）。

