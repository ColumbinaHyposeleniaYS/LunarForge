package com.example.lunarforge.cosmetics;

import java.io.File;
import net.minecraft.client.Minecraft;

public final class CosmeticFolder {
    private CosmeticFolder() {}

    public static File root() {
        Minecraft mc = Minecraft.getMinecraft();
        return new File(mc == null ? new File(".") : mc.mcDataDir, "lunarforge/cosmetics");
    }

    public static File cache() { return new File(root(), "cache"); }

    public static File config(String name) { return new File(root(), name); }
}
