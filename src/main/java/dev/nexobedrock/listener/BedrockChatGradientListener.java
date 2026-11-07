package dev.nexobedrock.listener;

import dev.nexobedrock.NexoBedrockPlugin;
import dev.nexobedrock.bedrock.ChatGradientGlyphs;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;

/** Replaces RGB chat text with pre-baked font glyphs only for Bedrock viewers. */
public final class BedrockChatGradientListener implements Listener {
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final NexoBedrockPlugin plugin;
    private final String bedrockNamePrefix;

    public BedrockChatGradientListener(NexoBedrockPlugin plugin, String bedrockNamePrefix) {
        this.plugin = plugin;
        this.bedrockNamePrefix = bedrockNamePrefix;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = original.render(source, displayName, message, viewer);
            if (!(viewer instanceof Player player) || !isBedrock(player.getName())) return rendered;
            String legacy = HEX.serialize(rendered);
            String rewritten = ChatGradientGlyphs.rewrite(legacy, plugin.getGlyphTextMap());
            return rewritten.equals(legacy) ? rendered : Component.text(rewritten);
        });
    }

    private boolean isBedrock(String playerName) {
        return bedrockNamePrefix != null && !bedrockNamePrefix.isEmpty()
                && playerName.startsWith(bedrockNamePrefix);
    }
}
