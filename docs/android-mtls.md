# Android 系统客户端证书

`Codex Mobile mTLS` 通过 WebView 的 `onReceivedClientCertRequest` 调用 Android KeyChain，按请求原始 host、port、keyTypes、issuers 选择系统证书。后台读取系统授权的私钥与完整证书链，回到 UI 线程完成请求。客户端私钥不会内置 APK 或交给 JavaScript。

请求串行处理，每个请求独立选择证书，不自动给其他主机复用证书别名。用户取消、密钥删除、空证书链、读取异常时调用 `ignore()`，允许重试。Activity 退出时结束所有挂起请求，忽略迟到回调。服务器 TLS 验证保留原行为，不新增信任全部证书或忽略服务器错误。

## 安装与使用

1. 下载云端 unsigned artifact，按 [本机签名](android-signing.md) 核对摘要、签名和包名。
2. 通过 ADB 安装签名 APK，与原版并存。使用手机已安装的客户端证书，不把证书文件上传到公开仓库。
3. 在“添加设备 / 管理设备”中加入私有连接资料；保留原网关 token。第一次出现系统证书选择器时由用户选择已安装证书。
4. 如果取消了选择，重新连接可以再次选择。系统可能缓存成功证书授权；重启 App 后仍需实测。

## 验收清单

- [x] 原生 JUnit/Robolectric 测试与 Android release 编译通过（GitHub Actions run 42983942414，提交 59a14d9）。
- [x] 下载摘要、对齐、签名和包名核验通过；RSA4096，APK v2/v3，版本0.2.3，versionCode1。
- [x] 手机确认两应用并存，系统证书授权与两台设备连接正常；取消后重试未单独取得实机结果。
- [x] 两台网关 HTTP 探测与 WSS 初始化、会话列表、已有会话恢复通过，不发送测试提示词。
- [x] 两入口无证书/错误证书均返回 HTTP400，正确证书但无 token 返回401（协议核验）。
- [x] 用户确认切换 Wi-Fi/移动流量并退出重开 App 后，SHAUNSHEEP 与141均正常。

2026-11-11 已关闭旧公网普通 HTTPS 入口19443/19444，公网使用 mTLS 入口19445/19446；ZeroTier 私网 token 入口仍保留。服务器脚本或 Node 请求通过不能替代 App 实机验收，未勾选项均待验证。

2026-11-11：已通过 ADB 并行安装，两包均存在。用户确认 SHAUNSHEEP 和141的 App 会话列表与已有会话可用；用户进一步确认 Wi-Fi/流量切换和 App 重启后两台仍正常；取消重试未单独确认。
