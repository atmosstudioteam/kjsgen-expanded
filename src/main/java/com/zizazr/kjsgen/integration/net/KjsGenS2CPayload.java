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
 * Server to client envelope: an {@code op} string plus a JSON {@code json} body.
 * Carries snapshots and broadcast deltas ({@code snapshot}, {@code list},
 * {@code upsertRecipe}, {@code removeRecipe}, {@code meta}, {@code exportResult},
 * {@code denied}). Client-bound payloads may be up to 1 MiB, so the full-project
 * {@code snapshot} travels this direction only.
 */
public record KjsGenS2CPayload(String op, String json) implements CustomPacketPayload {
    public static final Type<KjsGenS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(KjsGen.MODID, "s2c"));

    public static final StreamCodec<ByteBuf, KjsGenS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KjsGenS2CPayload::op,
            ByteBufCodecs.stringUtf8(1_048_576), KjsGenS2CPayload::json,
            KjsGenS2CPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
//?}
