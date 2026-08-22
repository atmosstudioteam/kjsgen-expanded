package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.*;
import java.util.*;

/** Spectrum 1.12.x recipe generator. Complex optional fields can be supplied through *Json params. */
public final class SpectrumRecipeCodegen implements RecipeCodegen {
    @Override public String generate(RecipeInstance r, RecipeTypeDefinition t) {
        String s = r.param(t, "__spectrumType").trim();
        if (s.equals("raw")) s = r.param(t, "serializer").trim().replaceFirst("^spectrum:", "");
        Obj o = new Obj().str("type", "spectrum:" + s);
        if (!r.group().isEmpty()) o.str("group", r.group());
        switch (s) {
            case "anvil_crushing" -> o.raw("ingredient", arr(r.listSlots("ingredient"), false)).raw("result", result(r.slot("output"), ""))
                    .num("crushedItemsPerPointOfDamage", r.param(t,"crushedItemsPerPointOfDamage"))
                    .num("experience",r.param(t,"experience")).optStr("particleEffectIdentifier",r.param(t,"particleEffectIdentifier"))
                    .optInt("particleCount",r.paramInt(t,"particleCount",0)).optStr("soundEventIdentifier",r.param(t,"soundEventIdentifier"));
            case "cinderhearth" -> o.raw("ingredient", ingredient(r.slot("input"),false)).raw("results", arrResult(r.listSlots("results")))
                    .num("experience",r.param(t,"experience")).integer("time",r.paramInt(t,"time",20));
            case "crystallarieum_growing" -> { o.raw("ingredient",ingredient(r.slot("input"),false)); fluid(o,r,"fluid","fluid");
                o.str("ink_color",r.param(t,"inkColor")).integer("ink_cost_tier",r.paramInt(t,"inkCostTier",1))
                 .integer("seconds_per_growth_stage",r.paramInt(t,"secondsPerGrowthStage",60))
                 .raw("additives",raw(r.param(t,"additivesJson"),"[]")).raw("growth_stage_states",raw(r.param(t,"growthStageStatesJson"),"[]"))
                 .optRaw("additional_recipe_viewer_results",r.param(t,"viewerResultsJson")); }
            case "dragonrot_converting", "liquid_crystal_converting", "midnight_solution_converting", "primordial_fire_burning" ->
                    o.raw("ingredient",arr(r.listSlots("ingredient"),false)).raw("result",result(r.slot("output"),""));
            case "enchanter" -> o.integer("time",r.paramInt(t,"time",200)).integer("required_experience",r.paramInt(t,"requiredExperience",0))
                    .raw("ingredients",arr(r.listSlots("ingredients"),false)).raw("result",result(r.slot("output"),""));
            case "enchantment_upgrade" -> o.str("enchantment",r.param(t,"enchantment")).raw("levels",raw(r.param(t,"levelsJson"),"[]"))
                    .optStr("bulk_item",r.param(t,"bulkItem")).optInt("level_cap",r.paramInt(t,"levelCap",0))
                    .optRaw("item_scaling",r.param(t,"itemScalingJson")).optRaw("xp_scaling",r.param(t,"xpScalingJson"));
            case "fusion_shrine" -> { o.integer("time",r.paramInt(t,"time",200)).num("experience",r.param(t,"experience"))
                    .bool("copy_components",r.paramBool(t,"copyComponents",false)).raw("ingredients",arr(r.listSlots("ingredients"),true))
                    .raw("result",result(r.slot("output"),"")); fluid(o,r,"fluid","fluid");
                    o.optStr("description",r.param(t,"description")).optRaw("world_conditions",r.param(t,"worldConditionsJson"))
                    .optRaw("effects",r.param(t,"effectsJson")).optRaw("during_crafting_effects",r.param(t,"duringCraftingEffectsJson")); }
            case "gated_crafting_shaped", "pedestal" -> { Shape sh=shape(r); if(s.equals("pedestal")) pedestal(o,r,t);
                    o.raw("pattern",sh.pattern).raw("key",sh.key).raw("result",result(r.slot("output"),"")); }
            case "gated_crafting_shapeless" -> o.raw("ingredients",arr(r.listSlots("ingredients"),false)).raw("result",result(r.slot("output"),""));
            case "ink_converting" -> o.raw("ingredient",arr(r.listSlots("ingredient"),false)).str("ink_color",r.param(t,"inkColor")).integer("amount",r.paramInt(t,"amount",1));
            case "pedestal_shapeless" -> { pedestal(o,r,t); o.raw("ingredients",arr(r.listSlots("ingredients"),false)).raw("result",result(r.slot("output"),"")); }
            case "potion_workshop_brewing" -> { slotStr(o,r,"ingredient1"); slotStr(o,r,"ingredient2"); slotStr(o,r,"ingredient3");
                    o.str("effect",r.param(t,"effect")).integer("base_duration_ticks",r.paramInt(t,"baseDurationTicks",200))
                     .num("potency_modifier",r.param(t,"potencyModifier")).bool("applicable_to_potions",r.paramBool(t,"applicableToPotions",true))
                     .bool("applicable_to_tipped_arrows",r.paramBool(t,"applicableToTippedArrows",true)).bool("applicable_to_potion_fillables",r.paramBool(t,"applicableToPotionFillables",true))
                     .str("ink_color",r.param(t,"inkColor")).integer("ink_cost",r.paramInt(t,"inkCost",1)).optInt("base_yield",r.paramInt(t,"baseYield",0)).optInt("potency_hard_cap",r.paramInt(t,"potencyHardCap",0)); }
            case "potion_workshop_crafting" -> { slotStr(o,r,"baseIngredient","base_ingredient"); slotStr(o,r,"ingredient1"); slotStr(o,r,"ingredient2"); slotStr(o,r,"ingredient3");
                    o.bool("use_up_base_ingredient",r.paramBool(t,"useUpBaseIngredient",true)).integer("color",r.paramInt(t,"color",0xffffff))
                     .raw("result",result(r.slot("output"),"")).optInt("required_experience",r.paramInt(t,"requiredExperience",0)); }
            case "potion_workshop_reacting" -> { slotStr(o,r,"input","item"); o.raw("modifiers",raw(r.param(t,"modifiersJson"),"{}")); }
            case "spirit_instiller" -> o.integer("time",r.paramInt(t,"time",200)).num("experience",r.param(t,"experience"))
                    .raw("ingredient1",ingredient(r.slot("ingredient1"),true)).raw("ingredient2",ingredient(r.slot("ingredient2"),true))
                    .raw("center_ingredient",ingredient(r.slot("centerIngredient"),true)).raw("result",result(r.slot("output"),""))
                    .when(r.paramBool(t,"copyComponents",false),"copy_components","true");
            case "titration_barrel" -> { o.raw("ingredients",arr(r.listSlots("ingredients"),true)).integer("min_fermentation_time_hours",r.paramInt(t,"minFermentationTimeHours",24))
                    .raw("fermentation",raw(r.param(t,"fermentationJson"),"{}")).raw("result",result(r.slot("output"),r.param(t,"resultComponentsJson")));
                    slotStr(o,r,"tappingItem","tapping_item"); fluid(o,r,"fluid","fluid"); }
        }
        o.optStr("required_advancement",r.param(t,"requiredAdvancement")).optStr("reveal_secret_advancement",r.param(t,"revealSecretAdvancement"));
        String extra=r.param(t,"extraJson").trim(); if(!extra.isEmpty()) o.extra(extra);
        StringBuilder js=new StringBuilder("event.custom(").append(o).append(')');
        if(!r.recipeId().isEmpty()) js.append(".id(").append(JsUtil.quote(r.recipeId())).append(')');
        return js.toString();
    }
    private static void pedestal(Obj o,RecipeInstance r,RecipeTypeDefinition t){o.integer("time",r.paramInt(t,"time",200)).str("tier",r.param(t,"tier"))
            .raw("colors",raw(r.param(t,"colorsJson"),"{}")).num("experience",r.param(t,"experience"));
        if(r.paramBool(t,"disableYieldUpgrades",false))o.bool("disable_yield_upgrades",true); if(r.paramBool(t,"skipRecipeRemainders",false))o.bool("skip_recipe_remainders",true);}
    private static void fluid(Obj o,RecipeInstance r,String slot,String key){SlotContent c=r.slot(slot);if(!c.isEmpty())o.raw(key,"{ "+(c.kind()==ContentKind.FLUID_TAG?"tag":"fluid")+": "+JsUtil.quote(c.id())+(c.amount()!=1000?", amount: "+c.amount():"")+" }");}
    private static void slotStr(Obj o,RecipeInstance r,String k){slotStr(o,r,k,k);} private static void slotStr(Obj o,RecipeInstance r,String s,String k){SlotContent c=r.slot(s);if(!c.isEmpty())o.str(k,(c.kind()==ContentKind.ITEM_TAG?"#":"")+c.id());}
    private static String ingredient(SlotContent c,boolean count){String k=c.kind()==ContentKind.ITEM_TAG?"tag":"item";return "{ "+k+": "+JsUtil.quote(c.id())+(count&&c.count()>1?", count: "+c.count():"")+" }";}
    private static String arr(List<SlotContent> a,boolean count){return "["+a.stream().filter(c->!c.isEmpty()).map(c->count?ingredient(c,true):ingredient(c,false)).reduce((x,y)->x+", "+y).orElse("")+"]";}
    private static String arrResult(List<SlotContent>a){return "["+a.stream().filter(c->!c.isEmpty()).map(c->result(c,"")).reduce((x,y)->x+", "+y).orElse("")+"]";}
    private static String result(SlotContent c,String components){return "{ id: "+JsUtil.quote(c.id())+(c.count()>1?", count: "+c.count():"")+(components!=null&&!components.isBlank()?", components: "+components.trim():"")+" }";}
    private static String raw(String s,String d){return s==null||s.isBlank()?d:s.trim();}
    private static Shape shape(RecipeInstance r){SlotContent[][]g=new SlotContent[3][3];int a=3,b=-1,c=3,d=-1;for(int y=0;y<3;y++)for(int x=0;x<3;x++){SlotContent v=r.slot("in"+(y*3+x));g[y][x]=v;if(!v.isEmpty()){a=Math.min(a,y);b=Math.max(b,y);c=Math.min(c,x);d=Math.max(d,x);}}if(b<0)a=b=c=d=0;Map<SlotContent,Character>m=new LinkedHashMap<>();List<String>p=new ArrayList<>();for(int y=a;y<=b;y++){StringBuilder q=new StringBuilder();for(int x=c;x<=d;x++){SlotContent v=g[y][x];q.append(v.isEmpty()?' ':m.computeIfAbsent(v,z->(char)('A'+m.size())));}p.add(JsUtil.quote(q.toString()));}String k="{";for(var e:m.entrySet())k+=(k.length()>1?", ":"")+e.getValue()+": "+ingredient(e.getKey(),false);return new Shape("["+String.join(", ",p)+"]",k+"}");}
    @Override public Optional<String> removeTypeId(RecipeInstance r,RecipeTypeDefinition t){String s=r.param(t,"__spectrumType").trim();if(s.equals("raw"))s=r.param(t,"serializer").trim();return s.isEmpty()?Optional.empty():Optional.of(s.contains(":")?s:"spectrum:"+s);}
    private record Shape(String pattern,String key){}
    private static final class Obj{final LinkedHashMap<String,String>m=new LinkedHashMap<>();String x="";Obj str(String k,String v){m.put(k,JsUtil.quote(v));return this;}Obj optStr(String k,String v){if(v!=null&&!v.isBlank())str(k,v.trim());return this;}Obj raw(String k,String v){m.put(k,v);return this;}Obj optRaw(String k,String v){if(v!=null&&!v.isBlank())raw(k,v.trim());return this;}Obj integer(String k,int v){m.put(k,""+v);return this;}Obj optInt(String k,int v){if(v>0)integer(k,v);return this;}Obj num(String k,String v){m.put(k,v==null||v.isBlank()?"0":v.trim());return this;}Obj bool(String k,boolean v){m.put(k,""+v);return this;}Obj when(boolean q,String k,String v){if(q)m.put(k,v);return this;}Obj extra(String v){x=v.trim();return this;}public String toString(){StringBuilder b=new StringBuilder("{\n");int i=0;for(var e:m.entrySet()){if(i++>0)b.append(",\n");b.append("  ").append(e.getKey()).append(": ").append(e.getValue());}if(!x.isBlank()){String z=x;if(z.startsWith("{")&&z.endsWith("}"))z=z.substring(1,z.length()-1).trim();if(!z.isEmpty()){if(i>0)b.append(",\n");b.append("  ").append(z.replace("\n","\n  "));}}return b.append("\n}").toString();}}
}
