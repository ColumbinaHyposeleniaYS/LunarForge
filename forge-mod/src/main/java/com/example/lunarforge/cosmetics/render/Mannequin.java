package com.example.lunarforge.cosmetics.render;

import com.example.lunarforge.cosmetics.Cosmetic;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

public final class Mannequin extends EntityOtherPlayerMP {
    public static final ResourceLocation ASTRONAUT = new ResourceLocation("lunarforge", "skins/astronaut_dark.png");
    private static WorldClient previewWorld;

    private final boolean self;
    private final List<Cosmetic> cosmetics = new ArrayList<Cosmetic>();

    private Mannequin(World world, boolean self) {
        super(world, new GameProfile(UUID.randomUUID(), self ? Minecraft.getMinecraft().getSession().getUsername() : "Astronaut"));
        this.self = self;
    }

    public static Mannequin astronaut() { return new Mannequin(world(), false); }

    public static Mannequin self() { return new Mannequin(world(), true); }

    public static World world() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) return mc.theWorld;
        if (previewWorld == null)
            previewWorld = new WorldClient(null, new WorldSettings(0L, WorldSettings.GameType.NOT_SET, false, false, WorldType.DEFAULT),
                0, EnumDifficulty.PEACEFUL, mc.mcProfiler);
        return previewWorld;
    }

    public List<Cosmetic> cosmetics() { return cosmetics; }

    public void wear(List<Cosmetic> list) { cosmetics.clear(); cosmetics.addAll(list); }

    @Override public ResourceLocation getLocationSkin() {
        return self && Minecraft.getMinecraft().thePlayer != null ? Minecraft.getMinecraft().thePlayer.getLocationSkin() : ASTRONAUT;
    }

    @Override public String getSkinType() {
        return self && Minecraft.getMinecraft().thePlayer != null ? Minecraft.getMinecraft().thePlayer.getSkinType() : "slim";
    }

    @Override public ResourceLocation getLocationCape() {
        return self && Minecraft.getMinecraft().thePlayer != null ? Minecraft.getMinecraft().thePlayer.getLocationCape() : null;
    }

    @Override public boolean hasPlayerInfo() { return self && Minecraft.getMinecraft().thePlayer != null; }

    @Override public boolean isWearing(EnumPlayerModelParts part) {
        return !self || Minecraft.getMinecraft().thePlayer == null || Minecraft.getMinecraft().thePlayer.isWearing(part);
    }

    @Override public boolean isSpectator() { return false; }

    @Override public boolean isInvisibleToPlayer(EntityPlayer player) { return true; }
}
