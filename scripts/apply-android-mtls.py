"""Apply the mTLS callback to pinned PakePlus after the existing hardening step."""
import sys
from pathlib import Path


def once(source, anchor, replacement):
    if source.count(anchor) != 1:
        raise SystemExit(f"mTLS anchor must occur exactly once: {anchor}")
    return source.replace(anchor, replacement, 1)


def main(root):
    source_dir = root / "app/src/main/java/com/app/pakeplus"
    activity_path = source_dir / "MainActivity.kt"
    gradle_path = root / "app/build.gradle.kts"
    source = activity_path.read_text(encoding="utf-8")
    if "clientCertificateHandler" in source:
        raise SystemExit("mTLS anchor already patched")
    source = once(source, "class MainActivity : AppCompatActivity() {",
                  "class MainActivity : AppCompatActivity() {\n"
                  "    private val clientCertificateHandler by lazy { ClientCertificateHandler(this) }\n")
    source = once(source, "override fun onDestroy() {",
                  "override fun onDestroy() {\n        clientCertificateHandler.close()")
    anchor = "inner class MyWebViewClient(val debug: Boolean) : WebViewClient() {"
    source = once(source, anchor, anchor + """
        override fun onReceivedClientCertRequest(
            view: android.webkit.WebView?,
            request: android.webkit.ClientCertRequest,
        ) {
            clientCertificateHandler.handle(request)
        }
""")
    gradle = gradle_path.read_text(encoding="utf-8")
    gradle = once(gradle, "android {", "android {\n    testOptions { unitTests.isIncludeAndroidResources = true }")
    gradle = once(gradle, "dependencies {", """dependencies {
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.mockito:mockito-core:4.11.0")
""")
    assets = Path(__file__).resolve().parents[1] / "android/mtls"
    handler = (assets / "ClientCertificateHandler.kt").read_text(encoding="utf-8")
    tests = (assets / "ClientCertificateHandlerTest.kt").read_text(encoding="utf-8")
    test_dir = root / "app/src/test/java/com/app/pakeplus"
    test_dir.mkdir(parents=True, exist_ok=True)
    activity_path.write_text(source, encoding="utf-8")
    gradle_path.write_text(gradle, encoding="utf-8")
    (source_dir / "ClientCertificateHandler.kt").write_text(handler, encoding="utf-8")
    (test_dir / "ClientCertificateHandlerTest.kt").write_text(tests, encoding="utf-8")


if __name__ == "__main__":
    main(Path(sys.argv[1]).resolve())
