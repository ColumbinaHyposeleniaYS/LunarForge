package com.example.lunarforge.module.modules.mechanic;

import com.example.lunarforge.feature.HudAnchor;
import com.example.lunarforge.gui.ui.LunarLang;
import com.example.lunarforge.module.Module;
import com.example.lunarforge.module.Page;
import com.example.lunarforge.module.hud.Draw;
import com.example.lunarforge.module.hud.TextHud;
import com.example.lunarforge.module.setting.BoolSetting;
import com.example.lunarforge.module.setting.TextSetting;
import java.text.DecimalFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

public final class ModuleToggleSneakHud extends Module {
    private static final ResourceLocation SPRINTING = new ResourceLocation("lunarforge", "textures/hud/sprinting.png");
    private static final ResourceLocation SNEAKING = new ResourceLocation("lunarforge", "textures/hud/sneaking.png");

    enum State {
        FLYING("flying"), FLYING_BOOST("flying", "boost", true), RIDING("riding"), DESCENDING("descending"),
        DISMOUNTING("dismounting"), SNEAKING_TOGGLED("sneaking", "toggled"), SNEAKING_HELD("sneaking", "held"),
        SPRINTING_TOGGLED("sprinting", "toggled"), SPRINTING_HELD("sprinting", "held"), SPRINTING_VANILLA("sprinting", "vanilla"),
        HELD("held", null), TOGGLED("toggled", null), VANILLA("vanilla", null);

        final String main, partition;
        final boolean extraArguments;
        State(String main) { this(main, null, false); }
        State(String main, String partition) { this(main, partition, false); }
        State(String main, String partition, boolean extraArguments) { this.main = main; this.partition = partition; this.extraArguments = extraArguments; }
    }

    private final ModuleToggleSneak parent;
    private final DecimalFormat boostFormat = new DecimalFormat("#.00");
    private final BoolSetting iconMode = bool("iconMode", false);
    private final TextSetting sprintingText = text("sprintingText");
    private final TextSetting sneakingText = text("sneakingText");
    private final TextSetting flyingText = text("flyingText");
    private final BoolSetting showRidingText = bool("showRidingText", true);
    private final BoolSetting descendingText = bool("descendingText", true);
    private final Hud hud;

    ModuleToggleSneakHud(ModuleToggleSneak parent) {
        super("TOGGLE_SNEAK_HUD_CHILD", true);
        this.parent = parent;
        hud = hud(new Hud());
    }

    @Override protected void layout(Page page) {
        page.section("generalOptions", s -> s.add(iconMode, sprintingText, sneakingText, flyingText, showRidingText, descendingText));
    }

    private String main(State state) {
        if (!sprintingText.isEmpty() && "sprinting".equals(state.main)) return sprintingText.get();
        if (!sneakingText.isEmpty() && "sneaking".equals(state.main)) return sneakingText.get();
        if (!flyingText.isEmpty() && "flying".equals(state.main)) return flyingText.get();
        return LunarLang.get("settings", state.main);
    }

    private String partition(State state, Object... args) {
        if (state.partition == null || args == null || state.extraArguments && args.length == 0) return null;
        return LunarLang.get("settings", state.partition, args);
    }

    private String format(State state, Object... args) { return format(state, null, args); }

    private String format(State state, State outer, Object... args) {
        if (iconMode.on()) {
            switch (state) {
                case FLYING_BOOST: case SPRINTING_VANILLA: state = State.VANILLA; break;
                case SNEAKING_TOGGLED: case SPRINTING_TOGGLED: state = State.TOGGLED; break;
                case SNEAKING_HELD: case SPRINTING_HELD: state = State.HELD; break;
                default: break;
            }
        }
        if (!descendingText.on() && (state == State.DESCENDING || state == State.DISMOUNTING))
            return outer == null ? "" : format(outer, args);
        if (hud.background.on()) return main(state);
        StringBuilder out = new StringBuilder();
        if (outer != null) out.append(format(outer, args)).append(" ");
        out.append(main(state));
        String part = partition(state, args);
        if (part != null) out.append(" (").append(part).append(")");
        return out.toString();
    }

    private String state() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        GameSettings gs = mc.gameSettings;
        String text = "";
        boolean flying = player.capabilities.isFlying;
        boolean riding = player.isRiding();
        boolean holdingSneak = ModuleToggleSneak.physicallyDown(gs.keyBindSneak);
        boolean holdingSprint = ModuleToggleSneak.physicallyDown(gs.keyBindSprint);
        State state = null;
        if (flying) {
            if (parent.flyBoost.on() && holdingSprint) {
                state = State.FLYING_BOOST;
                text += format(state, boostFormat.format(parent.flyBoostAmount.intValue()));
            } else {
                state = State.FLYING;
                text += format(state);
            }
        }
        if (riding && showRidingText.on()) {
            state = State.RIDING;
            text = format(State.RIDING);
        }
        if (gs.keyBindSneak.isKeyDown()) {
            text = flying ? format(State.DESCENDING, state)
                : riding && state != null ? format(State.DISMOUNTING, state)
                : holdingSneak && !parent.sneaking ? text + format(State.SNEAKING_HELD)
                : text + format(State.SNEAKING_TOGGLED);
        } else if (gs.keyBindSprint.isKeyDown()) {
            if (!flying && !riding) {
                boolean vanilla = !parent.toggleSprint.on();
                text = holdingSprint && !parent.sprinting ? text + format(State.SPRINTING_HELD)
                    : parent.sprintHeld ? text + format(State.SPRINTING_HELD)
                    : vanilla ? text + format(State.SPRINTING_VANILLA)
                    : text + format(State.SPRINTING_TOGGLED);
            }
        } else if (!flying && player.isSprinting()) {
            text += format(State.SPRINTING_VANILLA);
        }
        return text;
    }

    private final class Hud extends TextHud {
        Hud() { super(ModuleToggleSneakHud.this, 0, 0, HudAnchor.TOP_RIGHT, sizes(10, 18, 22, 50, 56, 80), false, false, false); }

        @Override protected String text(boolean preview) {
            if (Minecraft.getMinecraft().thePlayer == null) return preview ? "Sprinting" : "";
            String text = state();
            return preview && text.isEmpty() ? "Sprinting" : text;
        }

        @Override protected Boolean staticWidthFor(String text) {
            if (text == null) return null;
            return !text.contains(main(State.DESCENDING)) && !text.contains(main(State.DISMOUNTING));
        }

        @Override protected float height(boolean bg) { return super.height(bg) + (iconMode.on() ? 24 : 0); }

        @Override protected void drawText(String text, float x, float y, boolean withBrackets, boolean shadow) {
            if (iconMode.on()) {
                float w = Draw.width(withBrackets ? "[" + text + "]" : text);
                EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
                ResourceLocation icon = player != null && player.isSneaking() ? SNEAKING : SPRINTING;
                Draw.texture(icon, (int)(x + w / 2 - 12), (int)(y - 12), 24, 24, 0xFFFFFFFF);
                super.drawText(text, x, y + 12, withBrackets, shadow);
            } else {
                super.drawText(text, x, y, withBrackets, shadow);
            }
        }
    }
}
