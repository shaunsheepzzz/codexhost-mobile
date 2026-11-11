import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { describe, it, expect } from "vitest";

const activity = `package com.app.pakeplus
class MainActivity : AppCompatActivity() {
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
    inner class MyWebViewClient(val debug: Boolean) : WebViewClient() {
    }
}
`;

describe("Android mTLS patch", () => {
  for (const variant of ["valid", "missing", "duplicate"] as const) {
    it(`handles ${variant} pinned activity anchors`, () => {
      const root = mkdtempSync(join(tmpdir(), "mobile-mtls-"));
      try {
        const source = join(root, "app/src/main/java/com/app/pakeplus");
        mkdirSync(source, { recursive: true });
        writeFileSync(join(source, "MainActivity.kt"), variant === "missing"
          ? activity.replace("override fun onDestroy()", "override fun changed()")
          : variant === "duplicate" ? activity + activity : activity);
        writeFileSync(join(root, "app/build.gradle.kts"), "android {\n}\ndependencies {\n}\n");
        const result = spawnSync("python", [resolve("scripts/apply-android-mtls.py"), root], { encoding: "utf8" });
        if (variant !== "valid") {
          expect(result.status).not.toBe(0);
          expect(result.stderr).toContain("anchor");
          expect(readFileSync(join(source, "MainActivity.kt"), "utf8")).not.toContain("ClientCertificateHandler(this)");
        } else {
          expect(result.stderr).toBe("");
          expect(result.status).toBe(0);
          const patched = readFileSync(join(source, "MainActivity.kt"), "utf8");
          expect(patched).toContain("override fun onReceivedClientCertRequest");
          expect(patched.indexOf("clientCertificateHandler.close()")).toBeLessThan(patched.indexOf("webView.destroy()"));
          expect(readFileSync(join(source, "ClientCertificateHandler.kt"), "utf8")).toContain("KeyChain");
          expect(readFileSync(join(root, "app/src/test/java/com/app/pakeplus/ClientCertificateHandlerTest.kt"), "utf8")).toContain("@Test");
          expect(readFileSync(join(root, "app/build.gradle.kts"), "utf8")).toContain("robolectric");
          const repeated = spawnSync("python", [resolve("scripts/apply-android-mtls.py"), root], { encoding: "utf8" });
          expect(repeated.status).not.toBe(0);
        }
      } finally { rmSync(root, { recursive: true, force: true }); }
    });
  }
});
