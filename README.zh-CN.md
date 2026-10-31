# 天翼一号 2022 的 GCam 运行期适配

[English](README.md) · [通用移植研究](https://github.com/ChenQian-0701/gcam-unisoc-research)

本仓库只维护天翼一号 2022（TYH212U / ARES、UMS9620、Android 11）的专属模块、配置、构建安装说明和实机证据。跨机型的诊断流程与通用工具放在独立的 [gcam-unisoc-research](https://github.com/ChenQian-0701/gcam-unisoc-research) 仓库。

**首版为实验性研究版本。完整长短包围曝光、标准 HDR+ 连续拍摄和拍后 AE 恢复仍未完成。**

## 实现与边界

保留原版 BSG APK、签名和原生处理库，通过 LSPosed 做运行期适配。输入为 ISP 已处理的 YUV，再调用 BSG 已有转换器生成合成 RAW10；这不能恢复真实传感器 RAW 的信息。

入口在任何修复/探针前核对 TYH212U / ARES / API 30、主进程和指定 BSG APK 的完整 SHA-256。未知机型或版本跳过。一次进程启动会读取原始 APK 做哈希，存在启动 I/O 开销。

本配置只适用于公开相机 0、1。入口检查不是通用的逐镜头 RAW 检测器，不能启用隐藏或真正 RAW 接口后套用这些电平。

## 已有成果

- 启动与镜头枚举兼容修复、缺失元数据兜底。
- YUV 合成 RAW10 的电平一致性修复。
- 6 帧同曝光增强兼容方案，原设备实验有两会话 6/6 + 2/2 的保存及恢复记录。
- 单相机可用性等待的特定误判规避。
- 可开关的曝光、请求、帧输入及状态审计；实验保留但默认关闭。
- 本轮静态样张的三个平坦区域输出波动降低约 42%–56%，字样/纹理更明显，颗粒也有变化。不是“综合画质提高56%”。

## 使用前阅读

1. [已知问题](docs/known-issues.md)与[配置](config/baseline.json)。
2. [构建](docs/build.md)与[安装/恢复](docs/install.md)。
3. [验证状态](docs/validation.md)：历史结果与本次发布副本的测试分开。
4. [证据索引](docs/evidence-index.md)和[画质量化](docs/quality-results.md)。

模块不会自动覆盖偏好或清除旧调试属性；请自行按基线配置。原始 BSG 下载地址和 SHA-256 见 [English README](README.md)。专有 APK、原生库、私钥、全量手机日志和原始照片均未打包。

源码与文档的原创部分采用 Apache-2.0，第三方权利另列。欢迎带有清晰版本、配置、请求/返回帧和复现次数的反馈。
