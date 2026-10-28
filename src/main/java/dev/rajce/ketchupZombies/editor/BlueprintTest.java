package dev.rajce.ketchupZombies.editor;

import dev.lupino1.folia.FoliaManager;
import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.managers.BlueprintManager;
import dev.rajce.ketchupZombies.models.Blueprint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlueprintTest {

    /** Blocks from the player in each direction (X/Y/Z). */
    public static final int RADIUS = 8;

    private final KetchupZombies plugin;
    private final BlueprintManager blueprints;
    private final ConcurrentHashMap<UUID, Blueprint> copies = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> copying = ConcurrentHashMap.newKeySet();

    public BlueprintTest(KetchupZombies plugin, BlueprintManager blueprints) {
        this.plugin = plugin;
        this.blueprints = blueprints;
    }

    public void open(Player player) {
        int size = RADIUS * 2 + 1;
        Gui gui = new Gui(plugin, 1, "&8Blueprint test");
        gui.setItem(2, ItemBuilder.of(Material.PAPER).name("&eCopy").lore(
                "&7Copies a " + size + "x" + size + "x" + size + " area around you into memory.",
                "&7(" + RADIUS + " blocks each side, including down).",
                "&aLeft click: &7copy."
        ).build(), event -> {
            if (MenuClick.of(event) == MenuClick.LEFT) {
                copy(player);
            }
        });
        gui.setItem(6, ItemBuilder.of(Material.SLIME_BLOCK).name("&aPaste").lore(
                "&7Places the copy with its minimum corner at your feet.",
                "&7Spots that were empty become air.",
                "&7Player heads keep custom textures.",
                "&aLeft click: &7paste."
        ).build(), event -> {
            if (MenuClick.of(event) == MenuClick.LEFT) {
                paste(player);
            }
        });
        gui.open(player);
    }

    private void copy(Player player) {
        if (!copying.add(player.getUniqueId())) {
            reply(player, "&cCopy already running.");
            return;
        }
        World world = player.getWorld();
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        Location first = new Location(world, x - RADIUS, y - RADIUS, z - RADIUS);
        Location second = new Location(world, x + RADIUS, y + RADIUS, z + RADIUS);
        plugin.getMessageManager().send(player, "test.copying");
        blueprints.capture(new Blueprint(first, second)).whenComplete((captured, error) -> FoliaManager.runAtEntity(player, () -> {
            copying.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (error != null || captured == null) {
                plugin.getLogger().warning("Blueprint copy: " + (error == null ? "empty" : error.getMessage()));
                reply(player, "&cCopy failed. Check console.");
                return;
            }
            copies.put(player.getUniqueId(), captured);
            plugin.getMessageManager().send(player, "test.copied", Map.of("count", Integer.toString(captured.getBlocks().size())));
        }));
    }

    private void paste(Player player) {
        if (copying.contains(player.getUniqueId())) {
            reply(player, "&cCopy is still running.");
            return;
        }
        Blueprint blueprint = copies.get(player.getUniqueId());
        if (blueprint == null) {
            reply(player, "&cCopy a blueprint first.");
            return;
        }
        plugin.getMessageManager().send(player, "test.pasting");
        blueprints.paste(blueprint, player.getLocation().getBlock().getLocation(), () -> FoliaManager.runAtEntity(player, () -> {
            if (player.isOnline()) {
                plugin.getMessageManager().send(player, "test.pasted");
            }
        }));
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }
}
