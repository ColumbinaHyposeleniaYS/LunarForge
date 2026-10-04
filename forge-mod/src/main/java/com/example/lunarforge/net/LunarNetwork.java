package com.example.lunarforge.net;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LunarNetwork {
    static final Logger LOG = LogManager.getLogger("LunarForge/Network");
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("lunarforge.network"));
    private static final long MIN_BACKOFF = 2000, MAX_BACKOFF = 5 * 60 * 1000;

    private static volatile AssetSocket socket;
    private static volatile UUID connectedAs;

    private LunarNetwork() {}

    public static void init() {
        if (!ENABLED) return;
        LunarBuild.init();
        LunarNetwork instance = new LunarNetwork();
        FMLCommonHandler.instance().bus().register(instance);
        FMLCommonHandler.instance().bus().register(new Apollo());
        Thread t = new Thread(LunarNetwork::connectLoop, "LunarForge Websocket");
        t.setDaemon(true);
        t.start();
    }

    private static void connectLoop() {
        long backoff = MIN_BACKOFF;
        while (true) {
            long started = System.currentTimeMillis();
            Session session = Minecraft.getMinecraft().getSession();
            try {
                UUID id = session.getProfile().getId();
                String jwt = LunarAuth.fetchJwt(session);
                AssetSocket s = new AssetSocket();
                socket = s;
                connectedAs = id;
                s.run(session, jwt);
                LOG.info("Disconnected from Lunar");
            } catch (Exception e) {
                LOG.warn("Could not connect to Lunar: {}", e.toString());
            } finally {
                socket = null;
                connectedAs = null;
            }

            backoff = System.currentTimeMillis() - started > 60000 ? MIN_BACKOFF : Math.min(backoff * 2, MAX_BACKOFF);
            try { Thread.sleep(backoff); } catch (InterruptedException e) { return; }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        AssetSocket s = socket;
        TabLogos.tick(s);

        UUID as = connectedAs;
        if (s != null && as != null) {
            UUID now = null;
            try { now = Minecraft.getMinecraft().getSession().getProfile().getId(); } catch (RuntimeException ignored) {}
            if (!as.equals(now)) s.close();
        }
    }
}
