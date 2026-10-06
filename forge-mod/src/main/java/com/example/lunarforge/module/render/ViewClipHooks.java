package com.example.lunarforge.module.render;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.Vec3;

/**
 * Runtime hooks injected by core/RenderHooksTransformer for ModuleViewClip
 * (ported from Leader-Lite's ViewClip mixins):
 *
 * - VisGraph.func_178606_a is skipped for every chunk, so the chunk visibility
 *   graph stays empty and vanilla's computeVisibility() marks every face
 *   visible (fewer than 256 recorded corners -> setAllVisible(true)) - chunks
 *   render their back faces through walls.
 * - EntityRenderer.orientCamera's camera-clip raycast (Vec3.distanceTo) is
 *   replaced with the fixed 4.0 third-person distance, so the camera is never
 *   pulled in by blocks.
 * - EntityRenderer.setupFog reads air for the viewpoint block, so being
 *   inside water/lava never applies its fog.
 *
 * Toggling the module reloads all chunk renderers (Leader-Lite does the same),
 * since the visibility graph is baked into the compiled chunk meshes.
 */
public final class ViewClipHooks {
    private ViewClipHooks() {}

    public static boolean skipVisGraph() {
        return com.example.lunarforge.module.Modules.enabled("view_clip");
    }

    public static double cameraDistance(Vec3 from, Vec3 to) {
        if (com.example.lunarforge.module.Modules.enabled("view_clip")) return 4.0D;
        return from.distanceTo(to);
    }

    public static Material fogMaterial(Block block) {
        return com.example.lunarforge.module.Modules.enabled("view_clip") ? Material.air : block.getMaterial();
    }
}
