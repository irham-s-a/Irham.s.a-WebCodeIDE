import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";

const root = path.resolve(__dirname, "../..");
const framework = path.join(
	root,
	"node_modules/cordova-android/framework/src/org/apache/cordova",
);
const canRun = process.platform !== "win32" && fs.existsSync(framework);

// Meniru Cordova: before_prepare ditunggu dulu, baru after_prepare.
// Regresi v0.4.2: modify-java-files.js dulu tidak ditunggu sehingga menimpa
// patch immersive-fullscreen.js -> "cannot find symbol setFullscreenBackHandler".
describe.skipIf(!canRun)("cordova hooks ordering", () => {
	it("keeps the fullscreen patch after modify-java-files finishes", () => {
		const sim = fs.mkdtempSync(path.join(os.tmpdir(), "webcode-hooks-"));
		const lib = path.join(
			sim,
			"platforms/android/CordovaLib/src/org/apache/cordova",
		);
		fs.mkdirSync(lib, { recursive: true });
		fs.cpSync(framework, lib, { recursive: true });
		fs.symlinkSync(path.join(root, "node_modules"), path.join(sim, "node_modules"));
		fs.cpSync(path.join(root, "hooks"), path.join(sim, "hooks"), {
			recursive: true,
		});

		const script = `
			(async () => {
				const ctx = { opts: { platforms: ["android"], projectRoot: ${JSON.stringify(sim)} } };
				await require("./hooks/modify-java-files.js")(ctx);
				await require("./hooks/immersive-fullscreen.js")(ctx);
			})().catch((e) => { console.error(e); process.exit(1); });
		`;
		execFileSync(process.execPath, ["-e", script], {
			cwd: sim,
			stdio: "pipe",
			timeout: 120000,
		});

		const impl = fs.readFileSync(path.join(lib, "CordovaWebViewImpl.java"), "utf8");
		expect(impl).toContain("setFullscreenBackHandler");
		expect(impl).toContain("setFullscreenOrientation");
		expect(impl).toContain("setInputType");
		expect(fs.existsSync(path.join(lib, "ImmersiveFullscreen.java"))).toBe(true);
		fs.rmSync(sim, { recursive: true, force: true });
	}, 180000);
});
