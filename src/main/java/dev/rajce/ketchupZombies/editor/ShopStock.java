package dev.rajce.ketchupZombies.editor;

import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.rajce.ketchupZombies.models.ArmorOffer;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ShopStock {

    private static final String[] SETS = {"leather", "gold", "iron"};
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

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
            gui.setItem(slot++, ItemBuilder.of(weapon.material()).name((on ? "&a" : "&7") + weapon.title()).lore("&7Price: &f" + stats.price, on ? "&aIn this shop." : "&7Not in this shop.", "&aLeft click: &7toggle.").build(), event -> {
                FinePoint current = point(session, SpotKind.WEAPONS);
                if (current == null) {
                    return;
                }
                List<WeaponId> next = session.data().customWeapons(current)
                        ? new ArrayList<>(session.data().weaponOffers(current))
                        : editor.plugin().getShopMenu().defaultWeapons();
                if (next.contains(chosen)) {
                    next.remove(chosen);
                } else {
                    next.add(chosen);
                }
                session.data().weaponOffers(current, next);
                editor.persist(session);
                openWeapons(player, session);
            });
        }
        gui.setItem(45, ItemBuilder.of(Material.ARROW).name("&7Back").build(), event -> editor.showSpots(player, SpotKind.WEAPONS));
        gui.setItem(49, ItemBuilder.of(Material.CHEST).name("&aAdd all shop weapons").lore("&7Every config weapon with a shop price.").build(), event -> {
            FinePoint current = point(session, SpotKind.WEAPONS);
            if (current != null) {
                session.data().weaponOffers(current, editor.plugin().getShopMenu().defaultWeapons());
                editor.persist(session);
            }
            openWeapons(player, session);
        });
        gui.setItem(53, ItemBuilder.of(Material.BARRIER).name("&eUse default stock").lore("&7Drops the custom list. Sells every priced weapon again.").build(), event -> {
            FinePoint current = point(session, SpotKind.WEAPONS);
            if (current != null) {
                session.data().defaultWeapons(current);
                editor.persist(session);
            }
            editor.info(player, "editor.weapons-default");
            openWeapons(player, session);
        });
        gui.open(player);
    }

    public void open(Player player, EditorSession session) {
        FinePoint point = point(session, SpotKind.ARMOR);
        if (point == null) {
            editor.showSpots(player, SpotKind.ARMOR);
            return;
        }
        Gui gui = new Gui(editor.plugin(), 6, "&8Shop armor");
        List<ArmorOffer> offers = session.data().customArmor(point)
                ? session.data().armorOffers(point)
                : editor.plugin().getShopMenu().defaults();
        int slot = 0;
        for (ArmorOffer offer : offers) {
            if (slot >= 45) {
                break;
            }
            int index = slot;
            gui.setItem(slot++, ItemBuilder.of(Material.IRON_CHESTPLATE).name("&e" + offer.label()).lore("&7Price: &f" + offer.price(), "&cRight click: &7remove from this shop.").build(), event -> {
                if (MenuClick.of(event) == MenuClick.RIGHT) {
                    remove(player, session, index);
                }
            });
        }
        gui.setItem(45, ItemBuilder.of(Material.ARROW).name("&7Back").lore("&aLeft click: &7go back.").build(), event -> editor.showSpots(player, SpotKind.ARMOR));
        gui.setItem(49, ItemBuilder.of(Material.ANVIL).name("&aAdd armor").lore("&7Pick a set, the pieces and a price.").build(), event -> sets(player, session));
        gui.setItem(53, ItemBuilder.of(Material.BARRIER).name("&eUse default prices").lore("&7Drops the custom list. The shop sells the config bundles again.").build(), event -> {
            FinePoint current = point(session, SpotKind.ARMOR);
            if (current != null) {
                session.data().defaultArmor(current);
                editor.persist(session);
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
        if (session.armorPieces().isEmpty()) {
            editor.later(player, "&cPick at least one armor piece.", () -> pieces(player, session));
            return;
        }
        FinePoint point = point(session, SpotKind.ARMOR);
        session.mode(EditMode.NONE);
        if (point == null) {
            editor.laterInfo(player, "editor.left-mode", null, () -> editor.showSpots(player, SpotKind.ARMOR));
            return;
        }
        List<ArmorOffer> offers = new ArrayList<>(session.data().customArmor(point) ? session.data().armorOffers(point) : editor.plugin().getShopMenu().defaults());
        offers.add(new ArmorOffer(session.armorSet(), session.armorPieces(), Integer.parseInt(text)));
        session.data().armorOffers(point, offers);
        editor.persist(session);
        editor.laterInfo(player, "editor.armor-set", java.util.Map.of("price", text), () -> open(player, session));
    }

    private void remove(Player player, EditorSession session, int index) {
        FinePoint point = point(session, SpotKind.ARMOR);
        if (point == null) {
            return;
        }
        List<ArmorOffer> offers = new ArrayList<>(session.data().customArmor(point) ? session.data().armorOffers(point) : editor.plugin().getShopMenu().defaults());
        if (index >= 0 && index < offers.size()) {
            offers.remove(index);
        }
        session.data().armorOffers(point, offers);
        editor.persist(session);
        open(player, session);
    }

    private void sets(Player player, EditorSession session) {
        Gui gui = new Gui(editor.plugin(), 1, "&8Armor set");
        int slot = 2;
        for (String set : SETS) {
            gui.setItem(slot, ItemBuilder.of(switch (set) {
                case "gold" -> Material.GOLDEN_CHESTPLATE;
                case "iron" -> Material.IRON_CHESTPLATE;
                default -> Material.LEATHER_CHESTPLATE;
            }).name("&e" + set).lore("&aLeft click: &7pick pieces.").build(), event -> {
                session.armorSet(set);
                pieces(player, session);
            });
            slot += 2;
        }
        gui.setItem(8, ItemBuilder.of(Material.ARROW).name("&7Back").build(), event -> open(player, session));
        gui.open(player);
    }

    private void pieces(Player player, EditorSession session) {
        Gui gui = new Gui(editor.plugin(), 3, "&8" + session.armorSet());
        int slot = 10;
        for (String piece : PIECES) {
            boolean on = session.armorPieces().contains(piece);
            gui.setItem(slot++, ItemBuilder.of(on ? Material.LIME_DYE : Material.GRAY_DYE).name((on ? "&a" : "&7") + piece).lore("&aLeft click: &7toggle.").build(), event -> {
                session.toggleArmorPiece(piece);
                pieces(player, session);
            });
        }
        gui.setItem(22, ItemBuilder.of(Material.GOLD_INGOT).name("&eSet price").lore("&7Selected: &f" + (session.armorPieces().isEmpty() ? "none" : String.join(", ", session.armorPieces())), "&aLeft click: &7type the price.").build(), event -> {
            if (session.armorPieces().isEmpty()) {
                editor.reply(player, "&cPick at least one armor piece.");
                return;
            }
            session.mode(EditMode.ARMOR_PRICE);
            session.screen(EditorSession.Screen.SPOT);
            player.closeInventory();
            editor.info(player, "editor.type-price");
        });
        gui.setItem(18, ItemBuilder.of(Material.ARROW).name("&7Back").build(), event -> sets(player, session));
        gui.open(player);
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
