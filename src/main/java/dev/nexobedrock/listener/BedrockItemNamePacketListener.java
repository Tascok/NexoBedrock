package dev.nexobedrock.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.Pair;
import dev.nexobedrock.NexoBedrockPlugin;
import dev.nexobedrock.bedrock.GlyphComponentRewriter;
import dev.nexobedrock.bedrock.GlyphMappings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;

/** Rewrites inventory/equipment display names only for Floodgate clients. */
public final class BedrockItemNamePacketListener {
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final NexoBedrockPlugin plugin;
    private final String bedrockNamePrefix;
    private final boolean rgbEnabled;
    private final GlyphMappings glyphMappings;
    private final GlyphComponentRewriter glyphRewriter = new GlyphComponentRewriter();

    public BedrockItemNamePacketListener(NexoBedrockPlugin plugin, String bedrockNamePrefix,
                                         boolean rgbEnabled) {
        this.plugin = plugin;
        this.bedrockNamePrefix = bedrockNamePrefix;
        this.rgbEnabled = rgbEnabled;
        this.glyphMappings = new GlyphMappings(plugin);
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
            ItemStack rewritten = withRgbDisplayName(modifier.readSafely(i));
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
                ItemStack withName = withRgbDisplayName(rewritten.get(slot));
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
                ItemStack withName = withRgbDisplayName(pair.getSecond());
                rewritten.add(new Pair<>(pair.getFirst(), withName == null ? pair.getSecond() : withName));
            }
            modifier.write(i, rewritten);
        }
    }

    private ItemStack withRgbDisplayName(ItemStack original) {
        if (original == null || original.getType().isAir() || !original.hasItemMeta()) return null;
        ItemStack copy = original.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return null;
        Map<String, Integer> mappings = glyphMappings.get();
        boolean changed = false;
        Component name = meta.displayName();
        if (name != null) {
            Component rewritten = glyphRewriter.rewrite(name, mappings);
            if (rgbEnabled) {
                String rgb = HEX.serialize(rewritten);
                if (rgb.contains("\u00a7x")) rewritten = Component.text(rgb);
            }
            if (!rewritten.equals(name)) {
                meta.displayName(rewritten);
                changed = true;
            }
        }
        Component itemName = meta.itemName();
        if (itemName != null) {
            Component rewritten = glyphRewriter.rewrite(itemName, mappings);
            if (!rewritten.equals(itemName)) {
                meta.itemName(rewritten);
                changed = true;
            }
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> rewritten = new ArrayList<>(lore.size());
            for (Component line : lore) {
                Component result = glyphRewriter.rewrite(line, mappings);
                rewritten.add(result);
                if (!result.equals(line)) changed = true;
            }
            if (!rewritten.equals(lore)) meta.lore(rewritten);
        }
        if (!changed) return null;
        copy.setItemMeta(meta);
        return copy;
    }
}
