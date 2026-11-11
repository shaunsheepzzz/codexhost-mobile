# mTLS 安卓 Fork

本分支基于 [unlgame/codexhost-mobile](https://github.com/unlgame/codexhost-mobile)，保留 Apache-2.0 许可和上游归属。

- 仓库：`shaunsheepzzz/codexhost-mobile`。
- 应用 ID：`io.github.shaunsheepzzz.codexhostmobile`，名称：`Codex Mobile mTLS`。
- 与原版 `ai.unlgame.codexhostmobile` 并存，不卸载原版或迁移其数据。
- Android 工作流只允许手动触发，只生成未签名 APK 和 SHA256，不发布 Release、不调用 iOS/npm；保留的 iOS/npm 工作流限制为上游仓库。
- PakePlus Android 固定为 `787b9e5ea2da1b2d959485417ffeee62f0d30960`。原加固步骤完成后应用 `scripts/apply-android-mtls.py`，运行原生单测，再构建 APK。
- CI 不接收 APK 签名密钥，删除模板签名配置与模板 keystore。本机签名后才能安装。
- App 更新只接受本 fork 的正式签名资产 `CodexHostMobile-v<version>.apk`，不接受上游或 unsigned 产物。

见 [客户端证书](android-mtls.md) 和 [本机签名](android-signing.md)。
