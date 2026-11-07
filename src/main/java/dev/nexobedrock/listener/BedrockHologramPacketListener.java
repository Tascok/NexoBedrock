package dev.nexobedrock.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.Pair;
import dev.nexobedrock.NexoBedrockPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Preserves RGB formatting for entity and item packets only to Floodgate clients. */
public final class BedrockHologramPacketListener {
    private static final int TEXT_DISPLAY_TEXT_METADATA = 23;
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final NexoBedrockPlugin plugin;
    private final String bedrockNamePrefix;
    private final ConcurrentHashMap<UUID, Set<Integer>> textDisplays = new ConcurrentHashMap<>();

    public BedrockHologramPacketListener(NexoBedrockPlugin plugin, String bedrockNamePrefix) {
        this.plugin = plugin;
        this.bedrockNamePrefix = bedrockNamePrefix;
    }

    public void register() {
        ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.ENTITY_METADATA,
                PacketType.Play.Server.ENTITY_DESTROY,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS,
                PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!isBedrockName(event.getPlayer().getName())) return;
                try {
                    if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                        trackSpawn(event);
                    } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                        rewriteText(event);
                    } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_DESTROY) {
                        forgetDestroyed(event);
                    } else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
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

    private void trackSpawn(PacketEvent event) {
        EntityType type = event.getPacket().getEntityTypeModifier().readSafely(0);
        if (type != EntityType.TEXT_DISPLAY) return;
        int entityId = event.getPacket().getIntegers().read(0);
        textDisplays.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(entityId);
    }

    private void forgetDestroyed(PacketEvent event) {
        Set<Integer> tracked = textDisplays.get(event.getPlayer().getUniqueId());
        if (tracked == null) return;
        List<Integer> destroyed = event.getPacket().getIntLists().readSafely(0);
        if (destroyed != null) tracked.removeAll(destroyed);
    }

    private void rewriteText(PacketEvent event) {
        int entityId = event.getPacket().getIntegers().read(0);
        Set<Integer> tracked = textDisplays.get(event.getPlayer().getUniqueId());
        if (tracked == null || !tracked.contains(entityId)) return;

        List<WrappedDataValue> original = event.getPacket().getDataValueCollectionModifier().read(0);
        if (original == null || original.isEmpty()) return;
        List<WrappedDataValue> rewritten = new ArrayList<>(original);
        boolean changed = false;
        for (int i = 0; i < rewritten.size(); i++) {
            WrappedDataValue value = rewritten.get(i);
            if (value.getIndex() != TEXT_DISPLAY_TEXT_METADATA) continue;
            Object raw = value.getValue();
            boolean optional = raw instanceof Optional<?>;
            Object componentValue = optional ? ((Optional<?>) raw).orElse(null) : raw;
            if (!(componentValue instanceof WrappedChatComponent chat)) continue;

            Component component = GsonComponentSerializer.gson().deserialize(chat.getJson());
            String legacyRgb = HEX.serialize(component);
            if (!legacyRgb.contains("\u00a7x")) continue;

            Component legacyText = Component.text(legacyRgb);
            WrappedChatComponent bedrockText = WrappedChatComponent.fromJson(
                    GsonComponentSerializer.gson().serialize(legacyText));
            Object replacement = optional ? Optional.of(bedrockText) : bedrockText;
            rewritten.set(i, new WrappedDataValue(value.getIndex(), value.getSerializer(), replacement));
            changed = true;
        }
        if (changed) event.getPacket().getDataValueCollectionModifier().write(0, rewritten);
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
        if (meta == null || meta.displayName() == null) return null;
        String rgb = HEX.serialize(meta.displayName());
        if (!rgb.contains("\u00a7x")) return null;
        meta.displayName(Component.text(rgb));
        copy.setItemMeta(meta);
        return copy;
    }
}
