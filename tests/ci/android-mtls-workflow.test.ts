import { readFileSync } from "node:fs";
import { parse } from "yaml";
import { expect, it } from "vitest";

it("只手动构建独立包，测试先于编译且只上传未签名产物", () => {
  const source = readFileSync(".github/workflows/build-android.yml", "utf8");
  const workflow = parse(source);
  expect(Object.keys(workflow.on)).toEqual(["workflow_dispatch"]);
  expect(Object.keys(workflow.jobs)).toEqual(["build"]);
  expect(workflow.env.APP_ID).toBe("io.github.shaunsheepzzz.codexhostmobile");
  expect(workflow.env.DISPLAY_NAME).toBe("Codex Mobile mTLS");
  expect(source).not.toContain("secrets.");
  const steps = workflow.jobs.build.steps;
  const testIndex = steps.findIndex((s: { run?: string }) => s.run?.includes("./gradlew testReleaseUnitTest"));
  const buildIndex = steps.findIndex((s: { run?: string }) => s.run?.includes("./gradlew assembleRelease"));
  expect(testIndex).toBeGreaterThan(-1);
  expect(testIndex).toBeLessThan(buildIndex);
  const upload = steps.filter((s: { uses?: string }) => s.uses?.startsWith("actions/upload-artifact@"));
  expect(upload).toHaveLength(1);
  expect(upload[0].with.path).toContain("app-release-unsigned.apk");
  expect(source).not.toContain("gh release create");
});
