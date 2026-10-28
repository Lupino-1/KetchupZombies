package dev.rajce.ketchupZombies.editor;

import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.weapons.WeaponCatalog;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShopStock {

    private static final String[] SETS = {"leather", "gold", "iron", "diamond", "netherite"};

    private final Editor editor;

    public ShopStock(Editor editor) {
        this.editor = editor;
    }

    public void openWeapons(Player player, EditorSession session) {
        FinePoint point = point(session, SpotKind.WEAPONS);
        if (point == null) {
            editor.showSpots(player, SpotKind.WEAPONS);
            return;
        }
        List<WeaponId> stock = session.data().customWeapons(point)
                ? new ArrayList<>(session.data().weaponOffers(point))
                : editor.plugin().getShopMenu().defaultWeapons();
        Gui gui = new Gui(editor.plugin(), 6, "&8Shop weapons");
        int slot = 0;
        for (WeaponId weapon : WeaponId.values()) {
            var stats = switch (weapon) {
                case KNIFE -> editor.plugin().config().weapons.knife;
                case PISTOL -> editor.plugin().config().weapons.pistol;
                case RIFLE -> editor.plugin().config().weapons.rifle;
                case SHOTGUN -> editor.plugin().config().weapons.shotgun;
                case SNIPER -> editor.plugin().config().weapons.sniper;
                case ROCKET -> editor.plugin().config().weapons.rocketLauncher;
                case FLAMETHROWER -> editor.plugin().config().weapons.flamethrower;
                case DART -> editor.plugin().config().weapons.blowDart;
                case SOAKER -> editor.plugin().config().weapons.zombieSoaker;
                case ZAPPER -> editor.plugin().config().weapons.zombieZapper;
                case PUNCHER -> editor.plugin().config().weapons.puncher;
                case ELDER -> editor.plugin().config().weapons.elderGun;
                case DIGGER -> editor.plugin().config().weapons.goldDigger;
            };
            if (stats.price < 0 || slot >= 45) {
                continue;
            }
            boolean on = stock.contains(weapon);
            WeaponId chosen = weapon;
            String label = WeaponCatalog.displayName(editor.plugin(), weapon, 0);
            ItemStack icon = WeaponCatalog.stack(editor.plugin(), weapon, 0);
            gui.setItem(slot++, ItemBuilder.of(icon).name((on ? "&a" : "&7") + label).lore("&7Price: &f" + stats.price, on ? "&aWall gun for this shop." : "&7Not selected.", "&aLeft click: &7set as the only wall gun.").build(), event -> {
                FinePoint current = point(session, SpotKind.WEAPONS);
                if (current == null) {
                    return;
                }
                session.data().weaponOffers(current, List.of(chosen));
                editor.persist(session);
                editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.WEAPONS, current);
                openWeapons(player, session);
            });
        }
        gui.setItem(45, ItemBuilder.of(Material.ARROW).name("&7Back").build(), event -> editor.showSpots(player, SpotKind.WEAPONS));
        gui.setItem(49, ItemBuilder.of(Material.CHEST).name("&eMulti shop (GUI)").lore("&7Sells every priced weapon in a menu.", "&7Hypixel wall guns use one weapon instead.").build(), event -> {
            FinePoint current = point(session, SpotKind.WEAPONS);
            if (current != null) {
                session.data().weaponOffers(current, editor.plugin().getShopMenu().defaultWeapons());
                editor.persist(session);
                editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.WEAPONS, current);
            }
            openWeapons(player, session);
        });
        gui.setItem(53, ItemBuilder.of(Material.BARRIER).name("&eUse default stock").lore("&7Drops the custom list. Opens the multi-weapon GUI again.").build(), event -> {
            FinePoint current = point(session, SpotKind.WEAPONS);
            if (current != null) {
                session.data().defaultWeapons(current);
                editor.persist(session);
                editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.WEAPONS, current);
            }
            editor.info(player, "editor.weapons-default");
            openWeapons(player, session);
        });
        gui.open(player);
    }

    /** One wall armor set: helmet / chest / legs / boots + one price. No in-game GUI. */
    public void open(Player player, EditorSession session) {
        FinePoint point = point(session, SpotKind.ARMOR);
        if (point == null) {
            editor.showSpots(player, SpotKind.ARMOR);
            return;
        }
        ArmorOffer offer = offer(session, point);
        Gui gui = new Gui(editor.plugin(), 3, "&8Wall armor");
        gui.setItem(10, slotButton(offer, "helmet", Material.LEATHER_HELMET), event -> {
            if (MenuClick.of(event) == MenuClick.RIGHT) {
                clearSlot(player, session, "helmet");
            } else if (MenuClick.of(event) == MenuClick.LEFT) {
                pickSet(player, session, "helmet");
            }
        });
        gui.setItem(12, slotButton(offer, "chestplate", Material.LEATHER_CHESTPLATE), event -> {
            if (MenuClick.of(event) == MenuClick.RIGHT) {
                clearSlot(player, session, "chestplate");
            } else if (MenuClick.of(event) == MenuClick.LEFT) {
                pickSet(player, session, "chestplate");
            }
        });
        gui.setItem(14, slotButton(offer, "leggings", Material.LEATHER_LEGGINGS), event -> {
            if (MenuClick.of(event) == MenuClick.RIGHT) {
                clearSlot(player, session, "leggings");
            } else if (MenuClick.of(event) == MenuClick.LEFT) {
                pickSet(player, session, "leggings");
            }
        });
        gui.setItem(16, slotButton(offer, "boots", Material.LEATHER_BOOTS), event -> {
            if (MenuClick.of(event) == MenuClick.RIGHT) {
                clearSlot(player, session, "boots");
            } else if (MenuClick.of(event) == MenuClick.LEFT) {
                pickSet(player, session, "boots");
            }
        });
        gui.setItem(22, ItemBuilder.of(Material.GOLD_INGOT).name("&ePrice").lore("&7Current: &f" + offer.price(), "&7One price for the whole set on click.", "&aLeft click: &7type a whole number.").build(), event -> {
            if (MenuClick.of(event) != MenuClick.LEFT) {
                return;
            }
            session.mode(EditMode.ARMOR_PRICE);
            session.screen(EditorSession.Screen.SPOT);
            player.closeInventory();
            editor.info(player, "editor.type-price");
        });
        gui.setItem(18, ItemBuilder.of(Material.ARROW).name("&7Back").lore("&aLeft click: &7go back.").build(), event -> editor.showSpots(player, SpotKind.ARMOR));
        gui.setItem(26, ItemBuilder.of(Material.BARRIER).name("&eClear shop").lore("&7Removes the custom set from this hologram.").build(), event -> {
            FinePoint current = point(session, SpotKind.ARMOR);
            if (current != null) {
                session.data().defaultArmor(current);
                editor.persist(session);
                editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.ARMOR, current);
            }
            editor.info(player, "editor.armor-default");
            open(player, session);
        });
        gui.open(player);
    }

    public void onPrice(Player player, EditorSession session, String text) {
        if (!text.matches("[0-9]{1,9}")) {
            editor.later(player, "&cType a whole number, or &fleave&c.", null);
            return;
        }
        FinePoint point = point(session, SpotKind.ARMOR);
        session.mode(EditMode.NONE);
        if (point == null) {
            editor.laterInfo(player, "editor.left-mode", null, () -> editor.showSpots(player, SpotKind.ARMOR));
            return;
        }
        ArmorOffer offer = offer(session, point).withPrice(Integer.parseInt(text));
        session.data().armorOffers(point, List.of(offer));
        editor.persist(session);
        editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.ARMOR, point);
        editor.laterInfo(player, "editor.armor-set", java.util.Map.of("price", text), () -> open(player, session));
    }

    private void clearSlot(Player player, EditorSession session, String slot) {
        FinePoint point = point(session, SpotKind.ARMOR);
        if (point == null) {
            return;
        }
        ArmorOffer offer = offer(session, point).withSlot(slot, null);
        if (offer.empty()) {
            session.data().defaultArmor(point);
        } else {
            session.data().armorOffers(point, List.of(offer));
        }
        editor.persist(session);
        editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.ARMOR, point);
        open(player, session);
    }

    private void pickSet(Player player, EditorSession session, String piece) {
        Gui gui = new Gui(editor.plugin(), 1, "&8" + piece);
        int slot = 0;
        for (String set : SETS) {
            Material icon = icon(set, piece);
            String chosen = set;
            gui.setItem(slot++, ItemBuilder.of(icon).name("&e" + set + " " + piece).lore("&aLeft click: &7put on this shop (one price for the whole set).").build(), event -> {
                FinePoint point = point(session, SpotKind.ARMOR);
                if (point == null) {
                    return;
                }
                ArmorOffer offer = offer(session, point).withSlot(piece, chosen);
                session.data().armorOffers(point, List.of(offer));
                editor.persist(session);
                editor.plugin().getHolograms().showSpotAll(session.data(), SpotKind.ARMOR, point);
                open(player, session);
            });
        }
        gui.setItem(8, ItemBuilder.of(Material.ARROW).name("&7Back").build(), event -> open(player, session));
        gui.open(player);
    }

    private static ItemStack slotButton(ArmorOffer offer, String slot, Material empty) {
        String set = offer.set(slot);
        if (set == null) {
            return ItemBuilder.of(empty).name("&7" + slot).lore("&7Empty — only one of each slot.", "&aLeft click: &7pick material.", "&cRight click: &7clear.").build();
        }
        Material material = icon(set, slot);
        return ItemBuilder.of(material).name("&a" + set + " " + slot).lore("&7Part of the wall set (price &f" + offer.price() + "&7).", "&aLeft click: &7change material.", "&cRight click: &7clear.").build();
    }

    private static Material icon(String set, String piece) {
        return switch (set + "|" + piece) {
            case "leather|helmet" -> Material.LEATHER_HELMET;
            case "gold|helmet" -> Material.GOLDEN_HELMET;
            case "iron|helmet" -> Material.IRON_HELMET;
            case "diamond|helmet" -> Material.DIAMOND_HELMET;
            case "netherite|helmet" -> Material.NETHERITE_HELMET;
            case "leather|chestplate" -> Material.LEATHER_CHESTPLATE;
            case "gold|chestplate" -> Material.GOLDEN_CHESTPLATE;
            case "iron|chestplate" -> Material.IRON_CHESTPLATE;
            case "diamond|chestplate" -> Material.DIAMOND_CHESTPLATE;
            case "netherite|chestplate" -> Material.NETHERITE_CHESTPLATE;
            case "leather|leggings" -> Material.LEATHER_LEGGINGS;
            case "gold|leggings" -> Material.GOLDEN_LEGGINGS;
            case "iron|leggings" -> Material.IRON_LEGGINGS;
            case "diamond|leggings" -> Material.DIAMOND_LEGGINGS;
            case "netherite|leggings" -> Material.NETHERITE_LEGGINGS;
            case "leather|boots" -> Material.LEATHER_BOOTS;
            case "gold|boots" -> Material.GOLDEN_BOOTS;
            case "iron|boots" -> Material.IRON_BOOTS;
            case "diamond|boots" -> Material.DIAMOND_BOOTS;
            case "netherite|boots" -> Material.NETHERITE_BOOTS;
            default -> Material.LEATHER_CHESTPLATE;
        };
    }

    private static ArmorOffer offer(EditorSession session, FinePoint point) {
        if (session.data().customArmor(point) && !session.data().armorOffers(point).isEmpty()) {
            return session.data().armorOffers(point).get(0);
        }
        return new ArmorOffer(Map.of(), 0);
    }

    private static FinePoint point(EditorSession session, SpotKind kind) {
        if (session.data() == null) {
            return null;
        }
        List<FinePoint> points = session.data().spots(kind);
        int index = session.spotIndex();
        if (index < 0 || index >= points.size()) {
            return null;
        }
        return points.get(index);
    }
}
