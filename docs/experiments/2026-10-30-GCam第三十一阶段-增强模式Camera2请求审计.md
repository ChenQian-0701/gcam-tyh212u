> Historical experiment record, sanitized for publication. Intermediate conclusions may be superseded; read current known issues first. Named original work/log files are not all included.

# 第三十一阶段：增强模式 Camera2 请求审计

2026-10-30，Asia/Shanghai。恢复 Blueline，关闭全部新增功能削减实验；Google AWB=0，增强 on，临时关闭同曝光兼容方案以复现长短曝光问题。

## 新的直接证据

新增只读 SessionRequestProbe，在 CameraCaptureSessionImpl.captureBurst 的提交入口记录请求，避免仅看原生算法计划而误判 Android 层到底有没有提交控制。

确实提交了 6 个请求，CONTROL_AE_MODE=OFF（0）、AE_LOCK=true、CONTROL_CAPTURE_INTENT=CUSTOM（0）。曝光依次为 13.482732 / 27.843266 / 13.482732 / 27.843266 / 13.482732 / 13.482732 ms；ISO 依次为 321/623/321/623/321/321。

对应返回帧曝光为 20/20/20/13.482732/27.843266/13.482732 ms，ISO 为 623/321/321/320/622/320。曝光显示约 3 帧偏移，增益的过渡也不完全与请求对应。随后基准帧 0 被丢弃，再次报 Base frame -1；无 JPEG，快门恢复。

因此不是完全没发出手动请求，也不能用“设备未声明 MANUAL_SENSOR”就断言控制完全无效。当前事实是控制有部分作用，但拍摄序列的实际结果未按原生预期对应，导致一致性检查和基准帧选择失败。

## 静态拍照意图实验

原请求 intent=CUSTOM 可能影响 Unisoc HAL 选择的处理路径。新增默认关闭的 StillIntentRepair，只在新的非 ZSL payload burst 构建期间，将 AE=OFF、有效手动曝光、intent=CUSTOM 的 Builder 改为 STILL_CAPTURE（2）。不改曝光、ISO、tag、实际结果元数据；缓存 ZSL 和普通预览请求不覆盖。待实测判断是否改变 HAL 设置生效时机。

证据：stage31-enhanced-camera2.json 与 stage31-enhanced-camera2-native.txt；完整日志 work/stage31/。

## 静态拍照意图实验结果

确认 6 个手动请求均改为 intent=2 并真正提交，但返回曝光仍显示约 3 帧偏移：原请求短/长/短/长/短/短，实际前 3 帧仍为旧曝光，之后才出现第一短、第一长、第二短。首帧仍被丢弃并报基准帧 -1，无 JPEG；8.72 秒时快门已恢复。

因此 intent=CUSTOM 不是单独充分原因，StillIntentRepair 不进入默认构建。下一步正确方向应是请求生效等待/预热和帧对应策略；仅改 Interface、capture intent、实际元数据或基准帧索引都不能替代这一步。现有同曝光 6 帧兼容方案仍应保留，完整包围曝光未修复。

## 请求关联与 HAL ZSL

补采原始 TotalCaptureResult.getRequest().getTag() 与 burst 提交 tag：确认对应的是同一批手动请求，并非简单误把预览请求元数据分派给照片。在某组中前两帧旧曝光、随后两帧已匹配、末帧又收到长曝光；滞后并非永远固定 3 帧，不能硬编码平移 3 来掩盖所有情况。

同时发现手动增强请求仍带 CONTROL_ENABLE_ZSL=true。新增单变量实验只把该批手动请求的 HAL ZSL 设为 false，保持 CaptureIntent、曝光、ISO、tag 不变；与 GCam 自己的原生/Java ZSL 合成缓存区分，不能混称“关闭 HDR+”。结果随后追加。

仅关闭手动 burst 的 HAL ZSL 确认命中，但首帧仍被丢弃，8.72 秒中止、无 JPEG。注意随后恢复的 repeating preview 仍可能带 HAL ZSL=true；进一步对照在初始预览与照片请求（intent 0/1/2）中统一关闭 HAL ZSL，普通视频 intent=3 不覆盖。此对照保持 GCam 自身软件 ZSL 多帧路径；结果稍后追加。

统一关闭照片/预览请求 HAL ZSL 的标准组仍是有 JPEG、40 秒时快门未恢复；增强交替曝光组仍在 8.92 秒中止，无 JPEG。该开关确实进入 Camera2 请求（zsl=false），但未解决两项故障，已撤回。曝光偏移只能说与请求不一致，不能认定为 HAL ZSL 单一根因。
