import { createRequire } from "node:module";
import { afterEach, describe, expect, it, vi } from "vitest";

const require = createRequire(import.meta.url);
const fs = require("node:fs");
const { fixWindowsFileUrlPath, install } = require("../../hooks/lib/win-path-fix.js");

describe("hooks/lib/win-path-fix", () => {
	it("removes the leading slash before a drive letter", () => {
		expect(fixWindowsFileUrlPath("/G:/Projects/a/x.wasm")).toBe(
			"G:/Projects/a/x.wasm",
		);
		expect(fixWindowsFileUrlPath("\\C:\\Users\\x.wasm")).toBe("C:\\Users\\x.wasm");
	});

	it("leaves other paths and non-strings alone", () => {
		expect(fixWindowsFileUrlPath("G:/already/ok.wasm")).toBe("G:/already/ok.wasm");
		expect(fixWindowsFileUrlPath("/home/user/x.wasm")).toBe("/home/user/x.wasm");
		const bytes = new Uint8Array(2);
		expect(fixWindowsFileUrlPath(bytes)).toBe(bytes);
	});

	it("does nothing on non-Windows platforms", () => {
		expect(install({ platform: "linux" })).toBe(false);
	});

	describe("on win32", () => {
		const realPromise = fs.promises.readFile;
		const realSync = fs.readFileSync;
		const realCb = fs.readFile;
		afterEach(() => {
			fs.promises.readFile = realPromise;
			fs.readFileSync = realSync;
			fs.readFile = realCb;
		});

		it("rewrites paths passed to fs/promises.readFile, including via dynamic import", async () => {
			const spy = vi.fn(async () => Buffer.from("ok"));
			fs.promises.readFile = spy;
			expect(install({ platform: "win32" })).toBe(true);
			// Same call web-tree-sitter makes: await import("fs/promises") then readFile
			const fsp = await import("node:fs/promises");
			await fsp.readFile("/G:/Projects/x/tree-sitter-java_orchard.wasm");
			expect(spy).toHaveBeenCalledWith(
				"G:/Projects/x/tree-sitter-java_orchard.wasm",
			);
		});
	});
});
