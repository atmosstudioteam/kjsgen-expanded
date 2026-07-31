package com.zizazr.kjsgen;

import com.mojang.blaze3d.platform.InputConstants;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.integration.net.ClientEditSession;
import com.zizazr.kjsgen.templates.JsonLayoutLoader;
import com.zizazr.kjsgen.templates.UserLayoutStore;
import com.zizazr.kjsgen.ui.vanilla.VanillaEditorScreen;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry: keybind + editor screen opening. The editor is a dev tool, so
 * it is gated to singleplayer / operators.
 *
 * <p>Loader-agnostic: on NeoForge this is the client {@code @Mod} class, on Fabric the
 * {@code ClientModInitializer}. Both funnel into {@link #initClient()}, which registers the
 * keybind, client tick, resource-reload listener and connect/disconnect hooks via Architectury.
 */
//? if neoforge {
@net.neoforged.fml.common.Mod(value = KjsGen.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
//?}
public class KjsGenClient
        //? if fabric
        /*implements net.fabricmc.api.ClientModInitializer*/
{
    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.kjsgen.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.kjsgen"
    );

    //? if neoforge {
    public KjsGenClient(net.neoforged.fml.ModContainer container) {
        initClient();
    }
    //?}

    //? if fabric {
    /*@Override
    public void onInitializeClient() {
        initClient();
    }*/
    //?}

    /** Common client bootstrap, runs once on both loaders. */
    private static void initClient() {
        KeyMappingRegistry.register(OPEN_EDITOR);
        ClientTickEvent.CLIENT_POST.register(KjsGenClient::onClientTick);
        // Bundled + user JSON recipe layouts (JEI not required for the latter).
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, new JsonLayoutLoader());
        UserLayoutStore.loadAll();
        // A fresh connection starts in local mode until the server proves it has kjsgen.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> ClientEditSession.reset());
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ClientEditSession.reset());
        //? if neoforge {
        // Draw the other operators' cursors on top of everything (after the screen + its tooltips).
        // Architectury has no cross-loader screen-render-post event, so the collab-cursor overlay is
        // NeoForge-only for now; the Fabric equivalent (a mixin or fabric-api ScreenEvents) is a follow-up.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(KjsGenClient::onScreenRenderPost);
        //?}
    }

    //? if neoforge {
    /** Top-most overlay: remote cursors float above the editor and any of its sub-dialogs. */
    static void onScreenRenderPost(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (com.zizazr.kjsgen.ui.vanilla.CollabScreens.isEditorContext(event.getScreen())) {
            com.zizazr.kjsgen.ui.vanilla.CollabCursors.render(event.getGuiGraphics());
        }
    }
    //?}

    static void onClientTick(Minecraft minecraft) {
        while (OPEN_EDITOR.consumeClick()) {
            openEditor();
        }
        // Keep the other operators' presence tooltip up to date with which screen we're on.
        ClientEditSession.tickScreenReport();
    }

    /** True when the local player may use the (dev-only) editor. */
    private static boolean mayEdit(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        if (!mc.hasSingleplayerServer() && !mc.player.hasPermissions(2)) {
            mc.player.displayClientMessage(Component.translatable("kjsgen.error.no_permission"), false);
            return false;
        }
        return true;
    }

    /** Opens the recipe editor if the local player is allowed to use it. */
    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        mc.setScreen(new VanillaEditorScreen());
    }

    /**
     * Adds (or replaces the same type+output entry of) {@code recipe} in the current
     * project and opens the editor with it selected. Called from the JEI
     * "edit in kjsgen" recipe button.
     */
    public static void openEditorWithCapturedRecipe(RecipeInstance recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (!mayEdit(mc)) {
            return;
        }
        RecipeProject project = ClientEditSession.project();
        String uid = project.addOrReplaceByOutput(recipe);
        // In remote mode the recipe must reach the server before the editor requests its snapshot,
        // so push it first; the editor constructor then sends OP_OPEN and gets it back in the snapshot.
        ClientEditSession.pushRecipe(recipe);
        mc.setScreen(new VanillaEditorScreen(uid));
    }
}
