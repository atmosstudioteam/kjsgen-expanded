package com.zizazr.kjsgen.integration.kubejs;

import com.zizazr.kjsgen.core.CreateSpecialModel;
import com.zizazr.kjsgen.core.ParameterDefinition;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses hand-written or previously exported KubeJS server scripts back into
 * {@link RecipeInstance}s — the reverse of the {@code codegen} handlers. It scans
 * a whole file for {@code event.<method>(...)} statements and reconstructs the
 * editable recipe for the built-in vanilla types this tool knows how to author
 * ({@code shaped}, {@code shapeless}, the four cooking families, {@code stonecutting},
 * {@code smithingTransform}/{@code smithingTrim}).
 *
 * <p>{@code event.recipes.<mod>.<type>(...)} calls (Create, Mekanism and any addon
 * layout) are reversed generically: the incoming call is matched against the
 * registered {@link RecipeTypeDefinition} whose {@code __template} names the same
 * method, and the arguments/chained modifiers are mapped back onto the type's
 * slots and parameters by interpreting the template placeholders in reverse. The
 * two Create types with a bespoke Java generator (mechanical crafting's resizable
 * grid, sequenced assembly's stage chain) have dedicated handlers.
 *
 * <p>It is deliberately tolerant: an unrecognised statement (a {@code recipes.*}
 * call with no matching layout, a custom serializer) is counted as skipped rather
 * than failing the whole import, and {@code event.remove(...)} /
 * other non-recipe calls are silently ignored. Argument expressions are parsed
 * with a small brace/bracket/string-aware scanner rather than a real JS engine,
 * so exotic hand-written syntax may not round-trip perfectly; the imported recipe
 * is still added so the user can fix it up in the editor.
 */
public final class JsRecipeParser {

    /**
     * @param recipes    the recipes that were successfully reconstructed
     * @param recognized {@code recipes.size()} (recipes we could rebuild)
     * @param skipped    recipe-producing statements we saw but could not parse
     *                   (e.g. Create machine recipes) — reported so the user knows
     *                   the import wasn't complete
     */
    public record Result(List<RecipeInstance> recipes, int recognized, int skipped) {
    }

    /** The bare recipe-add methods we can reconstruct; anything else is "unsupported". */
    private static final java.util.Set<String> KNOWN_METHODS = java.util.Set.of(
            "shaped", "shapeless", "smelting", "blasting", "smoking",
            "campfireCooking", "campfire_cooking", "stonecutting",
            "smithingTransform", "smithingTrim");

    private static final Pattern COUNT_PREFIX = Pattern.compile("^(\\d+)x\\s+(.+)$");

    // ---- template-reversal placeholders (mirror TemplateRecipeCodegen) --------
    /** Pulls the method path out of a template call, e.g. {@code recipes.create.pressing}. */
    private static final Pattern TEMPLATE_METHOD = Pattern.compile("event\\.([A-Za-z0-9_.]+)\\(");
    private static final Pattern PH_ARG = Pattern.compile("\\{(?:slot|param_str|param):([^}]+)}");
    private static final Pattern PH_SLOT = Pattern.compile("\\{slot:([^}]+)}");
    private static final Pattern PH_OPT_CHANCE = Pattern.compile("\\{opt_slot_chance:([^:]+):([^}]+)}");
    private static final Pattern PH_PARAM_CALL = Pattern.compile("\\.([A-Za-z0-9_]+)\\(\\{param(?:_str)?:([^}]+)}\\)");
    private static final Pattern PH_OPT_CALL = Pattern.compile("\\{opt_call:([^}]+)}");
    private static final Pattern PH_FLAG_CALL = Pattern.compile("\\{flag_call:([^:]+):([^}]+)}");

    private JsRecipeParser() {
    }

    public static Result parse(String script) {
        String clean = stripComments(script);
        List<RecipeInstance> out = new ArrayList<>();
        int skipped = 0;
        int i = 0;
        while (true) {
            int at = indexOfEvent(clean, i);
            if (at < 0) {
                break;
            }
            int[] end = {at + 5};
            Chain chain = parseChain(clean, at + 5, end);
            if (chain == null) {
                i = at + 5;
                continue;
            }
            i = end[0];
            RecipeInstance recipe = buildRecipe(chain);
            if (recipe != null) {
                out.add(recipe);
            } else if (isRecipeProducing(chain.method)) {
                skipped++;
            }
        }
        return new Result(out, out.size(), skipped);
    }

    // ------------------------------------------------------------------ dispatch

    private static boolean isRecipeProducing(String method) {
        return KNOWN_METHODS.contains(method) || method.startsWith("recipes.");
    }

    private static RecipeInstance buildRecipe(Chain chain) {
        RecipeInstance recipe = switch (chain.method) {
            case "shaped" -> buildShaped(chain.args);
            case "shapeless" -> buildShapeless(chain.args);
            case "smelting" -> buildCooking("kjsgen:smelting", chain);
            case "blasting" -> buildCooking("kjsgen:blasting", chain);
            case "smoking" -> buildCooking("kjsgen:smoking", chain);
            case "campfireCooking", "campfire_cooking" -> buildCooking("kjsgen:campfire_cooking", chain);
            case "stonecutting" -> buildTwoSlot("kjsgen:stonecutting", chain.args);
            case "smithingTransform" -> buildSmithingTransform(chain.args);
            case "smithingTrim" -> buildSmithingTrim(chain.args);
            case "recipes.create.mechanical_crafting" -> buildMechanicalCrafting(chain.args);
            case "recipes.create.sequenced_assembly" -> buildSequencedAssembly(chain);
            default -> chain.method.startsWith("recipes.") ? buildFromTemplate(chain) : null;
        };
        if (recipe != null) {
            applyCommonModifiers(recipe, chain);
        }
        return recipe;
    }

    private static RecipeInstance buildShaped(List<String> args) {
        if (args.size() < 3) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance("kjsgen:shaped");
        setSlot(recipe, "output", parseContent(args.get(0)));
        List<String> rows = arrayElements(args.get(1));
        Map<String, String> mapping = objectEntries(args.get(2));
        for (int r = 0; r < Math.min(rows.size(), 3); r++) {
            String row = unquote(rows.get(r));
            for (int c = 0; c < Math.min(row.length(), 3); c++) {
                char ch = row.charAt(c);
                if (ch == ' ') {
                    continue;
                }
                String expr = mapping.get(String.valueOf(ch));
                if (expr != null) {
                    setSlot(recipe, "in" + (r * 3 + c), parseContent(expr));
                }
            }
        }
        return recipe;
    }

    private static RecipeInstance buildShapeless(List<String> args) {
        if (args.size() < 2) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance("kjsgen:shapeless");
        setSlot(recipe, "output", parseContent(args.get(0)));
        for (String expr : arrayElements(args.get(1))) {
            SlotContent content = parseContent(expr);
            if (content != null && !content.isEmpty()) {
                recipe.setListSlot("in", recipe.listSlots("in").size(), content);
            }
        }
        return recipe;
    }

    /** output + single input (stonecutting). */
    private static RecipeInstance buildTwoSlot(String typeId, List<String> args) {
        if (args.size() < 2) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance(typeId);
        setSlot(recipe, "output", parseContent(args.get(0)));
        setSlot(recipe, "input", parseContent(args.get(1)));
        return recipe;
    }

    private static RecipeInstance buildCooking(String typeId, Chain chain) {
        if (chain.args.size() < 2) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance(typeId);
        setSlot(recipe, "output", parseContent(chain.args.get(0)));
        setSlot(recipe, "input", parseContent(chain.args.get(1)));
        for (Mod mod : chain.mods) {
            if (mod.args.isEmpty()) {
                continue;
            }
            switch (mod.name) {
                case "xp" -> recipe.setParam("xp", mod.args.get(0).trim());
                case "cookingTime" -> recipe.setParam("cookingTime", mod.args.get(0).trim());
                default -> {
                }
            }
        }
        return recipe;
    }

    private static RecipeInstance buildSmithingTransform(List<String> args) {
        if (args.size() < 4) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance("kjsgen:smithing_transform");
        setSlot(recipe, "output", parseContent(args.get(0)));
        setSlot(recipe, "template", parseContent(args.get(1)));
        setSlot(recipe, "base", parseContent(args.get(2)));
        setSlot(recipe, "addition", parseContent(args.get(3)));
        return recipe;
    }

    private static RecipeInstance buildSmithingTrim(List<String> args) {
        if (args.size() < 3) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance("kjsgen:smithing_trim");
        setSlot(recipe, "template", parseContent(args.get(0)));
        setSlot(recipe, "base", parseContent(args.get(1)));
        setSlot(recipe, "addition", parseContent(args.get(2)));
        return recipe;
    }

    // ------------------------------------------------------------------ Create (bespoke Java generators)

    /** {@code event.recipes.create.mechanical_crafting(output, [pattern...], { A: '...' })}. */
    private static RecipeInstance buildMechanicalCrafting(List<String> args) {
        if (args.size() < 3) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance("kjsgen:create_mechanical_crafting");
        setSlot(recipe, "output", parseContent(args.get(0)));
        List<String> rows = new ArrayList<>();
        for (String row : arrayElements(args.get(1))) {
            rows.add(unquote(row));
        }
        Map<String, String> mapping = objectEntries(args.get(2));
        int h = Math.min(rows.size(), CreateSpecialModel.GRID_MAX);
        int w = 0;
        for (int r = 0; r < h; r++) {
            w = Math.max(w, rows.get(r).length());
        }
        w = Math.min(w, CreateSpecialModel.GRID_MAX);
        recipe.setParam("gridW", Integer.toString(Math.max(1, w)));
        recipe.setParam("gridH", Integer.toString(Math.max(1, h)));
        for (int r = 0; r < h; r++) {
            String row = rows.get(r);
            for (int c = 0; c < Math.min(row.length(), w); c++) {
                char ch = row.charAt(c);
                if (ch == ' ') {
                    continue;
                }
                String expr = mapping.get(String.valueOf(ch));
                if (expr != null) {
                    setSlot(recipe, CreateSpecialModel.cellKey(r, c), parseContent(expr));
                }
            }
        }
        return recipe;
    }

    /**
     * {@code event.recipes.create.sequenced_assembly([outputs], input, [steps])
     * .transitionalItem(trans).loops(n)} — each step is a nested
     * {@code event.recipes.create.<stage>(...)} call whose optional deployed/filled
     * ingredient is the second element of its {@code [trans, item]} argument.
     */
    private static RecipeInstance buildSequencedAssembly(Chain chain) {
        List<String> args = chain.args;
        if (args.size() < 3) {
            return null;
        }
        RecipeInstance recipe = new RecipeInstance("kjsgen:create_sequenced_assembly");
        RecipeTypeDefinition type = RecipeTypeRegistry.get("kjsgen:create_sequenced_assembly").orElse(null);
        List<String> outs = arrayElements(args.get(0));
        if (type != null) {
            assignRoleList(recipe, type, SlotRole.OUTPUT, outs);
        } else {
            String[] keys = {"output", "output2", "output3"};
            for (int i = 0; i < keys.length && i < outs.size(); i++) {
                setSlot(recipe, keys[i], parseContent(outs.get(i)));
            }
        }
        setSlot(recipe, "input", parseContent(args.get(1)));

        int idx = 0;
        for (String step : arrayElements(args.get(2))) {
            int at = indexOfEvent(step, 0);
            if (at < 0) {
                continue;
            }
            int[] e = {at + 5};
            Chain sc = parseChain(step, at + 5, e);
            if (sc == null || !sc.method.startsWith("recipes.")) {
                continue;
            }
            String stepId = sc.method.substring(sc.method.lastIndexOf('.') + 1);
            recipe.setParam("seq" + idx, stepId);
            if (CreateSpecialModel.stepType(stepId).hasItem() && sc.args.size() >= 2) {
                List<String> pair = arrayElements(sc.args.get(1));
                if (pair.size() >= 2) {
                    setSlot(recipe, "seqItem" + idx, parseContent(pair.get(1)));
                }
            }
            idx++;
        }
        recipe.setParam("__seqLen", Integer.toString(idx));

        for (Mod mod : chain.mods) {
            if (mod.args.isEmpty()) {
                continue;
            }
            switch (mod.name) {
                case "transitionalItem" -> setSlot(recipe, "transitional", parseContent(mod.args.get(0)));
                case "loops" -> recipe.setParam("loops", mod.args.get(0).trim());
                default -> {
                }
            }
        }
        return recipe;
    }

    // ------------------------------------------------------------------ template types (Create/Mekanism/addons)

    /**
     * Reverses a {@code recipes.<mod>.<type>} call by finding the registered type
     * whose template names the same method and mapping the call's arguments and
     * chained modifiers back onto its slots/parameters.
     */
    private static RecipeInstance buildFromTemplate(Chain chain) {
        for (RecipeTypeDefinition type : RecipeTypeRegistry.all()) {
            String template = templateOf(type);
            if (template.isEmpty()) {
                continue;
            }
            Matcher method = TEMPLATE_METHOD.matcher(template);
            if (!method.find() || !method.group(1).equals(chain.method)) {
                continue;
            }
            RecipeInstance recipe = new RecipeInstance(type.id());
            int open = template.indexOf('(');
            int[] end = new int[1];
            String innerArgs = readBalanced(template, open, end);
            String rest = end[0] <= template.length() ? template.substring(end[0]) : "";
            applyTemplatePositional(recipe, type, splitTop(innerArgs), chain.args);
            applyTemplateChained(recipe, type, rest, chain.mods);
            return recipe;
        }
        return null;
    }

    private static String templateOf(RecipeTypeDefinition type) {
        String template = type.parameter("__template").map(ParameterDefinition::defaultValue).orElse("");
        if (template.isEmpty()) {
            template = type.parameter("template").map(ParameterDefinition::defaultValue).orElse("");
        }
        return template;
    }

    /** Maps positional template descriptors ({@code {slot:..}}, {@code [{inputs}]}, ...) onto the call's args. */
    private static void applyTemplatePositional(RecipeInstance recipe, RecipeTypeDefinition type,
                                                List<String> descriptors, List<String> args) {
        int ai = 0;
        for (int di = 0; di < descriptors.size(); di++) {
            String d = descriptors.get(di).trim();
            if (d.contains("{outputs}")) {
                if (ai < args.size()) {
                    assignRoleList(recipe, type, SlotRole.OUTPUT, arrayElements(args.get(ai)));
                }
                ai++;
            } else if (d.contains("{inputs}")) {
                if (ai < args.size()) {
                    assignRoleList(recipe, type, SlotRole.INPUT, arrayElements(args.get(ai)));
                }
                ai++;
            } else if (d.startsWith("[")) {
                List<String> keys = slotKeys(d);
                if (ai < args.size()) {
                    List<String> elems = arrayElements(args.get(ai));
                    for (int k = 0; k < keys.size() && k < elems.size(); k++) {
                        setSlot(recipe, keys.get(k), parseForSlot(type, keys.get(k), elems.get(k)));
                    }
                }
                ai++;
            } else if (d.startsWith("{slot")) {
                String key = firstPlaceholderArg(d);
                if (key != null && ai < args.size()) {
                    setSlot(recipe, key, parseForSlot(type, key, args.get(ai)));
                }
                ai++;
                Matcher oc = PH_OPT_CHANCE.matcher(d);
                if (oc.find()) {
                    int remaining = descriptors.size() - (di + 1);
                    if (args.size() - ai > remaining) {
                        if (ai < args.size()) {
                            setSlot(recipe, oc.group(1), parseForSlot(type, oc.group(1), args.get(ai++)));
                        }
                        if (ai < args.size()) {
                            recipe.setParam(oc.group(2), args.get(ai++).trim());
                        }
                    }
                }
            } else if (d.startsWith("{param")) {
                String key = firstPlaceholderArg(d);
                if (key != null && ai < args.size()) {
                    recipe.setParam(key, d.startsWith("{param_str")
                            ? unquote(args.get(ai)) : args.get(ai).trim());
                }
                ai++;
            } else {
                ai++;
            }
        }
    }

    /** Reverses the template's chained modifiers: {@code .M({param:K})}, {@code {opt_call:K}}, {@code {flag_call:M:K}}. */
    private static void applyTemplateChained(RecipeInstance recipe, RecipeTypeDefinition type,
                                             String rest, List<Mod> mods) {
        Map<String, String> methodParam = new HashMap<>();
        Matcher pc = PH_PARAM_CALL.matcher(rest);
        while (pc.find()) {
            methodParam.put(pc.group(1), pc.group(2));
        }
        Map<String, String> flagMethod = new HashMap<>();
        Matcher fc = PH_FLAG_CALL.matcher(rest);
        while (fc.find()) {
            flagMethod.put(fc.group(1), fc.group(2));
        }
        Matcher oc = PH_OPT_CALL.matcher(rest);
        String enumParam = oc.find() ? oc.group(1) : null;

        for (Mod mod : mods) {
            if (methodParam.containsKey(mod.name) && !mod.args.isEmpty()) {
                recipe.setParam(methodParam.get(mod.name), mod.args.get(0).trim());
            } else if (flagMethod.containsKey(mod.name)) {
                recipe.setParam(flagMethod.get(mod.name), "true");
            } else if (enumParam != null && mod.args.isEmpty()) {
                List<String> options = type.parameter(enumParam)
                        .map(ParameterDefinition::options).orElse(List.of());
                if (options.contains(mod.name)) {
                    recipe.setParam(enumParam, mod.name);
                }
            }
        }
    }

    /**
     * Assign array elements to the slots of {@code role}, in layout order. A fixed slot
     * takes one element (output, output2, ...); a list slot ("in", "output", ...) absorbs
     * all remaining elements into its contiguous {@code key + index} entries.
     */
    private static void assignRoleList(RecipeInstance recipe, RecipeTypeDefinition type,
                                       SlotRole role, List<String> elems) {
        List<SlotDefinition> slots = type.slotsByRole(role);
        int ei = 0;
        for (SlotDefinition slot : slots) {
            if (ei >= elems.size()) {
                break;
            }
            if (slot.list()) {
                int idx = 0;
                for (; ei < elems.size(); ei++) {
                    SlotContent content = parseForSlot(type, slot.key(), elems.get(ei));
                    if (content != null && !content.isEmpty()) {
                        recipe.setListSlot(slot.key(), idx++, content);
                    }
                }
            } else {
                setSlot(recipe, slot.key(), parseForSlot(type, slot.key(), elems.get(ei)));
                ei++;
            }
        }
    }

    /**
     * Like {@link #parseContent} but aware of the target slot: a chemical-only slot
     * (Mekanism gases etc.) reinterprets the {@code "AMOUNTx id"} string form as a
     * chemical stack rather than a counted item stack.
     */
    private static SlotContent parseForSlot(RecipeTypeDefinition type, String slotKey, String expr) {
        SlotContent content = parseContent(expr);
        SlotDefinition def = type == null ? null : type.slot(slotKey).orElse(null);
        if (content == null || def == null || content.isEmpty()) {
            return content;
        }
        boolean chemicalOnly = def.allowsChemical() && !def.allowsItem() && !def.allowsFluid();
        if (chemicalOnly) {
            int amount = content.count() > 1 ? content.count() : 1000;
            if (content.kind() == com.zizazr.kjsgen.core.ContentKind.ITEM_TAG) {
                return SlotContent.chemicalTag(content.id(), amount);
            }
            if (content.kind() == com.zizazr.kjsgen.core.ContentKind.ITEM) {
                return SlotContent.chemical(content.id(), amount);
            }
        }
        return content;
    }

    private static String firstPlaceholderArg(String descriptor) {
        Matcher m = PH_ARG.matcher(descriptor);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> slotKeys(String descriptor) {
        List<String> keys = new ArrayList<>();
        Matcher m = PH_SLOT.matcher(descriptor);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static void applyCommonModifiers(RecipeInstance recipe, Chain chain) {
        for (Mod mod : chain.mods) {
            if (mod.args.isEmpty()) {
                continue;
            }
            switch (mod.name) {
                case "id" -> recipe.setRecipeId(unquote(mod.args.get(0)));
                case "group" -> recipe.setGroup(unquote(mod.args.get(0)));
                default -> {
                }
            }
        }
    }

    private static void setSlot(RecipeInstance recipe, String key, SlotContent content) {
        if (content != null && !content.isEmpty()) {
            recipe.setSlot(key, content);
        }
    }

    // ------------------------------------------------------------------ content

    /**
     * Reverse of {@link com.zizazr.kjsgen.codegen.JsUtil#ingredient}/{@code output}:
     * an argument expression to a {@link SlotContent}. Returns {@code null} for an
     * expression we don't understand.
     */
    static SlotContent parseContent(String rawExpr) {
        String expr = rawExpr == null ? "" : rawExpr.trim();
        if (expr.isEmpty()) {
            return SlotContent.EMPTY;
        }
        if (isQuote(expr.charAt(0))) {
            return parseStringContent(unquote(expr));
        }
        if (expr.startsWith("Fluid.of(")) {
            List<String> a = splitTop(inParens(expr));
            if (a.isEmpty()) {
                return null;
            }
            return SlotContent.fluid(unquote(a.get(0)), a.size() > 1 ? parseInt(a.get(1), 1000) : 1000);
        }
        if (expr.startsWith("Item.of(")) {
            List<String> a = splitTop(inParens(expr));
            if (a.isEmpty()) {
                return null;
            }
            String[] idComp = splitComponents(unquote(a.get(0)));
            int count = a.size() > 1 ? parseInt(a.get(1), 1) : 1;
            return SlotContent.item(idComp[0], count).withComponents(idComp[1]);
        }
        if (expr.startsWith("CreateItem.of(")) {
            List<String> a = splitTop(inParens(expr));
            if (a.isEmpty()) {
                return null;
            }
            SlotContent inner = parseContent(a.get(0));
            if (inner == null) {
                return null;
            }
            return a.size() > 1 ? inner.withChance(parseFloat(a.get(1), 1.0f)) : inner;
        }
        if (expr.startsWith("{")) {
            return parseObjectContent(expr);
        }
        return null;
    }

    /** Content of a JS string literal, e.g. {@code 3x minecraft:stone}, {@code #minecraft:planks}. */
    private static SlotContent parseStringContent(String inner) {
        String s = inner.trim();
        if (s.isEmpty()) {
            return SlotContent.EMPTY;
        }
        if (s.startsWith("#")) {
            return SlotContent.itemTag(s);
        }
        int count = 1;
        Matcher m = COUNT_PREFIX.matcher(s);
        if (m.matches()) {
            count = parseInt(m.group(1), 1);
            s = m.group(2).trim();
        }
        String[] idComp = splitComponents(s);
        return SlotContent.item(idComp[0], count).withComponents(idComp[1]);
    }

    /** Handles KubeJS ingredient objects: {@code { fluidTag: '#..', amount: n }}, {@code { item/tag/fluid: '..' }}. */
    private static SlotContent parseObjectContent(String expr) {
        Map<String, String> entries = objectEntries(expr);
        int amount = parseInt(strip(entries.get("amount")), 1000);
        if (entries.containsKey("fluidTag")) {
            return SlotContent.fluidTag(unquote(entries.get("fluidTag")), amount);
        }
        if (entries.containsKey("fluid")) {
            return SlotContent.fluid(unquote(entries.get("fluid")), amount);
        }
        if (entries.containsKey("tag")) {
            return SlotContent.itemTag(unquote(entries.get("tag")));
        }
        if (entries.containsKey("item")) {
            int count = parseInt(strip(entries.get("count")), 1);
            return SlotContent.item(unquote(entries.get("item")), count);
        }
        return null;
    }

    /** Splits {@code minecraft:sword[minecraft:damage=5]} into {id, "[components]"} (components kept with brackets). */
    private static String[] splitComponents(String idStr) {
        int b = idStr.indexOf('[');
        if (b < 0) {
            return new String[]{idStr, ""};
        }
        return new String[]{idStr.substring(0, b), idStr.substring(b)};
    }

    // ------------------------------------------------------------------ scanning

    private static int indexOfEvent(String s, int from) {
        int idx = from;
        while ((idx = s.indexOf("event", idx)) >= 0) {
            boolean okBefore = idx == 0 || !isIdentPart(s.charAt(idx - 1));
            int after = idx + 5;
            if (okBefore && after < s.length() && s.charAt(after) == '.') {
                return idx;
            }
            idx += 5;
        }
        return -1;
    }

    /**
     * Parses one {@code event.<path>(args)....(args)} chain starting at {@code pos}
     * (the '.' after {@code event}). Writes the index just past the chain into
     * {@code end}. Returns {@code null} if the text at {@code pos} isn't a call chain.
     */
    private static Chain parseChain(String s, int pos, int[] end) {
        int len = s.length();
        int p = ws(s, pos);
        StringBuilder path = new StringBuilder();
        int callOpen = -1;
        while (p < len && s.charAt(p) == '.') {
            p = ws(s, p + 1);
            String id = readIdent(s, p);
            if (id.isEmpty()) {
                return null;
            }
            p += id.length();
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(id);
            p = ws(s, p);
            if (p < len && s.charAt(p) == '(') {
                callOpen = p;
                break;
            }
        }
        if (callOpen < 0) {
            return null;
        }
        int[] close = new int[1];
        String args = readBalanced(s, callOpen, close);
        p = close[0];
        Chain chain = new Chain(path.toString(), splitTop(args));

        // trailing chained modifiers: .id(..).group(..).xp(..)...
        while (true) {
            int q = ws(s, p);
            if (q >= len || s.charAt(q) != '.') {
                p = q;
                break;
            }
            int r = ws(s, q + 1);
            String mid = readIdent(s, r);
            if (mid.isEmpty()) {
                break;
            }
            int r2 = ws(s, r + mid.length());
            if (r2 < len && s.charAt(r2) == '(') {
                int[] mclose = new int[1];
                String margs = readBalanced(s, r2, mclose);
                chain.mods.add(new Mod(mid, splitTop(margs)));
                p = mclose[0];
            } else {
                break;
            }
        }
        end[0] = p;
        return chain;
    }

    /** Content between the parenthesis at {@code open} and its match (exclusive); {@code end[0]} = index after ')'. */
    private static String readBalanced(String s, int open, int[] end) {
        int len = s.length();
        int depth = 0;
        int start = open + 1;
        for (int p = open; p < len; p++) {
            char c = s.charAt(p);
            if (isQuote(c)) {
                p = skipString(s, p);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    end[0] = p + 1;
                    return s.substring(start, p);
                }
            }
        }
        end[0] = len;
        return start <= len ? s.substring(start) : "";
    }

    /** Splits a raw argument string on top-level commas (respecting strings/brackets/braces). */
    private static List<String> splitTop(String raw) {
        List<String> parts = new ArrayList<>();
        if (raw == null) {
            return parts;
        }
        int len = raw.length();
        int depth = 0;
        int start = 0;
        for (int p = 0; p < len; p++) {
            char c = raw.charAt(p);
            if (isQuote(c)) {
                p = skipString(raw, p);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                addTrimmed(parts, raw.substring(start, p));
                start = p + 1;
            }
        }
        addTrimmed(parts, raw.substring(start));
        return parts;
    }

    private static void addTrimmed(List<String> parts, String piece) {
        String t = piece.trim();
        if (!t.isEmpty()) {
            parts.add(t);
        }
    }

    /** Elements of a {@code [ ... ]} array expression (each raw). */
    private static List<String> arrayElements(String expr) {
        String t = expr.trim();
        if (t.startsWith("[") && t.endsWith("]")) {
            return splitTop(t.substring(1, t.length() - 1));
        }
        return splitTop(t);
    }

    /** Key→value (raw) map of a {@code { ... }} object expression, insertion-ordered. */
    private static Map<String, String> objectEntries(String expr) {
        Map<String, String> map = new LinkedHashMap<>();
        String t = expr.trim();
        if (t.startsWith("{") && t.endsWith("}")) {
            t = t.substring(1, t.length() - 1);
        }
        for (String entry : splitTop(t)) {
            int colon = topLevelColon(entry);
            if (colon < 0) {
                continue;
            }
            String key = unquote(entry.substring(0, colon).trim());
            String value = entry.substring(colon + 1).trim();
            map.put(key, value);
        }
        return map;
    }

    private static int topLevelColon(String s) {
        int depth = 0;
        for (int p = 0; p < s.length(); p++) {
            char c = s.charAt(p);
            if (isQuote(c)) {
                p = skipString(s, p);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ':' && depth == 0) {
                return p;
            }
        }
        return -1;
    }

    /** The text inside the first {@code (...)} of {@code expr} (e.g. the args of {@code Fluid.of(...)}). */
    private static String inParens(String expr) {
        int open = expr.indexOf('(');
        if (open < 0) {
            return "";
        }
        int[] end = new int[1];
        return readBalanced(expr, open, end);
    }

    // ------------------------------------------------------------------ lexing helpers

    private static int skipString(String s, int p) {
        char q = s.charAt(p);
        for (p++; p < s.length(); p++) {
            char c = s.charAt(p);
            if (c == '\\') {
                p++;
                continue;
            }
            if (c == q) {
                return p;
            }
        }
        return s.length() - 1;
    }

    private static String readIdent(String s, int p) {
        int start = p;
        while (p < s.length() && isIdentPart(s.charAt(p))) {
            p++;
        }
        return s.substring(start, p);
    }

    private static int ws(String s, int p) {
        while (p < s.length() && Character.isWhitespace(s.charAt(p))) {
            p++;
        }
        return p;
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static boolean isQuote(char c) {
        return c == '\'' || c == '"' || c == '`';
    }

    /** Strips surrounding quotes and unescapes {@code \\} / {@code \'} / {@code \"}. Leaves unquoted text as-is. */
    static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2 && isQuote(s.charAt(0)) && s.charAt(s.length() - 1) == s.charAt(0)) {
            StringBuilder b = new StringBuilder();
            for (int i = 1; i < s.length() - 1; i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length() - 1) {
                    b.append(s.charAt(++i));
                } else {
                    b.append(c);
                }
            }
            return b.toString();
        }
        return s;
    }

    private static String strip(String s) {
        return s == null ? "" : s.trim();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Removes {@code //} line and {@code /* *}{@code /} block comments while preserving string literals. */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int len = src.length();
        for (int p = 0; p < len; p++) {
            char c = src.charAt(p);
            if (isQuote(c)) {
                int endQuote = skipString(src, p);
                out.append(src, p, Math.min(endQuote + 1, len));
                p = endQuote;
                continue;
            }
            if (c == '/' && p + 1 < len && src.charAt(p + 1) == '/') {
                p += 2;
                while (p < len && src.charAt(p) != '\n') {
                    p++;
                }
                out.append('\n');
                continue;
            }
            if (c == '/' && p + 1 < len && src.charAt(p + 1) == '*') {
                p += 2;
                while (p + 1 < len && !(src.charAt(p) == '*' && src.charAt(p + 1) == '/')) {
                    p++;
                }
                p++; // land on the '/', loop's p++ moves past it
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ model

    private record Mod(String name, List<String> args) {
    }

    private static final class Chain {
        final String method;
        final List<String> args;
        final List<Mod> mods = new ArrayList<>();

        Chain(String method, List<String> args) {
            this.method = method;
            this.args = args;
        }
    }
}
