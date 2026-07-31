package com.zizazr.kjsgen.integration.net;

import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerPlayer;
//? if <1.21 {
/*import com.zizazr.kjsgen.KjsGen;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Registers the two kjsgen payload channels and dispatches incoming payloads by op string,
 * cross-loader via Architectury's {@link NetworkManager}.
 *
 * <p>Architectury has no explicit "optional channel" concept: if the other side has no receiver
 * registered for a payload type, the packet is silently dropped and the connection is not refused.
 * That is exactly the behaviour kjsgen relies on to fall back to local-file mode against a
 * vanilla/other-modded server — so no extra opt-in flag is needed here.
 *
 * <p>Receivers run on the network thread, so every game-state mutation is wrapped in
 * {@link NetworkManager.PacketContext#queue}. The client-bound handler is an explicit lambda (not
 * a method reference) referencing {@code ClientEditSession} only inside its body, so that
 * client-only class is never loaded on a dedicated server during registration.
 */
public final class KjsGenNet {
    // ---- op names (shared by both directions where it makes sense) ----
    public static final String OP_OPEN = "open";
    public static final String OP_CLOSE = "close";
    public static final String OP_LIST = "list";
    public static final String OP_CREATE = "create";
    public static final String OP_DELETE = "delete";
    public static final String OP_UPSERT_RECIPE = "upsertRecipe";
    public static final String OP_REMOVE_RECIPE = "removeRecipe";
    public static final String OP_META = "meta";
    public static final String OP_EXPORT = "export";
    /** Live mouse position of one editor (panel-relative). Both directions. */
    public static final String OP_CURSOR = "cursor";
    /** Which kjsgen screen an operator is currently on (for the presence tooltip). Both directions. */
    public static final String OP_SCREEN = "screen";
    // ---- server -> client only ----
    public static final String OP_SNAPSHOT = "snapshot";
    public static final String OP_EXPORT_RESULT = "exportResult";
    public static final String OP_DENIED = "denied";
    /** The set of operators currently viewing a project (+ their assigned colour). */
    public static final String OP_PRESENCE = "presence";

    /** Server-bound JSON cap: matches C2S per-op deltas (default FriendlyByteBuf/STRING_UTF8 limit). */
    private static final int C2S_MAX = 32767;
    /** Client-bound JSON cap: whole-project snapshots, matching the 1.21 codec's stringUtf8(1 MiB). */
    private static final int S2C_MAX = 1_048_576;

    //? if <1.21 {
    /*private static final ResourceLocation C2S_ID = KjsGen.rl("c2s");
    private static final ResourceLocation S2C_ID = KjsGen.rl("s2c");
    *///?}

    private KjsGenNet() {
    }

    /** Registers both receivers on both sides. Called once from common setup. */
    public static void register() {
        //? if >=1.21 {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, KjsGenC2SPayload.TYPE, KjsGenC2SPayload.CODEC,
                (payload, ctx) -> {
                    if (ctx.getPlayer() instanceof ServerPlayer sender) {
                        ctx.queue(() -> ServerProjectStore.handle(sender, payload.op(), payload.json()));
                    }
                });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, KjsGenS2CPayload.TYPE, KjsGenS2CPayload.CODEC,
                (payload, ctx) -> ctx.queue(
                        () -> ClientEditSession.handleServer(payload.op(), payload.json())));
        //?}
        //? if <1.21 {
        /*NetworkManager.registerReceiver(NetworkManager.Side.C2S, C2S_ID, (buf, ctx) -> {
            String op = buf.readUtf();
            String json = buf.readUtf(C2S_MAX);
            if (ctx.getPlayer() instanceof ServerPlayer sender) {
                ctx.queue(() -> ServerProjectStore.handle(sender, op, json));
            }
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, S2C_ID, (buf, ctx) -> {
            String op = buf.readUtf();
            String json = buf.readUtf(S2C_MAX);
            ctx.queue(() -> ClientEditSession.handleServer(op, json));
        });
        *///?}
    }

    // ---- send helpers ----

    /** Client -> server. Call from client code only. */
    public static void toServer(String op, String json) {
        //? if >=1.21 {
        NetworkManager.sendToServer(new KjsGenC2SPayload(op, json));
        //?}
        //? if <1.21 {
        /*FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(op);
        buf.writeUtf(json, C2S_MAX);
        NetworkManager.sendToServer(C2S_ID, buf);
        *///?}
    }

    /** Server -> one client. */
    public static void toPlayer(ServerPlayer player, String op, String json) {
        //? if >=1.21 {
        NetworkManager.sendToPlayer(player, new KjsGenS2CPayload(op, json));
        //?}
        //? if <1.21 {
        /*FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(op);
        buf.writeUtf(json, S2C_MAX);
        NetworkManager.sendToPlayer(player, S2C_ID, buf);
        *///?}
    }
}
