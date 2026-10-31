> Historical experiment record, sanitized for publication. Intermediate conclusions may be superseded; read current known issues first. Named original work/log files are not all included.

# 第二十二阶段：紫偏对照与 Google AWB 定位

2026-10-30，接续第二十一阶段。

用户确认 HDR+ 增强仍会报 `Base frame index -1 is out of bounds [0,5]`，所以旧版 16/16 通过只代表当时短期实验，不能代表问题已彻底消失。

## 直接输入对照

新增默认关闭的 InputSnapshot，在快门后复制一帧 YUV，按 NV21 保存参考 JPEG。仅复制 buffer，不修改位置/像素。源 YUV 白色区域正常；GCam 普通（HDR 关闭）输出紫偏，说明颜色偏移主要发生在后续处理链。

新增 ColorRepair 实验，在 tbt.k 后把 native AwbInfo 的增益/矩阵与合成 RAW 转换矩阵同步改为单位值。结果：

| 普通模式 | 白区域 R/G | B/G | 观测 |
|---|---:|---:|---|
| 原矩阵、Google AWB=1 | 1.117 | 1.330 | 明显紫偏 |
| 单位矩阵、Google AWB=1 | 1.131 | 1.318 | 仍紫偏 |
| 单位矩阵、Google AWB=0 | 0.999 | 1.000 | 白色恢复中性 |
| 原矩阵、Google AWB=0 | 1.000 | 1.000 | 同样恢复中性 |

ROI 是照片归一化坐标 `[0.30,0.025,0.70,0.13]` 内同一白色背景区域；不是整幅图均值。场景/人物有轻微移动，不将这些数字当作正式色彩标定。应以灰卡、不同光源继续验证。

结论：普通模式不需要额外单位矩阵补丁，关闭 Google AWB 已足以消除本场景紫偏。此前“关闭 AWB 没有收益”基于黑图/丢片状态，不能外推到修复 RAW10 电平后的当前管线。

## 增强模式初测

单位矩阵 + Google AWB=0 + 原六帧/边界处理下，增强模式同会话 3/3 保存，快门逐次恢复约 10.7 秒，白区 R/G≈1、B/G≈1。下一组去掉额外矩阵改动，并记录请求/实际曝光及原生合成回调，以区分颜色设置效果和真实多帧算法行为。

证据：`evidence/2026-10-30-stage22-color-roi-initial.json`；完整快门矩阵、YUV 输入参考和样张在 `work/stage22/`，收尾时补齐精简证据。诊断样张不作为最终发布图片。

该阶段未将 ColorRepair 的单位矩阵实验设为默认。InputSnapshot 诊断会增加复制/编码开销，不用于最终性能评估。
