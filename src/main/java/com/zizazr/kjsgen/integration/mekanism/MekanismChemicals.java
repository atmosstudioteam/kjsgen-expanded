package com.zizazr.kjsgen.integration.mekanism;

import dev.architectury.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional bridge to the Mekanism chemical registry ({@code mekanism:chemical},
 * the single registry that holds gases/infuse types/pigments/slurries since 10.7).
 * <p>
 * Compiled against the Mekanism API jar ({@code compileOnly}); every Mekanism
 * class is referenced only inside {@link Impl}, which is class-loaded lazily
 * after the {@link #available()} check, so the mod runs fine without Mekanism.
 */
public final class MekanismChemicals {

    /**
     * One chemical (or a tag's sample chemical) for pickers and slot rendering.
     *
     * @param id   registry id ("mekanism:oxygen") or tag id for tag entries
     * @param name display name
     * @param icon block-atlas sprite location, null when unresolvable (empty tag)
     * @param tint RGB tint the icon must be multiplied with
     */
    public record ChemicalInfo(String id, String name, ResourceLocation icon, int tint) {
    }

    private static Boolean available;

    private MekanismChemicals() {
    }

    public static boolean available() {
        if (available == null) {
            boolean ok = Platform.isModLoaded("mekanism");
            if (ok) {
                try {
                    Class.forName("mekanism.api.MekanismAPI");
                } catch (Throwable t) {
                    ok = false;
                }
            }
            available = ok;
        }
        return available;
    }

    // Mekanism has no Fabric 1.21.1 build, so its API jar is not on the Fabric compile classpath.
    // Every method that touches {@link Impl} (the only Mekanism-typed code) is compiled out on Fabric
    // and returns empty there; on Fabric {@link #available()} is false anyway, so behaviour is identical.

    /** Chemicals matching {@code query} (id or lower-cased display name), up to {@code cap}. */
    public static List<ChemicalInfo> search(String query, int cap) {
        //? if neoforge {
        return available() ? Impl.search(query, cap) : List.of();
        //?}
        //? if fabric {
        /*return List.of();*/
        //?}
    }

    /** Chemical tags matching {@code query}; icon/tint come from the first tag member. */
    public static List<ChemicalInfo> searchTags(String query, int cap) {
        //? if neoforge {
        return available() ? Impl.searchTags(query, cap) : List.of();
        //?}
        //? if fabric {
        /*return List.of();*/
        //?}
    }

    public static Optional<ChemicalInfo> byId(String id) {
        //? if neoforge {
        return available() ? Impl.byId(id) : Optional.empty();
        //?}
        //? if fabric {
        /*return Optional.empty();*/
        //?}
    }

    /** First chemical of a tag, for rendering tag contents. */
    public static Optional<ChemicalInfo> tagSample(String tagId) {
        //? if neoforge {
        return available() ? Impl.tagSample(tagId) : Optional.empty();
        //?}
        //? if fabric {
        /*return Optional.empty();*/
        //?}
    }

    /** The only class that touches Mekanism types; never loaded when Mekanism is absent. */
    //? if neoforge {
    private static final class Impl {
        static List<ChemicalInfo> search(String query, int cap) {
            List<ChemicalInfo> out = new ArrayList<>();
            for (mekanism.api.chemical.Chemical chemical : mekanism.api.MekanismAPI.CHEMICAL_REGISTRY) {
                if (out.size() >= cap) {
                    break;
                }
                ResourceLocation key = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
                if (key == null || key.equals(mekanism.api.MekanismAPI.EMPTY_CHEMICAL_NAME)) {
                    continue;
                }
                String name = chemical.getTextComponent().getString();
                if (query.isEmpty() || key.toString().contains(query)
                        || name.toLowerCase(Locale.ROOT).contains(query)) {
                    out.add(info(key, chemical));
                }
            }
            return out;
        }

        static List<ChemicalInfo> searchTags(String query, int cap) {
            List<ChemicalInfo> out = new ArrayList<>();
            for (TagKey<mekanism.api.chemical.Chemical> tagKey
                    : mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getTagNames().toList()) {
                if (out.size() >= cap) {
                    break;
                }
                String tagId = tagKey.location().toString();
                if (query.isEmpty() || tagId.contains(query)) {
                    ChemicalInfo sample = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getTag(tagKey)
                            .flatMap(set -> set.stream().findFirst())
                            .map(holder -> info(tagKey.location(), holder.value()))
                            .orElse(null);
                    out.add(new ChemicalInfo(tagId, "#" + tagId,
                            sample == null ? null : sample.icon(),
                            sample == null ? 0xFFFFFF : sample.tint()));
                }
            }
            return out;
        }

        static Optional<ChemicalInfo> byId(String id) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || rl.equals(mekanism.api.MekanismAPI.EMPTY_CHEMICAL_NAME)
                    || !mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(rl)) {
                return Optional.empty();
            }
            mekanism.api.chemical.Chemical chemical = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(rl);
            if (chemical == null) {
                return Optional.empty();
            }
            return Optional.of(info(rl, chemical));
        }

        static Optional<ChemicalInfo> tagSample(String tagId) {
            ResourceLocation rl = ResourceLocation.tryParse(tagId);
            if (rl == null) {
                return Optional.empty();
            }
            TagKey<mekanism.api.chemical.Chemical> tagKey =
                    TagKey.create(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY_NAME, rl);
            return mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getTag(tagKey)
                    .flatMap(set -> set.stream().findFirst())
                    .map(holder -> info(
                            mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(holder.value()),
                            holder.value()));
        }

        private static ChemicalInfo info(ResourceLocation id, mekanism.api.chemical.Chemical chemical) {
            return new ChemicalInfo(id.toString(), chemical.getTextComponent().getString(),
                    chemical.getIcon(), chemical.getTint());
        }
    }
    //?}
}
