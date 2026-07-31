package com.zizazr.kjsgen.integration.net;

// The payload/StreamCodec networking API is 1.20.5+; on 1.20.1 KjsGenNet uses the FriendlyByteBuf-
// based Architectury API directly and this class is compiled out to a bare package declaration.
//? if >=1.21 {
import com.zizazr.kjsgen.KjsGen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server envelope: an {@code op} string plus a JSON {@code json} body.
 * A single payload type carries every editor operation ({@code open}, {@code create},
 * {@code upsertRecipe}, {@code removeRecipe}, {@code meta}, {@code export}, ...) so the
 * networking surface stays tiny. Server-bound payloads must stay below 32 KiB, so the
 * client only ever sends per-recipe / per-op deltas here — never a whole project snapshot.
 */
public record KjsGenC2SPayload(String op, String json) implements CustomPacketPayload {
    public static final Type<KjsGenC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(KjsGen.MODID, "c2s"));

    public static final StreamCodec<ByteBuf, KjsGenC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KjsGenC2SPayload::op,
            ByteBufCodecs.STRING_UTF8, KjsGenC2SPayload::json,
            KjsGenC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
//?}
