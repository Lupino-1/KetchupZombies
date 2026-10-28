package dev.rajce.ketchupZombies.editor;

import dev.lupino1.gui.Gui;
import dev.lupino1.item.ItemBuilder;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaDoor;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.ArenaWindow;
import dev.rajce.ketchupZombies.models.BlockPoint;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.models.SpotKind;
import dev.rajce.ketchupZombies.models.UltimateMachine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ArenaMenus {

    private final Editor editor;

    public ArenaMenus(Editor editor) {
        this.editor = editor;
    }

    public void create(Player player, EditorSession session) {
        Gui gui = new Gui(editor.plugin(), 3, "&8Create arena");
        gui.setItem(11, valueButton(session.corner1() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Corner 1", format(session.corner1()), "Left-click a block in the world. That corner of the blueprint.", "Clear this corner."), event -> editor.clickCreateCorner(player, 1, MenuClick.of(event)));
        gui.setItem(13, valueButton(Material.NAME_TAG, "Name", session.createName() == null ? "Not set" : session.createName(), "Type the arena name in chat. Letters and numbers only, 1-32.", "Clear the name."), event -> editor.clickCreateName(player, MenuClick.of(event)));
        gui.setItem(15, valueButton(session.corner2() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Corner 2", format(session.corner2()), "Left-click the opposite corner of the blueprint.", "Clear this corner."), event -> editor.clickCreateCorner(player, 2, MenuClick.of(event)));
        boolean ready = session.createReady();
        gui.setItem(22, ItemBuilder.of(ready ? Material.EMERALD_BLOCK : Material.BARRIER)
                .name(ready ? "&aSave" : "&cSave")
                .lore("&7Writes the blueprint JSON and an empty arena file.", "&aLeft click: &7save when the name and both corners are set.")
                .build(), event -> {
            if (MenuClick.of(event) == MenuClick.LEFT) {
                editor.saveCreate(player);
            }
        });
        gui.open(player);
    }

    public void hub(Player player, EditorSession session) {
        ArenaData data = session.data();
        Gui gui = new Gui(editor.plugin(), 4, "&8Edit " + data.name());
        gui.setItem(10, hubButton(Material.BRICKS, "Sections", data.sections().size(), "Named areas of the map. Doors connect two sections."), event -> open(event, () -> editor.showSections(player)));
        gui.setItem(11, hubButton(Material.OAK_DOOR, "Doors", data.doors().size(), "A path between two sections. Bought doors remove their blocks."), event -> open(event, () -> editor.showDoors(player)));
        gui.setItem(12, hubButton(Material.GLASS_PANE, "Windows", data.windows().size(), "A flat 2x2 or 2x3 area. It rebuilds during the game."), event -> open(event, () -> editor.showWindows(player)));
        gui.setItem(13, hubButton(SpotKind.WEAPONS.icon(), "Weapon shops", data.spots(SpotKind.WEAPONS).size(), "Left-click a shop to pick which weapons it sells."), event -> open(event, () -> editor.showSpots(player, SpotKind.WEAPONS)));
        gui.setItem(21, hubButton(SpotKind.ARMOR.icon(), "Armor shops", data.spots(SpotKind.ARMOR).size(), "Wall set: helmet/chest/legs/boots + one price. Click buys all — no menu."), event -> open(event, () -> editor.showSpots(player, SpotKind.ARMOR)));
        gui.setItem(14, hubButton(SpotKind.LUCKY_CHESTS.icon(), "Lucky chests", data.spots(SpotKind.LUCKY_CHESTS).size(), "Left-click the block that becomes the chest. Text sits above it."), event -> open(event, () -> editor.showSpots(player, SpotKind.LUCKY_CHESTS)));
        gui.setItem(15, hubButton(Material.ANVIL, "Ultimate machines", data.machines().size(), "Like a door: set the blocks and one hologram. Click with a gun after power is on."), event -> open(event, () -> editor.showMachines(player)));
        gui.setItem(16, hubButton(SpotKind.PERKS.icon(), "Perks", data.spots(SpotKind.PERKS).size(), "Hologram coordinates. Perks work after power is on."), event -> open(event, () -> editor.showSpots(player, SpotKind.PERKS)));
        gui.setItem(22, hubButton(SpotKind.POWER.icon(), "Power switch", data.spots(SpotKind.POWER).size(), "One hologram for the whole arena. It turns power on."), event -> open(event, () -> editor.showSpots(player, SpotKind.POWER)));
        gui.setItem(24, ItemBuilder.of(data.spawn() == null ? Material.GRAY_BED : Material.RED_BED)
                .name("&eArena spawn")
                .lore(
                        "&7Current: &f" + point(data.spawn()),
                        "&7Players land here in lobby and at match start.",
                        "&aLeft click: &7stand where they should spawn and left-click.",
                        "&cRight click: &7clear (falls back to first section center)."
                ).build(), event -> editor.clickArenaSpawn(player, MenuClick.of(event)));
        gui.setItem(31, ItemBuilder.of(Material.BARRIER).name("&cClose").lore("&7Stops editing. Saved markers stay in the file.", "&aLeft click: &7close.").build(), event -> open(event, () -> editor.closeEdit(player)));
        gui.setItem(33, ItemBuilder.of(Material.TNT).name("&cDelete arena").lore("&7Deletes the arena file and the blueprint.", "&cRight click: &7delete. This cannot be undone.").build(), event -> editor.deleteArena(player, MenuClick.of(event)));
        gui.open(player);
    }

    public void sections(Player player, EditorSession session) {
        Gui gui = list("&8Sections", () -> editor.showHub(player));
        int slot = 0;
        for (Map.Entry<String, ArenaSection> entry : session.data().sections().entrySet()) {
            if (slot >= 45) {
                break;
            }
            String name = entry.getKey();
            gui.setItem(slot++, ItemBuilder.of(Material.BRICKS).name("&e" + name).lore("&7" + entry.getValue().format(), "&cRight click: &7delete this section, its doors and its windows.").build(), event -> editor.clickSection(player, name, MenuClick.of(event)));
        }
        gui.setItem(49, addButton("Add section", "Opens a form. Set the name and both corners, then save."), event -> open(event, () -> editor.beginSection(player)));
        gui.open(player);
    }

    public void sectionForm(Player player, EditorSession session) {
        Gui gui = new Gui(editor.plugin(), 3, "&8Section");
        gui.setItem(11, valueButton(session.sectionCorner1() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Corner 1", format(session.sectionCorner1()), "Left-click a block in the world.", "Clear this corner."), event -> editor.clickSectionCorner(player, 1, MenuClick.of(event)));
        gui.setItem(13, valueButton(Material.NAME_TAG, "Name", session.sectionName() == null ? "Not set" : session.sectionName(), "Type the section name in chat. Letters and numbers only.", "Clear the name."), event -> editor.clickSectionName(player, MenuClick.of(event)));
        gui.setItem(15, valueButton(session.sectionCorner2() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Corner 2", format(session.sectionCorner2()), "Left-click the opposite corner.", "Clear this corner."), event -> editor.clickSectionCorner(player, 2, MenuClick.of(event)));
        boolean ready = session.sectionName() != null && session.sectionCorner1() != null && session.sectionCorner2() != null;
        gui.setItem(22, ItemBuilder.of(ready ? Material.EMERALD_BLOCK : Material.BARRIER).name(ready ? "&aSave" : "&cSave").lore("&7Saves the section into the arena file.", "&aLeft click: &7save when the name and both corners are set.").build(), event -> open(event, () -> editor.saveSection(player)));
        gui.setItem(26, backButton(), event -> open(event, () -> editor.showSections(player)));
        gui.open(player);
    }

    public void doors(Player player, EditorSession session) {
        Gui gui = list("&8Doors", () -> editor.showHub(player));
        int slot = 0;
        for (ArenaDoor door : session.data().doors().values()) {
            if (slot >= 45) {
                break;
            }
            String key = door.key();
            gui.setItem(slot++, ItemBuilder.of(Material.OAK_DOOR).name("&e" + door.format()).lore(
                    "&7Blocks: &f" + door.blocks().size(),
                    "&7Price: &f" + price(door.price()),
                    "&7" + door.from() + " -> " + door.to() + ": &f" + point(door.hologramA()),
                    "&7" + door.to() + " -> " + door.from() + ": &f" + point(door.hologramB()),
                    "&aLeft click: &7edit blocks, price and holograms.",
                    "&cRight click: &7delete this door."
            ).build(), event -> editor.clickDoor(player, key, MenuClick.of(event)));
        }
        gui.setItem(49, addButton("Add door", "Pick two sections, then create the door."), event -> open(event, () -> editor.beginDoor(player)));
        gui.open(player);
    }

    public void doorPick(Player player, EditorSession session) {
        Gui gui = list("&8Pick two sections", () -> editor.showDoors(player));
        int slot = 0;
        for (String name : session.data().sections().keySet()) {
            if (slot >= 45) {
                break;
            }
            boolean selected = session.selected(name);
            gui.setItem(slot++, ItemBuilder.of(selected ? Material.LIME_CONCRETE : Material.BRICKS)
                    .name((selected ? "&a" : "&e") + name)
                    .lore(selected ? "&aSelected. &7Left click: unselect." : "&7Left click: select. Only two sections can be selected.")
                    .build(), event -> open(event, () -> editor.toggleDoorSection(player, name)));
        }
        boolean ready = session.selected().size() == 2;
        gui.setItem(49, ItemBuilder.of(ready ? Material.EMERALD_BLOCK : Material.BARRIER)
                .name(ready ? "&aCreate door" : "&cCreate door")
                .lore("&7Creates a door between the two lime sections.", "&aLeft click: &7create when exactly two sections are selected.")
                .build(), event -> open(event, () -> editor.createDoor(player)));
        gui.open(player);
    }

    public void doorEdit(Player player, EditorSession session) {
        ArenaDoor door = session.data().doors().get(session.doorKey());
        Gui gui = new Gui(editor.plugin(), 3, "&8Door");
        if (door == null) {
            gui.setItem(13, ItemBuilder.of(Material.BARRIER).name("&cDoor missing").lore("&7It was deleted.").build(), event -> open(event, () -> editor.showDoors(player)));
            gui.open(player);
            return;
        }
        gui.setItem(10, ItemBuilder.of(Material.OAK_DOOR).name("&eDoor blocks").lore("&7Current: &f" + door.blocks().size(), "&7Blocks you place here disappear after the door is bought.", "&aLeft click: &7place blocks in the world. Type leave to stop.", "&cRight click: &7clear every stored door block.").build(), event -> editor.clickDoorBlocks(player, MenuClick.of(event)));
        gui.setItem(12, ItemBuilder.of(door.price() == null ? Material.GRAY_CONCRETE : Material.GOLD_INGOT).name("&ePrice").lore("&7Current: &f" + price(door.price()), "&7One price for both sides.", "&aLeft click: &7type a whole number in chat.", "&cRight click: &7clear the price.").build(), event -> editor.clickDoorPrice(player, MenuClick.of(event)));
        gui.setItem(14, ItemBuilder.of(door.hologramA() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE)
                .name("&e" + door.from() + " -> " + door.to())
                .lore(
                        "&7Current: &f" + point(door.hologramA()),
                        "&7Place in &f" + door.from() + "&7. Text %section% = &f" + door.to() + "&7.",
                        "&aLeft click: &7stand on the spot and left-click.",
                        "&cRight click: &7clear this hologram."
                ).build(), event -> editor.clickDoorHologram(player, 0, MenuClick.of(event)));
        gui.setItem(16, ItemBuilder.of(door.hologramB() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE)
                .name("&e" + door.to() + " -> " + door.from())
                .lore(
                        "&7Current: &f" + point(door.hologramB()),
                        "&7Place in &f" + door.to() + "&7. Text %section% = &f" + door.from() + "&7.",
                        "&aLeft click: &7stand on the spot and left-click.",
                        "&cRight click: &7clear this hologram."
                ).build(), event -> editor.clickDoorHologram(player, 1, MenuClick.of(event)));
        gui.setItem(22, backButton(), event -> open(event, () -> editor.showDoors(player)));
        gui.open(player);
    }

    public void machines(Player player, EditorSession session) {
        Gui gui = list("&8Ultimate machines", () -> editor.showHub(player));
        int slot = 0;
        for (UltimateMachine machine : session.data().machines().values()) {
            if (slot >= 45) {
                break;
            }
            String id = machine.id();
            gui.setItem(slot++, ItemBuilder.of(Material.ANVIL).name("&e" + id).lore(
                    "&7Blocks: &f" + machine.blocks().size(),
                    "&7Hologram: &f" + point(machine.hologram()),
                    "&aLeft click: &7edit.",
                    "&cRight click: &7delete."
            ).build(), event -> editor.clickMachine(player, id, MenuClick.of(event)));
        }
        gui.setItem(49, addButton("Add machine", "Creates another ultimate station. Then set its blocks and hologram."), event -> open(event, () -> editor.beginMachine(player)));
        gui.open(player);
    }

    public void machineEdit(Player player, EditorSession session) {
        UltimateMachine machine = session.data().machines().get(session.machineId());
        Gui gui = new Gui(editor.plugin(), 3, "&8Ultimate machine");
        if (machine == null) {
            gui.setItem(13, ItemBuilder.of(Material.BARRIER).name("&cMachine missing").lore("&7It was deleted.").build(), event -> open(event, () -> editor.showMachines(player)));
            gui.open(player);
            return;
        }
        gui.setItem(11, ItemBuilder.of(Material.ANVIL).name("&eMachine blocks").lore(
                "&7Current: &f" + machine.blocks().size(),
                "&7Blocks you place here are the machine.",
                "&aLeft click: &7place blocks in the world. Type leave to stop.",
                "&cRight click: &7clear every stored block."
        ).build(), event -> editor.clickMachineBlocks(player, MenuClick.of(event)));
        gui.setItem(15, ItemBuilder.of(machine.hologram() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE).name("&eHologram").lore(
                "&7Current: &f" + point(machine.hologram()),
                "&aLeft click: &7stand on the spot and left-click.",
                "&cRight click: &7clear this hologram."
        ).build(), event -> editor.clickMachineHologram(player, MenuClick.of(event)));
        gui.setItem(22, backButton(), event -> open(event, () -> editor.showMachines(player)));
        gui.open(player);
    }

    public void windows(Player player, EditorSession session) {
        Gui gui = list("&8Windows", () -> editor.showHub(player));
        int index = 0;
        for (ArenaWindow window : session.data().windows()) {
            if (index >= 45) {
                break;
            }
            int slot = index;
            String area = window.faceMin() == null || window.faceMax() == null ? "Area not set" : window.faceMin().format() + " -> " + window.faceMax().format();
            gui.setItem(index++, ItemBuilder.of(Material.GLASS_PANE).name("&e" + window.format()).lore("&7" + area, "&7Blocks: &f" + window.blocks().size(), "&aLeft click: &7open settings.", "&cRight click: &7delete this window.").build(), event -> editor.clickWindow(player, slot, MenuClick.of(event)));
        }
        gui.setItem(49, addButton("Add window", "Creates an empty window. Left-click it to set both corners and the zombie spawn."), event -> open(event, () -> editor.beginWindow(player)));
        gui.open(player);
    }

    public void windowForm(Player player, EditorSession session) {
        ArenaWindow window = window(session);
        Gui gui = new Gui(editor.plugin(), 4, "&8Window settings");
        if (window == null) {
            gui.setItem(13, ItemBuilder.of(Material.BARRIER).name("&cWindow missing").lore("&7It was deleted.").build(), event -> open(event, () -> editor.showWindows(player)));
            gui.open(player);
            return;
        }
        gui.setItem(10, valueButton(Material.BRICKS, "Section", window.section() == null ? "Not set" : window.section(), "Open the list and pick a section.", "Clear the section."), event -> editor.clickWindowSection(player, MenuClick.of(event)));
        gui.setItem(12, valueButton(session.windowCorner1() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Corner 1", format(session.windowCorner1()), "Left-click one corner of the window area.", "Clear this corner."), event -> editor.clickWindowCorner(player, 1, MenuClick.of(event)));
        gui.setItem(14, valueButton(session.windowCorner2() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Corner 2", format(session.windowCorner2()), "Left-click the opposite corner. The area must be a flat 2x2 or 2x3.", "Clear this corner."), event -> editor.clickWindowCorner(player, 2, MenuClick.of(event)));
        gui.setItem(16, valueButton(window.spawn() == null ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE, "Spawn point", point(window.spawn()), "Stand where zombies should spawn and left-click.", "Clear the spawn."), event -> editor.clickWindowSpawn(player, MenuClick.of(event)));
        gui.setItem(19, valueButton(window.repair1() == null ? Material.GRAY_CONCRETE : Material.ORANGE_CONCRETE, "Repair corner 1", block(window.repair1()), "Left-click one corner of the sneak-to-repair zone (block coords).", "Clear this corner."), event -> editor.clickWindowRepair(player, 1, MenuClick.of(event)));
        gui.setItem(21, valueButton(window.repair2() == null ? Material.GRAY_CONCRETE : Material.ORANGE_CONCRETE, "Repair corner 2", block(window.repair2()), "Left-click the opposite corner. Without both corners repair is disabled.", "Clear this corner."), event -> editor.clickWindowRepair(player, 2, MenuClick.of(event)));
        gui.setItem(31, backButton(), event -> open(event, () -> editor.showWindows(player)));
        gui.open(player);
    }

    private static ArenaWindow window(EditorSession session) {
        int index = session.windowIndex();
        if (session.data() == null || index < 0 || index >= session.data().windows().size()) {
            return null;
        }
        return session.data().windows().get(index);
    }

    public void pickWindowSection(Player player, EditorSession session) {
        Gui gui = list("&8Window section", () -> editor.showWindowForm(player));
        int slot = 0;
        for (String name : session.data().sections().keySet()) {
            if (slot >= 45) {
                break;
            }
            gui.setItem(slot++, ItemBuilder.of(Material.BRICKS).name("&e" + name).lore("&aLeft click: &7use this section.").build(), event -> open(event, () -> editor.pickWindowSection(player, name)));
        }
        gui.open(player);
    }

    public void spots(Player player, EditorSession session, SpotKind kind) {
        Gui gui = list("&8" + kind.title(), () -> editor.showHub(player));
        int index = 0;
        for (FinePoint point : session.data().spots(kind)) {
            if (index >= 45) {
                break;
            }
            int slot = index;
            gui.setItem(index++, ItemBuilder.of(kind.icon()).name("&e" + point.format()).lore(kind == SpotKind.ARMOR
                    ? new String[]{"&7Hologram coordinate. The block itself is not used.", "&aLeft click: &7set the wall armor pieces + price.", "&cRight click: &7remove this hologram."}
                    : kind == SpotKind.WEAPONS
                    ? new String[]{"&7Hologram coordinate. The block itself is not used.", "&aLeft click: &7pick weapons for this shop.", "&cRight click: &7remove this hologram."}
                    : new String[]{"&7Hologram coordinate. The block itself is not used.", "&cRight click: &7remove this hologram."}).build(), event -> editor.clickSpot(player, kind, slot, MenuClick.of(event)));
        }
        String add = kind == SpotKind.POWER ? "Set power hologram" : "Add hologram";
        String detail = kind == SpotKind.POWER
                ? "Stand on the spot and left-click. Your position replaces the power hologram."
                : "Stand on the spot and left-click. Your position is saved, not the block you hit.";
        gui.setItem(49, addButton(add, detail), event -> open(event, () -> editor.beginSpot(player, kind)));
        gui.open(player);
    }

    private Gui list(String title, Runnable back) {
        Gui gui = new Gui(editor.plugin(), 6, title);
        gui.setItem(45, backButton(), event -> open(event, back));
        return gui;
    }

    private static void open(InventoryClickEvent event, Runnable action) {
        if (MenuClick.of(event) == MenuClick.LEFT) {
            action.run();
        }
    }

    private static ItemStack valueButton(Material material, String title, String current, String left, String right) {
        return ItemBuilder.of(material).name("&e" + title).lore("&7Current: &f" + current, "&aLeft click: &7" + left, "&cRight click: &7" + right).build();
    }

    private static ItemStack hubButton(Material material, String title, int count, String detail) {
        return ItemBuilder.of(material).name("&e" + title).lore("&7Count: &f" + count, "&7" + detail, "&aLeft click: &7open.").build();
    }

    private static ItemStack addButton(String name, String detail) {
        return ItemBuilder.of(Material.EMERALD).name("&a" + name).lore("&7" + detail, "&aLeft click: &7start.").build();
    }

    private static ItemStack backButton() {
        return ItemBuilder.of(Material.ARROW).name("&7Back").lore("&aLeft click: &7go back.").build();
    }

    private static String format(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Not set";
        }
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private static String point(FinePoint point) {
        return point == null ? "Not set" : point.format();
    }

    private static String block(BlockPoint point) {
        return point == null ? "Not set" : point.format();
    }

    private static String price(Integer price) {
        return price == null ? "Not set" : Integer.toString(price);
    }
}
