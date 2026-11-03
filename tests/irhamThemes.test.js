import { EditorState } from "@codemirror/state";
import { describe, expect, it } from "vitest";
import {
	createIrhamTheme,
	irhamThemeConfigs,
} from "../src/cm/themes/irhamThemes";

const REQUIRED = [
	"name", "caption", "background", "foreground", "selection", "cursor",
	"dropdownBackground", "dropdownBorder", "activeLine", "lineNumber",
	"lineNumberActive", "matchingBracket", "keyword", "storage", "variable",
	"parameter", "function", "memberProperty", "string", "constant", "type",
	"class", "number", "comment", "heading", "invalid", "regexp",
];

describe("Irham.s.a WebCode IDE themes", () => {
	it("has unique ids", () => {
		const ids = irhamThemeConfigs.map((c) => c.name);
		expect(new Set(ids).size).toBe(ids.length);
	});

	for (const config of irhamThemeConfigs) {
		it(`${config.name} is complete and valid`, () => {
			for (const key of REQUIRED) {
				expect(config[key], `${config.name}.${key}`).toBeTruthy();
			}
			for (const key of REQUIRED.slice(2)) {
				expect(config[key]).toMatch(/^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/);
			}
			const state = EditorState.create({
				doc: "",
				extensions: createIrhamTheme(config),
			});
			expect(state).toBeTruthy();
		});
	}
});
