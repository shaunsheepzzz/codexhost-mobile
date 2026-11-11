# 安卓客户端证书支持实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fork 安卓 App，使其通过 Android 系统已安装的客户端证书访问两台设备的 mTLS 网关。

**Architecture:** 保留 React 前端及现有 Kotlin/WebView 包装层，在固定版本的 PakePlus 上应用独立、可复现的证书支持补丁。认证处理放在原生 Kotlin 类中，构建只产出未签名安卓 APK，下载后本机持久化签名并通过 ADB 并行安装。

**Tech Stack:** Kotlin、Android KeyChain/WebView、JDK 17、Android SDK 34、Python 3、Node.js、Vitest、GitHub Actions。

**Spec:** `docs/superpowers/specs/2026-11-11-android-mtls-design.md`（用户已确认）。

## 全局约束

- Fork 目标 `shaunsheepzzz/codexhost-mobile`，保留 Apache-2.0 许可和上游归属。
- PakePlus 固定提交 `787b9e5ea2da1b2d959485417ffeee62f0d30960`；修改该版本需重新验证补丁。
- 新包名选用 `io.github.shaunsheepzzz.codexhostmobile`，名称为 `Codex Mobile mTLS`；这是实施选择，原版保留。
- 保留系统服务器证书验证；不内置客户端私钥，不跳过 TLS 校验。
- APK 签名材料只存本机仓库外受限目录，不交给 CI，不提交证书、口令、网关二维码或真实 token。
- 只构建安卓，不自动发布 npm/iOS，不自动发布未签名 APK 为可安装更新。
- 保留原网关 token；真实验收不发送聊天提示词，不删除聊天历史。
- 19443/19444 普通入口保持现状，手机验收后另行处理；本轮只使用19445/19446测试入口。
- 中文文档与提交信息；每次提交遵循 `docs/commit-conventions.md`。先核实已有 Git 身份或 GitHub 公共身份，只配置本仓库，不猜测邮箱。

## Review Focus

- 用户取消证书选择后可重试：使用 `ignore()`，不永久缓存拒绝；由任务1测试。
- Activity 退出时选择器或密钥读取尚未完成：结束挂起请求，迟到回调不得再次完成请求；由任务1测试。
- 多个并发 HTTP/WSS 认证请求：不同时弹出多个选择器，每个请求保留自身 host/port/issuers/keyTypes；由任务1、3测试。
- 密钥被移除、证书链为空或读取异常：安全失败，不崩溃、不转为信任全部证书；由任务1测试。
- 包名、签名和更新来源不一致：原版继续存在，拒绝上游 APK，云端未签名产物不能当作更新发布；由任务2、3测试。

## 文件职责

- `android/mtls/ClientCertificateHandler.kt`：系统证书选择、后台密钥读取、单次请求完成与生命周期清理。
- `android/mtls/ClientCertificateHandlerTest.kt`：通过 Robolectric/JUnit 和可注入的证书选择/读取接口验证真实控制流程。
- `scripts/apply-android-mtls.py`：在已生成并完成现有加固的 PakePlus 工程中复制 Kotlin 文件、注入回调和退出清理、配置测试依赖；不改变现有加固语义。
- `tests/ci/android-mtls-patch.test.ts`：在固定版本最小 fixture 上执行补丁，验证锚点漂移报错，运行测试所需源码被正确装入。
- `tests/ci/fixtures/pakeplus-android-mtls/`：固定版本的 MainActivity 与 Gradle 最小相关 fixture，注明来源与提交。
- `.github/workflows/build-android.yml`：沿用前端、权限、资源加固，只保留安卓未签名构建与测试。
- `src/app-update/release.ts`：更新来源限定 fork；相关现有测试同步调整。
- `tests/ci/mobile-packaging-workflows.test.ts`：调整原有签名/联动发布断言，保持现有资源和权限保障。
- `docs/android-mtls.md`、`docs/FORK.md`、`docs/android-signing.md`：维护当前构建、签名、并行安装与证书授权说明。
- 本机仓库外 `android-signing/`：持久化签名密钥及受限口令文件；本机签名脚本按文件/环境读取口令，不回显。

### 任务1：实现并验证原生证书回调

**接口：** `ClientCertificateHandler(activity: Activity)`；`handle(request: ClientCertRequest): Unit`；`close(): Unit`。测试注入选择器、后台执行器和密钥加载器，生产默认调用 KeyChain；不暴露到 JavaScript。

- [ ] 1. 写 `ClientCertificateHandlerTest.kt`：系统选择器收到请求原始 host、port、keyTypes、principals；选择后使用返回的私钥和完整证书链调用 `proceed` 一次。
- [ ] 2. 增加取消、空私钥、空证书链、读取异常、close 后回调、并发请求的测试；断言每个请求最多完成一次，取消可重试，close 后不再授权。
- [ ] 3. 为补丁写 Vitest 测试：固定源码 fixture 上注入成功；缺失或重复锚点明确失败，不静默修改错误位置。先执行 `npm test -- tests/ci/android-mtls-patch.test.ts`，确认因补丁缺失而失败。
- [ ] 4. 实现补丁；锚点为 `inner class MyWebViewClient(val debug: Boolean) : WebViewClient()` 与 `override fun onDestroy()`。复制 handler 和测试，补充 Robolectric 测试配置。准备精确固定提交的临时 Android 工程，测试先于 handler 实现执行并确认失败原因为缺失实现。
- [ ] 5. 实现 handler：UI 线程排队选择器，后台加载 KeyChain 私钥/证书链，UI 线程完成请求；退出时结束排队与挂起请求；迟到结果忽略。不引入自动为其他主机复用证书别名的缓存。
- [ ] 6. 运行 `npm test -- tests/ci/android-mtls-patch.test.ts`，以及生成工程中的 `./gradlew testReleaseUnitTest --no-daemon`；预期测试全部通过。无本地 JDK17/SDK 时，在任务2的云构建先跑测试并保留失败、通过两次运行证据。
- [ ] 7. 通过 `git diff --check`、`git diff --cached --check`、暂存文件核对后提交；不把临时 Android 工程或构建产物加入仓库。

### 任务2：Fork 安卓构建、更新与签名路径

**接口：** 任务1补丁在现有 Python 加固步骤后、Gradle 测试/编译前执行；CI 输出 `app-release-unsigned.apk` 和 SHA256，本机签名后沿用 `CodexHostMobile-v<version>.apk` 更新资产命名。

- [ ] 1. 创建或确认 `shaunsheepzzz/codexhost-mobile` fork，保留上游 remote；核实远端身份再推送。现有 `codex/android-mtls` 独立 checkout 可复用，执行前按 worktree 技能核实隔离。
- [ ] 2. 给 `tests/ui/app-update-release.test.ts` 增加 fork 正式资产可接受、上游资产被拒绝的测试。先运行 `npm test -- tests/ui/app-update-release.test.ts`，确认新来源断言失败。
- [ ] 3. 修改前端更新仓库/API/缓存键和原生 AppUpdater 下载白名单为 fork；同步打包资源 URL 白名单，不改变通用 URL 校验语义。运行对应测试通过。
- [ ] 4. 为 workflow 写/改测试：独立 APP_ID 与名称、只允许手动安卓构建、不需要签名 secrets、无 iOS/npm/release 联动、Gradle 单测先于 assembleRelease、只上传未签名产物；确认新断言先失败。
- [ ] 5. 修改 `.github/workflows/build-android.yml` 实现上述行为。保留原有加固及权限/资源检查，清除模板 release/debug 签名绑定与公开模板 keystore，不使用模板签名。现有 iOS/npm workflow 若可独立触发，则加上游仓库限制，确保 fork 不自动发布。
- [ ] 6. 执行 `npm test -- tests/ci/mobile-packaging-workflows.test.ts tests/ci/android-mtls-patch.test.ts tests/ui/app-update-release.test.ts` 和 `npm run build`；确认通过后在 fork 触发构建，等待 Gradle 单测与安卓编译结果。
- [ ] 7. 在仓库外受限目录创建并保留新签名密钥；安装所需 JDK17/Android 官方工具后，对下载产物先核对 SHA256，再 `zipalign`、`apksigner sign`、`apksigner verify --print-certs`；核对最终包名、版本和非 debug 签名。不打印口令。
- [ ] 8. 更新 fork/签名文档与 `docs/android-mtls.md`，明确 unsigned artifact 不能直接安装；不同包名可以并行安装。按中文提交规范提交代码与文档。

### 任务3：手机实测和交付

**接口：** 使用任务2验证签名后的 APK；ADB `D:/chengxu/k20pro/tools/platform-tools/adb.exe`；先核实序列号，不假设设备仍连接。

- [ ] 1. `adb devices` 确认授权设备，通过 ADB 安装新包，核实原 `ai.unlgame.codexhostmobile` 与新包均存在。启动新 App，检查显示名称正确。
- [ ] 2. 使用私有连接资料加入19445/19446两台网关，不输出完整 token。首次系统证书授权由用户选择 `Codex Mobile - Redmi K90 Pro Max`；不自动操作凭据确认对话框。
- [ ] 3. 先取消选择一次并重试，确认能重新选择；完成授权后验证 HTTP 设备探测、WSS 初始化、会话列表及已有会话恢复。确认并发请求不会重复弹窗或卡死。
- [ ] 4. 无证书及非受信证书请求被拒绝，带证书无 token 仍被拒绝；仅检查状态码，不输出凭据。不能用 Node 验证替代 App 端验收。
- [ ] 5. 分别在 Wi-Fi 和移动流量测试，重启 App 后再测试。记录真实通过项，无法验证的项目明确标记待验收，不声称完成。
- [ ] 6. 运行相关回归和最终 diff 检查，更新文档及本机长期记忆中的手机验收事实。交付 fork 链接、本机签名 APK 路径和证书使用说明；普通公网入口的收紧留作单独后续操作。

## 执行与提交状态

设计已确认，本计划待用户选择执行方式后实施。推荐当前会话直接执行：三项任务依赖连续，便于保持构建与手机状态一致；完成后安排独立代码审查。当前 Git 作者身份未配置，提交前先解决，不伪造身份，不因此阻止测试和实现。

## 2026-11-11 执行进度

实现与 fork 构建已提交到 `codex/android-mtls`（59a14d9），Node 474 项、前端构建、云端 Gradle 原生单测与 release 编译均通过。下载摘要、zipalign、APK v2/v3、本机持久化 RSA4096 签名及独立包名验证通过，已并行安装至红米，保留原版。手机系统证书选择及 Wi-Fi/流量端到端验收尚未完成，未发布 Release、未收紧普通入口。

原生测试先于处理器编写，但因本机没有 JDK17/SDK，未观察原生实现前的失败运行；云端首轮直接通过，不能声称原生处理器完成严格 RED→GREEN。补丁与更新来源测试已观察失败再通过。补丁 fixture 暂为手写最小结构，由云端完整固定版本生成工程验证补充；待补覆盖为后台加载中重复选择回调。
