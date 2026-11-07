package dev.nexobedrock.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.Pair;
import dev.nexobedrock.NexoBedrockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Rewrites inventory/equipment display names only for Floodgate clients. */
public final class BedrockItemNamePacketListener {
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final NexoBedrockPlugin plugin;
    private final String bedrockNamePrefix;

    public BedrockItemNamePacketListener(NexoBedrockPlugin plugin, String bedrockNamePrefix) {
        this.plugin = plugin;
        this.bedrockNamePrefix = bedrockNamePrefix;
    }

    public void register() {
        ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS,
                PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isBedrockName(event.getPlayer().getName())) return;
                try {
                    if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                        rewriteSingleItem(event);
                    } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
                        rewriteItemList(event);
                    } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                        rewriteEquipment(event);
                    }
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.FINE,
                            "NexoBedrock: Bedrock text packet left unchanged", throwable);
                }
            }
        });
    }

    private boolean isBedrockName(String name) {
        return bedrockNamePrefix != null && !bedrockNamePrefix.isEmpty()
                && name.startsWith(bedrockNamePrefix);
    }

    private void rewriteSingleItem(PacketEvent event) {
        var modifier = event.getPacket().getItemModifier();
        for (int i = 0; i < modifier.size(); i++) {
            ItemStack rewritten = withGlyphDisplayName(modifier.readSafely(i));
            if (rewritten != null) modifier.write(i, rewritten);
        }
    }

    private void rewriteItemList(PacketEvent event) {
        var modifier = event.getPacket().getItemListModifier();
        for (int i = 0; i < modifier.size(); i++) {
            List<ItemStack> items = modifier.readSafely(i);
            if (items == null) continue;
            List<ItemStack> rewritten = new ArrayList<>(items);
            for (int slot = 0; slot < rewritten.size(); slot++) {
                ItemStack withName = withGlyphDisplayName(rewritten.get(slot));
                if (withName != null) rewritten.set(slot, withName);
            }
            modifier.write(i, rewritten);
        }
    }

    private void rewriteEquipment(PacketEvent event) {
        var modifier = event.getPacket().getSlotStackPairLists();
        for (int i = 0; i < modifier.size(); i++) {
            List<Pair<com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot, ItemStack>> pairs =
                    modifier.readSafely(i);
            if (pairs == null) continue;
            List<Pair<com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot, ItemStack>> rewritten =
                    new ArrayList<>(pairs.size());
            for (var pair : pairs) {
                ItemStack withName = withGlyphDisplayName(pair.getSecond());
                rewritten.add(new Pair<>(pair.getFirst(), withName == null ? pair.getSecond() : withName));
            }
            modifier.write(i, rewritten);
        }
    }

    private ItemStack withGlyphDisplayName(ItemStack original) {
        if (original == null || original.getType().isAir() || !original.hasItemMeta()) return null;
        ItemMeta meta = original.getItemMeta();
        if (meta == null || meta.displayName() == null) return null;
        String source = HEX.serialize(meta.displayName());
        String glyphs = plugin.getGlyphTextMap().get(source);
        if (glyphs == null || glyphs.equals(source)) return null;
        ItemStack copy = original.clone();
        meta = copy.getItemMeta();
        if (meta == null) return null;
        meta.displayName(Component.text(glyphs));
        copy.setItemMeta(meta);
        return copy;
    }
}
