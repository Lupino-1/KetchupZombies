package dev.rajce.ketchupZombies.shop;

import dev.lupino1.config.ConfigHolder;
import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.PluginConfig;
import dev.rajce.ketchupZombies.shop.gui.GuiItemConfig;
import dev.rajce.ketchupZombies.shop.gui.MenuGuiConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Player perk chest. Layout and border live in {@code gui/perks.yml}.
 */
public final class PerkMenu {

    private final KetchupZombies plugin;
    private final ConfigHolder<MenuGuiConfig> holder;

    public PerkMenu(KetchupZombies plugin) {
        this.plugin = plugin;
        this.holder = ConfigHolder.create(plugin, MenuGuiConfig.class, "gui/perks.yml");
    }

    public void open(Player player) {
        MenuGuiConfig config = holder.get();
        int rows = Math.max(1, Math.min(6, config.rows));
        Gui gui = new Gui(plugin, rows, config.title == null ? "&8Perks" : config.title);
        if (config.items != null) {
            for (GuiItemConfig item : config.items.values()) {
                if (item == null) {
                    continue;
                }
                boolean owned = owned(player, item);
                ItemStack stack = stack(item, owned);
                for (int slot : item.parsedSlots()) {
                    if (slot < 0 || slot >= rows * 9) {
                        continue;
                    }
                    gui.setItem(slot, stack, event -> {
                        List<String> actions = event.isRightClick() ? item.rightClickActions : item.leftClickActions;
                        run(player, item, actions);
                    });
                }
            }
        }
        gui.open(player);
    }

    private void run(Player player, GuiItemConfig item, List<String> actions) {
        if (actions == null) {
            return;
        }
        for (String action : actions) {
            if (action == null || action.isBlank() || action.startsWith("state:")) {
                continue;
            }
            int split = action.indexOf(':');
            if (split < 0) {
                continue;
            }
            String kind = action.substring(0, split);
            String id = action.substring(split + 1);
            int price = price(id);
            String title = plain(item.name);
            if ("buy".equals(kind)) {
                plugin.getShopMenu().buyPerk(player, id, title, price);
            } else if ("sell".equals(kind)) {
                plugin.getShopMenu().sellPerk(player, id, title, price);
            }
        }
    }

    private boolean owned(Player player, GuiItemConfig item) {
        String id = actionId(item);
        if (id == null) {
            return false;
        }
        if ("power".equals(id)) {
            return plugin.getProfiles().power(player.getUniqueId());
        }
        return plugin.getProfiles().perk(player.getUniqueId(), id);
    }

    private static String actionId(GuiItemConfig item) {
        String raw = first(item.leftClickActions);
        if (raw == null) {
            raw = first(item.rightClickActions);
        }
        if (raw == null) {
            return null;
        }
        int split = raw.indexOf(':');
        return split < 0 ? null : raw.substring(split + 1);
    }

    private static String first(List<String> actions) {
        if (actions == null) {
            return null;
        }
        for (String action : actions) {
            if (action != null && !action.isBlank()) {
                return action;
            }
        }
        return null;
    }

    private ItemStack stack(GuiItemConfig item, boolean owned) {
        Material material = Material.matchMaterial(item.material == null ? "STONE" : item.material);
        if (material == null) {
            material = Material.STONE;
        }
        List<String> source = owned && item.ownedLore != null && !item.ownedLore.isEmpty() ? item.ownedLore : item.lore;
        List<String> lore = new ArrayList<>();
        if (source != null) {
            for (String line : source) {
                lore.add(fill(line, price(actionId(item))));
            }
        }
        ItemBuilder builder = ItemBuilder.of(material).name(fill(item.name == null ? " " : item.name, price(actionId(item))));
        if (!lore.isEmpty()) {
            builder.lore(lore.toArray(String[]::new));
        }
        return builder.build();
    }

    private int price(String id) {
        if (id == null) {
            return 0;
        }
        PluginConfig.Perks perks = plugin.config().perks;
        return switch (id) {
            case Profiles.FAST_REVIVE -> perks.fastRevive;
            case Profiles.SPEED -> perks.speed;
            case Profiles.QUICK_FIRE -> perks.quickFire;
            case Profiles.EXTRA_HEALTH -> perks.extraHealth;
            case Profiles.EXTRA_WEAPON -> perks.extraWeapon;
            case Profiles.FROZEN_BULLETS -> perks.frozenBullets;
            case Profiles.FLAME_BULLETS -> perks.flameBullets;
            default -> 0;
        };
    }

    private static String fill(String raw, int price) {
        if (raw == null) {
            return " ";
        }
        return raw.replace("%price%", Integer.toString(price));
    }

    private static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?i)&[0-9a-fk-or]", "").trim();
    }
}
