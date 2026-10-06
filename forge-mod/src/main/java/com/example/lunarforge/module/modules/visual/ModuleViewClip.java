package com.example.lunarforge.module.modules.visual;

import com.example.lunarforge.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Ported from Leader-Lite (leader.module.modules.render.ViewClip): lets the
 * third-person camera clip through walls (no camera pull-in), suppresses
 * inside-block fog, and renders chunk back faces so the world stays visible
 * from inside/behind terrain. The actual work happens in three ASM patches
 * (core/RenderHooksTransformer -> module/render/ViewClipHooks): VisGraph
 * visibility recording, EntityRenderer.orientCamera and EntityRenderer.setupFog.
 * Chunk renderers reload on every toggle because the visibility graph is baked
 * into the compiled chunk meshes.
 */
public final class ModuleViewClip extends Module {

    public ModuleViewClip() {
        super("VIEW_CLIP", false);
    }

    @Override protected void onEnable() { reloadRenderers(); }

    @Override protected void onDisable() { reloadRenderers(); }

    private void reloadRenderers() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal != null && mc.theWorld != null) mc.renderGlobal.loadRenderers();
    }
}
