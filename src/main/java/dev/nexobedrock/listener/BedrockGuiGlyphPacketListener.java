package dev.nexobedrock.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedTeamParameters;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/** Rewrites unsigned text components sent to Bedrock players; signed chat is untouched. */
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
                ListenerPriority.HIGHEST, PacketType.Play.Server.OPEN_WINDOW,
                PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Server.SET_ACTION_BAR_TEXT,
                PacketType.Play.Server.SET_TITLE_TEXT, PacketType.Play.Server.SET_SUBTITLE_TEXT,
                PacketType.Play.Server.SCOREBOARD_OBJECTIVE, PacketType.Play.Server.SCOREBOARD_SCORE,
                PacketType.Play.Server.SCOREBOARD_TEAM) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isBedrockPlayer(event.getPlayer().getName())) return;
                Map<String, Integer> mappings = refreshMap();
                if (mappings.isEmpty()) return;
                try {
                    if (event.getPacketType() == PacketType.Play.Server.SCOREBOARD_TEAM) {
                        rewriteTeam(event, mappings);
                        return;
                    }
                    var components = event.getPacket().getChatComponents();
                    for (int index = 0; index < components.size(); index++) {
                        WrappedChatComponent component = components.readSafely(index);
                        if (component != null) components.write(index, rewrite(component, mappings));
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.FINE,
                            "NexoBedrock: glyph text packet left unchanged", exception);
                }
            }
        });
    }

    private void rewriteTeam(PacketEvent event, Map<String, Integer> mappings) {
        var modifier = event.getPacket().getOptionalTeamParameters();
        for (int index = 0; index < modifier.size(); index++) {
            Optional<WrappedTeamParameters> value = modifier.readSafely(index);
            if (value == null || value.isEmpty()) continue;
            WrappedTeamParameters team = value.get();
            WrappedTeamParameters rewritten = WrappedTeamParameters.newBuilder(team)
                    .displayName(rewrite(team.getDisplayName(), mappings))
                    .prefix(rewrite(team.getPrefix(), mappings))
                    .suffix(rewrite(team.getSuffix(), mappings)).build();
            modifier.write(index, Optional.of(rewritten));
        }
    }

    private WrappedChatComponent rewrite(WrappedChatComponent component,
                                          Map<String, Integer> mappings) {
        if (component == null) return null;
        JsonElement json = JsonParser.parseString(component.getJson());
        return WrappedChatComponent.fromJson(rewriter.rewriteByFont(json, mappings).toString());
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
