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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlueprintTest {

    public static final int SPAN_XZ = 156;
    public static final int SPAN_Y = 32;

    private final KetchupZombies plugin;
    private final BlueprintManager blueprints;
    private final ConcurrentHashMap<UUID, Blueprint> copies = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> copying = ConcurrentHashMap.newKeySet();

    public BlueprintTest(KetchupZombies plugin, BlueprintManager blueprints) {
        this.plugin = plugin;
        this.blueprints = blueprints;
    }

    public void open(Player player) {
        Gui gui = new Gui(plugin, 1, "&8Blueprint test");
        gui.setItem(2, ItemBuilder.of(Material.PAPER).name("&eCopy").lore("&7Copies a 156x32x156 area around you into memory.", "&aLeft click: &7copy.").build(), event -> {
            if (MenuClick.of(event) == MenuClick.LEFT) {
                copy(player);
            }
        });
        gui.setItem(6, ItemBuilder.of(Material.SLIME_BLOCK).name("&aPaste").lore("&7Places the copy with its minimum corner at your feet.", "&7Spots that were empty become air.", "&aLeft click: &7paste.").build(), event -> {
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
        int half = SPAN_XZ / 2;
        Location first = new Location(world, x - half, y, z - half);
        Location second = new Location(world, x - half + SPAN_XZ - 1, y + SPAN_Y - 1, z - half + SPAN_XZ - 1);
        reply(player, "&7Copying...");
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
            reply(player, "&aCopied &f" + captured.getBlocks().size() + "&a blocks.");
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
        reply(player, "&7Pasting...");
        blueprints.paste(blueprint, player.getLocation().getBlock().getLocation(), () -> FoliaManager.runAtEntity(player, () -> {
            if (player.isOnline()) {
                reply(player, "&aPasted. Empty spots are air.");
            }
        }));
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }
}
