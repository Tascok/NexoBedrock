package dev.nexobedrock.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.nexobedrock.NexoBedrockPlugin;
import dev.nexobedrock.bedrock.GlyphMapLoader;
import dev.nexobedrock.bedrock.GlyphTextRewriter;
import dev.nexobedrock.config.PluginConfig;
import dev.nexobedrock.util.PathUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/** Rewrites custom inventory title glyphs only in outgoing packets to Bedrock players. */
public final class BedrockGuiGlyphPacketListener {
    private final NexoBedrockPlugin plugin;
    private final String bedrockNamePrefix;
    private final AtomicReference<Map<String, Integer>> glyphs = new AtomicReference<>(Map.of());
    private final GlyphMapLoader loader = new GlyphMapLoader();
    private final GlyphTextRewriter rewriter = new GlyphTextRewriter();
    private volatile long loadedModified = Long.MIN_VALUE;

    public BedrockGuiGlyphPacketListener(NexoBedrockPlugin plugin, String bedrockNamePrefix) {
        this.plugin = plugin;
        this.bedrockNamePrefix = bedrockNamePrefix;
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.HIGHEST, PacketType.Play.Server.OPEN_WINDOW) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isBedrockPlayer(event.getPlayer().getName())) return;
                Map<String, Integer> mappings = refreshMap();
                if (mappings.isEmpty()) return;
                var components = event.getPacket().getChatComponents();
                for (int index = 0; index < components.size(); index++) {
                    WrappedChatComponent component = components.readSafely(index);
                    if (component == null) continue;
                    try {
                        JsonElement json = JsonParser.parseString(component.getJson());
                        JsonElement rewritten = rewriter.rewriteByFont(json, mappings);
                        components.write(index,
                                WrappedChatComponent.fromJson(rewritten.toString()));
                    } catch (RuntimeException exception) {
                        plugin.getLogger().log(Level.FINE,
                                "NexoBedrock: GUI title glyph packet left unchanged", exception);
                    }
                }
            }
        });
    }

    private Map<String, Integer> refreshMap() {
        PluginConfig config = plugin.getPluginConfig();
        Path pack = PathUtil.resolve(plugin, config.geyserPacksFolder())
                .resolve(config.packName() + ".mcpack");
        try {
            long modified = Files.getLastModifiedTime(pack).toMillis();
            if (modified != loadedModified) {
                glyphs.set(loader.load(pack));
                loadedModified = modified;
            }
        } catch (IOException exception) {
            if (loadedModified != Long.MIN_VALUE) {
                glyphs.set(Map.of());
                loadedModified = Long.MIN_VALUE;
            }
        }
        return glyphs.get();
    }

    private boolean isBedrockPlayer(String name) {
        return bedrockNamePrefix != null && !bedrockNamePrefix.isEmpty()
                && name.startsWith(bedrockNamePrefix);
    }
}
