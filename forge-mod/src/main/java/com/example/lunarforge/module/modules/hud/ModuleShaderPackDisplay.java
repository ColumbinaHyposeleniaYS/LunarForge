package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;

public final class ModuleShaderPackDisplay extends Module {
    private final BoolSetting prefix=bool("displayPrefix",false), showNone=bool("showIfNoShaders",false);
    public ModuleShaderPackDisplay() {
        super("SHADER_PACK_DISPLAY",false);
        hud(new TextHud(this,0,0,HudAnchor.TOP_RIGHT,TextHud.sizes(12,24,64,60,100,300)) {
            protected String text(boolean preview) {
                String pack=shaderPack(); if (pack==null && !preview && !showNone.on()) return null;
                String name=(pack==null?lang("noShaders"):pack).replace('+',' ').replace(".zip","");
                return prefix.on()?lang("shaderPack")+": "+name:name;
            }
            protected Boolean staticWidthFor(String text) { return Boolean.FALSE; }
        });
    }
    protected void layout(Page p) { p.section("generalOptions",s -> s.add(prefix,showNone)); }
    private static String shaderPack() {
        for (String className:new String[]{"net.optifine.shaders.Shaders","shadersmod.client.Shaders"}) try {
            Class<?> shaders=Class.forName(className,false,ModuleShaderPackDisplay.class.getClassLoader());
            String name=String.valueOf(shaders.getMethod("getShaderPackName").invoke(null));
            return name.equals("(off)")||name.equals("OFF")||name.equals("null")?null:name;
        } catch (ReflectiveOperationException | LinkageError absent) {  }
        return null;
    }
}
