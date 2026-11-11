# Android 本机签名

CI 下载产物为 `app-release-unsigned.apk` 与同名 `.sha256`；不能直接安装，也不能当作 App 更新发布。

签名前先核对下载文件 SHA256。使用 Android 官方 build-tools 的 `zipalign -P 16 -f 4` 对齐，再使用 `apksigner sign` 签名，最终执行 `apksigner verify --verbose --print-certs`，核对包名、版本、release 非 debug 签名和证书指纹。

持久化 keystore 与随机口令保存在本机仓库外受限目录；后续升级必须使用同一包名和签名。不得提交、打印或上传 keystore/口令到 GitHub。使用 `--ks-pass file:<受限口令文件>` 和 `--key-pass file:<受限口令文件>`，不把口令放进命令行。

本分支包名 `io.github.shaunsheepzzz.codexhostmobile`，最终签名文件沿用 `CodexHostMobile-v<version>.apk`。原版使用不同包名和签名，保持安装和数据不变。

详见 [mTLS 使用与验收](android-mtls.md)。
