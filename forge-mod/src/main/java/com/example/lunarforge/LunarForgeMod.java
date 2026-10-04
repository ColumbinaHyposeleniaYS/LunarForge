package com.example.lunarforge;

import com.example.lunarforge.feature.FeatureManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = LunarForgeMod.MOD_ID,
    name = LunarForgeMod.MOD_NAME,
    version = LunarForgeMod.VERSION,
    acceptedMinecraftVersions = "[1.8.9]",
    clientSideOnly = true
)
public final class LunarForgeMod {
    public static final String MOD_ID = "lunarforge";
    public static final String MOD_NAME = "Lunar Forge";
    public static final String VERSION = "0.1.0";

    private Logger logger;
    private java.io.File configFile;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        configFile = event.getSuggestedConfigurationFile();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.example.lunarforge.module.modules.server.BedwarsBeds.Stitcher());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FeatureManager features = new FeatureManager(configFile);

        ClientEvents clientEvents = new ClientEvents(features);
        clientEvents.registerKeyBindings();

        MinecraftForge.EVENT_BUS.register(clientEvents);
        MinecraftForge.EVENT_BUS.register(com.example.lunarforge.util.ClickCounter.INSTANCE);
        com.example.lunarforge.module.ModuleManager.registerEvents();

        com.example.lunarforge.cosmetics.render.CosmeticLayers.install();
        FMLCommonHandler.instance().bus().register(clientEvents);

        com.example.lunarforge.net.LunarNetwork.init();

        logger.info("{} loaded with {} feature(s)", MOD_NAME, features.size());
    }
}
