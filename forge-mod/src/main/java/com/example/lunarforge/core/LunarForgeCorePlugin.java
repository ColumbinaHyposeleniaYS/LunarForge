package com.example.lunarforge.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("LunarForgeCore")
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.TransformerExclusions("com.example.lunarforge.core.")
@IFMLLoadingPlugin.SortingIndex(1001)
public final class LunarForgeCorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"com.example.lunarforge.core.SplashTransformer", "com.example.lunarforge.core.OneSevenTransformer", "com.example.lunarforge.core.ModuleHooksTransformer", "com.example.lunarforge.core.TabListTransformer", "com.example.lunarforge.core.CameraCombatTransformer", "com.example.lunarforge.core.RenderHooksTransformer", "com.example.lunarforge.core.PortHooksTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
