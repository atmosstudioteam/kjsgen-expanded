package com.zizazr.kjsgen.templates;

import com.zizazr.kjsgen.core.*;
import java.util.*;

/** Built-in editor layouts for Modern Industrialization and Spectrum. */
public final class IntegrationRecipeTypes {
    private static final String MI="modern_industrialization", SP="spectrum";
    private IntegrationRecipeTypes(){}
    public static void register(){mi();spectrum();}

    private static void mi(){
        String[] machines={"assembler","blast_furnace","centrifuge","chemical_reactor","coke_oven","compressor","cutting_machine","distillation_tower","distillery","electrolyzer","fusion_reactor","heat_exchanger","implosion_compressor","macerator","mixer","oil_drilling_rig","packer","polarizer","pressurizer","quarry","unpacker","vacuum_freezer","wiremill"};
        for(String m:machines){List<ParameterDefinition> p=new ArrayList<>(miParams());p.add(ps("__machine",MI+":"+m));reg("mi_"+m,MI,MI+":steel_machine_casing",126,72,miSlots(),p,"kjsgen:modern_industrialization");}
        List<ParameterDefinition> custom=new ArrayList<>();custom.add(ps("machineType",MI+":macerator"));custom.addAll(miParams());custom.add(ps("__machine",""));
        reg("mi_custom_machine",MI,MI+":steel_machine_casing",126,72,miSlots(),custom,"kjsgen:modern_industrialization");
        reg("mi_forge_hammer",MI,MI+":forge_hammer",82,36,List.of(i("input",SlotRole.INPUT,0,9,true,true,false),i("output",SlotRole.OUTPUT,60,9,true,false,true)),List.of(pi("damage",0),pi("hammerCount",1)),"kjsgen:modern_industrialization");
    }
    private static List<SlotDefinition> miSlots(){return List.of(
            list("itemIn",SlotRole.INPUT,0,0,false,true,false,true,true),list("fluidIn",SlotRole.INPUT,0,36,false,true,true,false,true),
            list("itemOut",SlotRole.OUTPUT,90,0,false,false,false,true,true),list("fluidOut",SlotRole.OUTPUT,90,36,false,false,true,false,true));}
    private static List<ParameterDefinition> miParams(){return List.of(pi("eu",8),pi("duration",200),ps("dimension",""),ps("biome",""),ps("biomeTag",""),ps("adjacentBlock",""),pe("adjacentPosition","none","none","below","behind"),ps("registeredConditionJson",""));}

    private static void spectrum(){
        sp("anvil_crushing","spectrum:bedrock_anvil",List.of(list("ingredient",SlotRole.INPUT,0,9,true,true,false,false,false),i("output",SlotRole.OUTPUT,72,9,true,false,true)),List.of(pf("crushedItemsPerPointOfDamage",1),pf("experience",0),ps("particleEffectIdentifier","enchanted_hit"),pi("particleCount",10),ps("soundEventIdentifier","block.anvil.land")));
        sp("cinderhearth","spectrum:cinderhearth",List.of(i("input",SlotRole.INPUT,0,9,true,true,false),list("results",SlotRole.OUTPUT,72,9,true,false,false,true,false)),List.of(pf("experience",0),pi("time",20)));
        sp("crystallarieum_growing","spectrum:crystallarieum",List.of(i("input",SlotRole.INPUT,0,0,true,true,false),f("fluid",SlotRole.INPUT,0,36,false,true)),List.of(ps("inkColor","spectrum:white"),pi("inkCostTier",1),pi("secondsPerGrowthStage",60),ps("additivesJson","[]"),ps("growthStageStatesJson","[]"),ps("viewerResultsJson","")));
        conv("dragonrot_converting","spectrum:pedestal_all_basic");
        sp("enchanter","spectrum:enchanter",List.of(list("ingredients",SlotRole.INPUT,0,0,true,true,false,true,false),i("output",SlotRole.OUTPUT,90,9,true,false,true)),List.of(pi("time",200),pi("requiredExperience",0)));
        sp("enchantment_upgrade","spectrum:enchanter",List.of(),List.of(ps("enchantment","minecraft:sharpness"),ps("bulkItem",""),ps("levelsJson","[]"),pi("levelCap",0),ps("itemScalingJson",""),ps("xpScalingJson","")));
        sp("fusion_shrine","spectrum:fusion_shrine_basalt",List.of(list("ingredients",SlotRole.INPUT,0,0,true,true,false,true,false),f("fluid",SlotRole.INPUT,0,36,false,true),i("output",SlotRole.OUTPUT,108,9,true,false,true)),List.of(pi("time",200),pf("experience",0),pb("copyComponents",false),ps("description",""),ps("worldConditionsJson",""),ps("effectsJson",""),ps("duringCraftingEffectsJson","")));
        shaped("gated_crafting_shaped","minecraft:crafting_table",List.of());
        sp("gated_crafting_shapeless","minecraft:crafting_table",List.of(list("ingredients",SlotRole.INPUT,0,0,true,true,false,true,false),i("output",SlotRole.OUTPUT,94,18,true,false,true)),List.of());
        sp("ink_converting","spectrum:color_picker",List.of(list("ingredient",SlotRole.INPUT,0,9,true,true,false,false,false)),List.of(ps("inkColor","spectrum:white"),pi("amount",1)));
        conv("liquid_crystal_converting","spectrum:liquid_crystal_bucket");conv("midnight_solution_converting","spectrum:midnight_solution_bucket");
        List<ParameterDefinition> ped=List.of(pi("time",200),pe("tier","basic","basic","simple","advanced","complex"),ps("colorsJson","{}"),pf("experience",0),pb("disableYieldUpgrades",false),pb("skipRecipeRemainders",false));
        shaped("pedestal","spectrum:pedestal_all_basic",ped);
        sp("pedestal_shapeless","spectrum:pedestal_all_basic",List.of(list("ingredients",SlotRole.INPUT,0,0,true,true,false,true,false),i("output",SlotRole.OUTPUT,94,18,true,false,true)),ped);
        sp("potion_workshop_brewing","spectrum:potion_workshop",List.of(i("ingredient1",SlotRole.INPUT,0,0,false,true,false),i("ingredient2",SlotRole.INPUT,18,0,false,true,false),i("ingredient3",SlotRole.INPUT,36,0,false,true,false)),List.of(ps("effect","minecraft:speed"),pi("baseDurationTicks",200),pf("potencyModifier",1),pi("potencyHardCap",0),pi("baseYield",0),pb("applicableToPotions",true),pb("applicableToTippedArrows",true),pb("applicableToPotionFillables",true),ps("inkColor","spectrum:white"),pi("inkCost",1)));
        sp("potion_workshop_crafting","spectrum:potion_workshop",List.of(i("baseIngredient",SlotRole.INPUT,0,18,true,true,false),i("ingredient1",SlotRole.INPUT,18,0,false,true,false),i("ingredient2",SlotRole.INPUT,36,0,false,true,false),i("ingredient3",SlotRole.INPUT,54,0,false,true,false),i("output",SlotRole.OUTPUT,108,18,true,false,true)),List.of(pb("useUpBaseIngredient",true),pi("color",0xffffff),pi("requiredExperience",0)));
        sp("potion_workshop_reacting","spectrum:potion_workshop",List.of(i("input",SlotRole.INPUT,0,9,true,false,false)),List.of(ps("modifiersJson","{}")));
        conv("primordial_fire_burning","spectrum:doombloom_seed");
        sp("spirit_instiller","spectrum:spirit_instiller",List.of(i("ingredient1",SlotRole.INPUT,0,0,true,true,true),i("centerIngredient",SlotRole.INPUT,18,18,true,true,true),i("ingredient2",SlotRole.INPUT,36,0,true,true,true),i("output",SlotRole.OUTPUT,90,18,true,false,true)),List.of(pi("time",200),pf("experience",0),pb("copyComponents",false)));
        sp("titration_barrel","spectrum:titration_barrel",List.of(list("ingredients",SlotRole.INPUT,0,0,true,true,false,true,false),f("fluid",SlotRole.INPUT,0,36,false,true),i("tappingItem",SlotRole.CATALYST,54,36,false,false,false),i("output",SlotRole.OUTPUT,108,18,true,false,true)),List.of(pi("minFermentationTimeHours",24),ps("fermentationJson","{}"),ps("resultComponentsJson","")));
        List<ParameterDefinition> raw=new ArrayList<>(common("raw"));raw.add(0,ps("serializer","spectrum:clear_ink"));reg("spectrum_raw",SP,"spectrum:pedestal_all_basic",90,36,List.of(),raw,"kjsgen:spectrum");
    }
    private static void conv(String n,String icon){sp(n,icon,List.of(list("ingredient",SlotRole.INPUT,0,9,true,true,false,false,false),i("output",SlotRole.OUTPUT,72,9,true,false,true)),List.of());}
    private static void shaped(String n,String icon,List<ParameterDefinition> params){List<SlotDefinition>s=new ArrayList<>();for(int y=0;y<3;y++)for(int x=0;x<3;x++)s.add(i("in"+(y*3+x),SlotRole.INPUT,x*18,y*18,false,true,false));s.add(i("output",SlotRole.OUTPUT,94,18,true,false,true));sp(n,icon,s,params);}
    private static void sp(String n,String icon,List<SlotDefinition>s,List<ParameterDefinition>p){List<ParameterDefinition>a=new ArrayList<>(p);a.addAll(common(n));reg("spectrum_"+n,SP,icon,126,54,s,a,"kjsgen:spectrum");}
    private static List<ParameterDefinition> common(String n){return List.of(ps("requiredAdvancement",""),ps("revealSecretAdvancement",""),ps("extraJson",""),ps("__spectrumType",n));}

    private static void reg(String id,String mod,String icon,int w,int h,List<SlotDefinition>s,List<ParameterDefinition>p,String codegen){RecipeTypeRegistry.register(new RecipeTypeDefinition("kjsgen:"+id,mod,icon,w,h,List.copyOf(s),List.of(LayoutDecoration.arrow(61,19)),List.copyOf(p),codegen,mod));}
    private static SlotDefinition i(String k,SlotRole r,int x,int y,boolean req,boolean tag,boolean count){return new SlotDefinition(k,r,x,y,req,true,tag,false,false,count,false,false);}
    private static SlotDefinition f(String k,SlotRole r,int x,int y,boolean req,boolean tag){return new SlotDefinition(k,r,x,y,req,false,tag,true,false,false,false,false);}
    private static SlotDefinition list(String k,SlotRole r,int x,int y,boolean req,boolean tag,boolean fluid,boolean count,boolean chance){return new SlotDefinition(k,r,x,y,req,!fluid,tag,fluid,false,count,chance,true);}
    private static ParameterDefinition pi(String k,int v){return ParameterDefinition.ofInt(k,v);}private static ParameterDefinition pf(String k,float v){return ParameterDefinition.ofFloat(k,v);}private static ParameterDefinition ps(String k,String v){return ParameterDefinition.ofString(k,v);}private static ParameterDefinition pb(String k,boolean v){return ParameterDefinition.ofBool(k,v);}private static ParameterDefinition pe(String k,String d,String...v){return ParameterDefinition.ofEnum(k,d,List.of(v));}
}
