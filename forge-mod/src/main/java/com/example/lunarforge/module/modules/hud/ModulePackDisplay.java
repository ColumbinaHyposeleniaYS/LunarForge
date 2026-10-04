package com.example.lunarforge.module.modules.hud;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.*;
import com.example.lunarforge.module.setting.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

public final class ModulePackDisplay extends Module {
    public enum Order implements ChoiceSetting.Option {
        FIRST("first"), LAST("last"); final String id; Order(String id) { this.id=id; } public String langId() { return id; }
    }
    public enum Replacement implements ChoiceSetting.Option {
        NONE("none"), FULL("full_text"), WHITE_ONLY("white_only_text");
        final String id; Replacement(String id) { this.id=id; } public String langId() { return id; }
    }
    private final BoolSetting icon = bool("packIcon",true), description = bool("packDescription",false), extension = bool("packExtension",false);
    private final ChoiceSetting<Order> order = choice("packOrder",Order.FIRST);
    private final BoolSetting ignoreServer = bool("ignoreServerPack",false);
    private final ChoiceSetting<Replacement> titleReplacement = choice("titleReplacement",Replacement.WHITE_ONLY), descriptionReplacement = choice("descriptionReplacement",Replacement.NONE);
    private final ColorSetting titleColor = color("textColor",-1), descriptionColor = color("descriptionReplacementColor",-1);
    private final BoolSetting moveDown = bool("moveTitleDown",true), bold = bool("keepBold",true), italic = bool("keepItalic",true), underline = bool("keepUnderline",true), strike = bool("keepStrikethrough",true), obfuscated = bool("keepObfuscated",true);
    private final BoolSetting shadow = bool("textShadow",true), brackets = bool("brackets",true), background = bool("background",true), border = bool("border",false);
    private final NumberSetting height = integer("backgroundHeight",24,12,64), thickness = decimal("borderThickness",.5f,.5f,3);
    private final ColorSetting bracketColor = color("bracketColor",-1), backgroundColor = color("backgroundColor",0x6F000000), borderColor = color("borderColor",0x9F000000);
    private IResourcePack selected;
    private ResourceLocation thumbnail;
    private List<String> lines = Collections.emptyList();
    private long checked;
    private boolean vanillaPack;
    public ModulePackDisplay() { super("PACK_DISPLAY",false); hud(new Hud()); child(new ModuleShaderPackDisplay(),"generalOptions"); }
    protected void layout(Page p) {
        p.section("generalOptions",s -> {
            s.add(icon,description,extension); s.add(moveDown).hideIf(description::on); s.add(ignoreServer,order,titleReplacement,descriptionReplacement,shadow);
            s.group(brackets,b -> b.add(bracketColor)).hideIf(background::on);
            s.group(background,c -> { c.add(height); c.group(border,b -> b.add(thickness,borderColor)); c.add(backgroundColor); });
        });
        p.section("colorOptions",s -> {
            s.add(titleColor).hideIf(() -> titleReplacement.get()==Replacement.NONE);
            s.add(descriptionColor).hideIf(() -> descriptionReplacement.get()==Replacement.NONE);
            s.add(bold,italic,underline,strike,obfuscated);
        });
    }
    private void refresh() {
        long now=net.minecraft.client.Minecraft.getSystemTime(); if (now-checked<500) return; checked=now;
        ResourcePackRepository repository=mc().getResourcePackRepository();
        List<ResourcePackRepository.Entry> packs=repository.getRepositoryEntries();
        IResourcePack pack=!ignoreServer.on() && (order.get()==Order.FIRST || packs.isEmpty()) ? repository.getResourcePackInstance() : null;
        if (pack==null && !packs.isEmpty()) pack=packs.get(order.get()==Order.FIRST?packs.size()-1:0).getResourcePack();
        if (pack==null) pack=repository.rprDefaultResourcePack;
        if (pack==selected && selected!=null) return;
        selected=pack;
        vanillaPack=pack==repository.rprDefaultResourcePack;
        if (thumbnail!=null) { mc().getTextureManager().deleteTexture(thumbnail); thumbnail=null; }
        try {
            BufferedImage image=pack.getPackImage();
            if (image==null) image=repository.rprDefaultResourcePack.getPackImage();
            if (image!=null) thumbnail=mc().getTextureManager().getDynamicTextureLocation("lunarforge-pack-display",new DynamicTexture(image));
        } catch (IOException ignored) {
            try { BufferedImage fallback=repository.rprDefaultResourcePack.getPackImage();
                if (fallback!=null) thumbnail=mc().getTextureManager().getDynamicTextureLocation("lunarforge-pack-display",new DynamicTexture(fallback));
            } catch (IOException absent) {  }
        }
        if (vanillaPack) { lines=Collections.singletonList(lang("defaultDescription")); return; }
        try {
            PackMetadataSection metadata=pack.getPackMetadata(mc().getResourcePackRepository().rprMetadataSerializer,"pack");
            lines=metadata==null?Collections.<String>emptyList():mc().fontRendererObj.listFormattedStringToWidth(metadata.getPackDescription().getFormattedText(),256);
        } catch (IOException ignored) { lines=Collections.emptyList(); }
    }
    private String format(String text,Replacement replacement) {
        if (!bold.on()) text=text.replace("\u00a7l",""); if (!italic.on()) text=text.replace("\u00a7o","");
        if (!underline.on()) text=text.replace("\u00a7n",""); if (!strike.on()) text=text.replace("\u00a7m","");
        if (!obfuscated.on()) text=text.replace("\u00a7k","");
        return replacement==Replacement.FULL?text.replaceAll("(?i)\u00a7[0-9A-F]",""):text;
    }
    private final class Hud extends HudElement {
        private String title;
        private float contentHeight,textX,textY;
        private boolean showDescription,showIcon,showBrackets;
        Hud() { super(ModulePackDisplay.this,0,0,HudAnchor.TOP_RIGHT); }
        public void layout(Page p) { p.section("generalOptions", s -> s.addFirst(scale)); }
        public boolean visible(boolean preview) {
            refresh(); title=selected==null||vanillaPack?lang("default"):selected.getPackName();
            if (!extension.on()) title=title.replace(".zip",""); title=format(title,titleReplacement.get());
            showDescription=description.on()&&!lines.isEmpty(); showIcon=icon.on()&&thumbnail!=null;
            showBrackets=!background.on()&&brackets.on(); if (showBrackets) title="["+title+"]";
            float w=Draw.width(title); int h=Draw.fontHeight(); contentHeight=h;
            if (showDescription || !moveDown.on()&&!lines.isEmpty()) contentHeight+=lines.size()*(h+2);
            if (showDescription) for (String line:lines) w=Math.max(w,Draw.width(line));
            float totalHeight=Math.max(contentHeight,height.value())+4;
            size(w+8+(showIcon?totalHeight:0),totalHeight); textX=4+(showIcon?totalHeight:0); textY=(totalHeight-contentHeight)/2;
            return true;
        }
        public void render(boolean preview) {
            if (background.on()) { Draw.fill(backgroundColor,0,0,width(),height()); if (border.on()) Draw.border(borderColor,0,0,width(),height(),thickness.value()); }
            if (showIcon) Draw.textureRegion(thumbnail,0,0,height(),height(),0,0,1,1);
            Draw.text(title,textX,textY,titleReplacement.get()==Replacement.NONE?-1:titleColor.color(0),shadow.on());
            float y=textY+Draw.fontHeight()+2;
            if (showDescription) for (String line:lines) { Draw.text(format(line,descriptionReplacement.get()),textX,y,descriptionReplacement.get()==Replacement.NONE?-1:descriptionColor.color(0),shadow.on()); y+=Draw.fontHeight()+2; }
        }
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void reload(net.minecraftforge.client.event.TextureStitchEvent.Post event) { selected=null; checked=0; }
}
