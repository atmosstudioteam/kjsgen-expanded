# kjsgen — CLAUDE.md

KubeJS Generator: a NeoForge 1.21.1 (Java 21) dev-tool mod. A JEI-like visual
recipe editor that exports KubeJS scripts (`kubejs/server_scripts/*.js`).

## UI: vanilla Screens

**The editor lives in package `ui/vanilla/`** and is built entirely on stock
Minecraft — no third-party UI library. This is the interface to modify when the
user asks for editor/UI changes.

- Entry point: [VanillaEditorScreen](src/main/java/com/zizazr/kjsgen/ui/vanilla/VanillaEditorScreen.java)
  — a plain `net.minecraft.client.gui.screens.Screen`, opened with keybind **K**
  (`key.kjsgen.open_editor`) via `KjsGenClient.openEditor()`.
- Dialogs extend [VanillaDialogScreen](src/main/java/com/zizazr/kjsgen/ui/vanilla/VanillaDialogScreen.java)
  (renders `renderBackground` as backdrop, NOT the parent screen — see gotcha below).
- Screens: `VanillaSlotEditScreen`, `VanillaTypePickerScreen`, `VanillaProjectsScreen`,
  `VanillaExportScreen`, `VanillaCodePreviewScreen`.
- `VanillaTheme` holds the colour palette + item/fluid icon helpers — everything is
  `GuiGraphics` + stock Minecraft widgets (`EditBox`, `Button`, `CycleButton`).

### Rendering gotchas (found via in-game testing, don't reintroduce)

- `GuiGraphics` **batches text and item rendering** and flushes at end of frame. Anything drawn
  by a "parent" screen paints ON TOP of stuff drawn after it in the same frame if the parent's
  batch hasn't been flushed. This is why dialogs must NOT re-render the parent editor as a
  backdrop — use `renderBackground(g, mouseX, mouseY, partialTick)` instead.
- `enableScissor()`/`disableScissor()` flush the batch within the clip bounds, so scrollable
  regions with items/text render correctly.
- Always draw text with shadow (`drawString(..., true)`) — it's unreadable without it on the
  blurred backdrop.
- Compute dialog height from the actual bottom of the last widget, not a hardcoded guess — too
  short a dialog makes rows overlap the action buttons.

### Build / verify

```bash
./gradlew compileJava   # fast correctness check against the real 1.21.1 API
./gradlew build          # jar in build/libs/
```

No unit tests exist; correctness is verified by compiling against the NeoForge/MC API and by
manual in-game testing (`./gradlew runClient`).

See [docs/adding-recipe-types.md](docs/adding-recipe-types.md) for how to add a new recipe type
(JSON layout + template codegen vs. a custom Java codegen handler).

## Project structure

```
core/                 — data model (SlotContent, RecipeInstance, RecipeProject, type registry, validator)
templates/            — built-in recipe types + JSON layout loader (kjsgen_layouts)
codegen/              — RecipeCodegen + per-type KubeJS code generators
integration/kubejs/   — script assembly + marker-block export
integration/jei/      — JEI layout import + "edit in kjsgen" recipe button
ui/vanilla/           — vanilla Screen editor (keybind K)
api/                  — RegisterRecipeTypesEvent for addon mods
```

## Key versions

NeoForge `21.1.235`, Parchment mappings for MC `1.21.1`, Java 21. Dev-only (`localRuntime`)
mods used for `runClient` testing: KubeJS `2101.7.2-build.369`, JEI `19.27.0.346`,
Mekanism `1.21.1-10.7.9.72`, Create `6.0.10-280`.

## When to use subagents

By default, small/targeted tasks (read one file, grep one symbol, fix one bug) are done directly
— spawning an agent for those just burns time for no benefit. The two rules below are hard
requirements because guessing wrong here has repeatedly cost real rework on this project; treat
them as blocking, not optional:

- **MUST use docs-search-agent** before writing or editing any code that calls into NeoForge,
  Minecraft, JEI, or KubeJS/MoreJS APIs you have not already verified earlier in this
  same session. Do not pattern-match from memory or from similar-looking code elsewhere in the
  repo — this project has shipped wrong JEI signatures from guessing before. Also usable
  for KubeJS wiki lookups.
- **MUST use log-analyzer** instead of pasting raw output into the conversation whenever
  `./gradlew build`/`runClient` fails or a Minecraft crash log needs diagnosing.

The rest are judgment calls — reach for them when the task genuinely matches, not by default:

- **Explore** — broad, unfamiliar-territory sweeps across the repo, e.g. "where does the JEI
  import map categories to layouts" or "find every codegen handler that calls
  `wrapperHeader()`". Use when the answer requires scanning across `core/`, `codegen/`,
  `integration/` and `ui/vanilla/` and you only need the conclusion.
- **search-agent** — a quick, narrow lookup when you already roughly know where to look (a known
  symbol, a specific string, "which file defines `SlotRole`"). Lighter than Explore.
- **search-specialist** — web research for third-party mod recipe DSLs not vendored locally
  (Create, Mekanism, MoreJS syntax) when an addon-api integration needs more than a best-guess
  template.
- **planning-agent** — get a spec before implementing a non-trivial feature (e.g. a live JEI
  render/layout editor in the vanilla UI, adding a new addon recipe-type family), then
  implement it yourself.
- **general-purpose** — multi-step tasks spanning several files/edits where the scope isn't yet
  clear from a single targeted search.

If a task doesn't clearly match any of the above, do it directly rather than delegating. If you
want an agent used for something ad hoc, name it explicitly in the request (e.g. "используй
docs-search-agent, чтобы проверить...") — that reliably forces delegation even for small asks.


## Conventions

- Access is gated to singleplayer or server operators (permission level 2+) — a dev tool, not
  a player-facing feature.
- Export uses marker blocks `// kjsgen:start <project>` / `// kjsgen:end <project>` inside
  `kubejs/server_scripts/`; re-export replaces only that block.
- See [README.md](README.md) for the full feature list and the addon API for third-party
  mods contributing recipe types.
