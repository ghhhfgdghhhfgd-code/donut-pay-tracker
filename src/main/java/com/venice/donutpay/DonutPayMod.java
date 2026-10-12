package com.venice.donutpay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DonutPayMod implements ClientModInitializer {

    record Payment(String from, double amount, long time) {}

    static final Map<String, Double> totals = new LinkedHashMap<>();
    static final Deque<Payment> recent = new ArrayDeque<>();
    static double grandTotal = 0;
    static boolean overlayVisible = true;
    static KeyBinding toggleKey, resetKey;

    static final String NAME = "(?<name>[A-Za-z0-9_]{2,16})";
    static final String AMT  = "\\$?(?<amount>[0-9][0-9,]*(?:\\.[0-9]+)?)";
    static final List<Pattern> PATTERNS = List.of(
        Pattern.compile(NAME + " has paid you \\" + AMT),
        Pattern.compile(NAME + " paid you \\" + AMT),
        Pattern.compile(NAME + " has sent you \\" + AMT),
        Pattern.compile(NAME + " sent you \\" + AMT),
        Pattern.compile(NAME + " gave you \\" + AMT),
        Pattern.compile("You received \\" + AMT + " from " + NAME),
        Pattern.compile("You have received \\" + AMT + " from " + NAME)
    );

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.donutpay.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "category.donutpay"));
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.donutpay.reset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.donutpay"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) overlayVisible = !overlayVisible;
            while (resetKey.wasPressed()) reset();
        });

        ClientReceiveMessageEvents.GAME.register((msg, ov) -> handle(msg.getString()));
        ClientReceiveMessageEvents.CHAT.register((msg, sm, snd, p, ts) -> handle(msg.getString()));

        HudRenderCallback.EVENT.register(DonutPayMod::render);
    }

    static void handle(String raw) {
        if (raw == null) return;
        String msg = raw.replaceAll("§.", "").trim();
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(msg);
            if (m.find()) {
                try {
                    record(m.group("name"), Double.parseDouble(m.group("amount").replace(",", "")));
                } catch (Exception ignored) {}
                return;
            }
        }
    }

    static void record(String from, double amount) {
        totals.merge(from, amount, Double::sum);
        grandTotal += amount;
        recent.addFirst(new Payment(from, amount, System.currentTimeMillis()));
        while (recent.size() > 25) recent.removeLast();
    }

    static void reset() { totals.clear(); recent.clear(); grandTotal = 0; }

    static final DecimalFormat DF = new DecimalFormat("#,##0.##");
    static String money(double v) { return "$" + DF.format(v); }

    static void render(DrawContext ctx, RenderTickCounter tick) {
        if (!overlayVisible) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        TextRenderer tr = client.textRenderer;
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("PAYMENT TRACKER").formatted(Formatting.GOLD, Formatting.BOLD));
        lines.add(Text.literal("Received: " + money(grandTotal)).formatted(Formatting.GREEN));
        if (!totals.isEmpty()) {
            lines.add(Text.literal(""));
            lines.add(Text.literal("From players:").formatted(Formatting.YELLOW));
            totals.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(10)
                .forEach(e -> lines.add(Text.literal("  " + e.getKey() + ": " + money(e.getValue()))
                    .formatted(Formatting.WHITE)));
        }
        if (!recent.isEmpty()) {
            lines.add(Text.literal(""));
            lines.add(Text.literal("Recent:").formatted(Formatting.YELLOW));
            recent.stream().limit(6)
                .forEach(p -> lines.add(Text.literal("  +" + money(p.amount()) + " from " + p.from())
                    .formatted(Formatting.AQUA)));
        }

        int pad = 4, lineH = tr.fontHeight + 2;
        int w = lines.stream().mapToInt(tr::getWidth).max().orElse(80);
        int h = lines.size() * lineH;
        int x = 6, y = 6;
        ctx.fill(x - pad, y - pad, x + w + pad, y + h + pad, 0x99000000);
        ctx.fill(x - pad, y - pad, x + w + pad, y - pad + 1, 0xFF6A6A6A);
        int cy = y;
        for (Text l : lines) { ctx.drawTextWithShadow(tr, l, x, cy, 0xFFFFFFFF); cy += lineH; }
    }
}
