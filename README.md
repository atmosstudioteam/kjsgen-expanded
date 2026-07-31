# KubeJS Generator (kjsgen)

A visual, JEI-style recipe editor for **Minecraft 1.21.1 / NeoForge** that exports
ready-to-use **KubeJS** scripts (`kubejs/server_scripts/*.js`).

The editor is built entirely on stock Minecraft screens (`GuiGraphics` + vanilla
widgets) — no third-party UI library.

## Features

- **JEI-style editor** — 18×18 slot grid with vanilla arrow/flame decorations,
  opened in-game with a keybind (**K** by default).
- **Import from JEI** — pull any installed mod's recipe layout straight from its
  JEI category (pixel-perfect slot positions), then fill in the KubeJS
  template. Imported layouts live in `kjsgen/layouts/*.json` and load on
  startup even without JEI.
- **Live JEI rendering & layout editor** — for imported types, the canvas draws
  the mod's real category panel and lets you drag its actual JEI slots to new
  positions with the mouse.
- **Full slot editing** — item / item tag / fluid / fluid tag, count, drop
  chance, data components (SNBT), with search by id or name.
- **Recipe settings** — id, group, comment, export file, "if mod loaded"
  condition, plus per-type fields (cook time, XP, etc.).
- **Real-time validation** and **live code preview** as you edit.
- **Projects** — save/load editor sessions independently of export.
- **Smart export** — marker blocks (`// kjsgen:start <project>` /
  `// kjsgen:end <project>`) so re-exporting only replaces its own block,
  never hand-written code. Recipes can be split across multiple `.js` files.
- Writes only inside `kubejs/server_scripts/` of the current instance.
- Access limited to singleplayer or server operators (permission level 2+).

## Supported recipe types

| Mod | Recipe types |
|---|---|
| Minecraft | Shaped Crafting, Shapeless Crafting, Smelting, Blasting, Smoking, Campfire Cooking, Stonecutting, Smithing (Transform/Trim), Potion Brewing¹ |
| [Create](https://modrinth.com/mod/create) | Crushing, Milling, Mixing, Compacting, Pressing, Cutting, Deploying, Filling, Emptying, Splashing, Haunting, Sandpaper Polishing, Mechanical Crafting, Sequenced Assembly |
| [Mekanism](https://modrinth.com/mod/mekanism) | Enriching, Crushing, Compressing, Combining, Purifying, Sawing, Smelting, Injecting, Oxidizing, Dissolution, Crystallizing, Chemical Infusing, Metallurgic Infusing, Energy Conversion |

¹ Requires the [MoreJS](https://kubejs.com/wiki/addons/morejs) addon
(`MoreJSEvents.registerPotionBrewing`).

Create/Mekanism types are always available in the editor and export with an
`if (Platform.isLoaded(...))` guard. Any other mod's recipes can be added via
**Import from JEI**, or by an addon registering its own type (see below).

## Usage

1. Install **KubeJS** (needed for the exported scripts to run; the editor
   itself works without it).
2. Press **K** in-game (rebindable) to open the editor.
3. **+ Add Recipe** → pick a type from the searchable grid.
4. Click a slot to search/type an id, set a tag/fluid, count, chance, or data
   components. Right-click a slot to clear it.
5. Set recipe parameters on the side panel and watch the live code preview.
6. **Export** → review the generated files, then write them to
   `kubejs/server_scripts/`.
7. `/reload` in-game to pick up the new recipes.

## Addon API

A third-party mod can register its own recipe types without touching kjsgen,
in one of two ways.

### 1. JSON only (no code)

Drop a file at `assets/<your_namespace>/kjsgen_layouts/<name>.json`:

```json
{
  "id": "mymod:press",
  "mod": "mymod",
  "icon": "mymod:press_machine",
  "width": 82,
  "height": 34,
  "slots": [
    { "key": "input",  "role": "INPUT",  "x": 0,  "y": 8, "required": true, "item": true, "tag": true },
    { "key": "output", "role": "OUTPUT", "x": 60, "y": 8, "required": true, "item": true, "count": true, "chance": true }
  ],
  "decorations": [ { "type": "ARROW", "x": 26, "y": 9 } ],
  "parameters": [
    { "key": "__template", "type": "STRING",
      "default": "event.recipes.mymod.press({slot:output}, {slot:input}).time({param:time})" },
    { "key": "time", "type": "INT", "default": "100" }
  ],
  "codegen": "kjsgen:template",
  "requiresMod": "mymod"
}
```

Template placeholders: `{slot:KEY}`, `{inputs}`, `{outputs}` (all filled
inputs/outputs, comma-separated), `{param:KEY}`, `{param_str:KEY}`, `{id}`,
`{group}`. The `.id(...)`/`.group(...)` suffix is added automatically (disable
with `__suffix_id = "false"`).

Decoration types: `ARROW`, `FLAME`, `PLUS`, `TEXT` (+ a `"text"` field).

### 2. Java (custom codegen)

```java
modEventBus.addListener((RegisterRecipeTypesEvent e) -> {
    e.registerCodegen("mymod:press", (recipe, type) -> "event.recipes.mymod.press(...)");
    e.registerType(new RecipeTypeDefinition("mymod:press", "mymod", "mymod:press_machine",
            82, 34, slots, decorations, parameters, "mymod:press", "mymod"));
});
```

`com.zizazr.kjsgen.api.RegisterRecipeTypesEvent` fires on the mod bus during
common setup. `RecipeCodegen.wrapperHeader()/wrapperFooter()` let you generate
code under a different KubeJS event (as the built-in brewing type does for
MoreJS).

## Building

```bash
./gradlew build   # jar in build/libs/
```

Dependencies: NeoForge 21.1.x, Java 21.
