package com.example.lunarforge.module.render;

import com.example.lunarforge.module.modules.visual.ModuleMobSize;
import net.minecraft.entity.EntityLivingBase;

public final class NametagHooks {
    private NametagHooks() {}

    public static double y(EntityLivingBase entity, double y) {
        return com.example.lunarforge.module.modules.server.ModuleHypixelMods.nameY(entity, ModuleMobSize.nameTagY(entity, y));
    }
}
