package dev.rajce.ketchupZombies.managers;

import dev.lupino1.command.LPCommand;
import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.db.SqlDatabase;
import dev.rajce.ketchupZombies.editor.BlueprintTest;
import dev.rajce.ketchupZombies.editor.Editor;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.Arrays;
import java.util.Map;

public class CommandManager {

    private final KetchupZombies plugin;
    private final Editor editor;
    private final BlueprintTest test;
    private final ArenaCopies copies;

    public CommandManager(KetchupZombies plugin, Editor editor, BlueprintTest test, ArenaCopies copies) {
        this.plugin = plugin;
        this.editor = editor;
        this.test = test;
        this.copies = copies;
    }

    public void register() {
        LPCommand.create(plugin, "ketchupzombies")
                .aliases("kz")
                .playerOnly()
                .messages(plugin.getMessageManager())
                .description("Arena editor")
                .usage("/ketchupzombies join <arena> normal <1-4>|party|leave|createarena|editarena <name>|copy <name>")
                .sub("createarena", sub -> sub.playerOnly().execute(ctx -> editor.openCreate(ctx.player())))
                .sub("test", sub -> sub.playerOnly().execute(ctx -> test.open(ctx.player())))
                .sub("copy", sub -> sub.playerOnly()
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.send("cmd.usage", Map.of("usage", "/ketchupzombies copy <name>"));
                                return;
                            }
                            copies.copy(ctx.player(), name);
                        }))
                .sub("join", sub -> sub.playerOnly()
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> plugin.getGames().join(ctx.player(), ctx.arg(0), ctx.arg(1), ctx.arg(2))))
                .sub("leave", sub -> sub.playerOnly().execute(ctx -> plugin.getGames().leave(ctx.player())))
                .sub("party", sub -> sub.playerOnly()
                        .execute(ctx -> {
                            String action = ctx.arg(0);
                            if ("invite".equalsIgnoreCase(action)) {
                                plugin.getGames().invite(ctx.player(), ctx.arg(1));
                                return;
                            }
                            if ("accept".equalsIgnoreCase(action)) {
                                plugin.getGames().accept(ctx.player());
                                return;
                            }
                            if ("leave".equalsIgnoreCase(action)) {
                                plugin.getGames().partyLeave(ctx.player());
                                return;
                            }
                            plugin.getGames().list(ctx.player());
                        }))
                .sub("refresh", sub -> sub.playerOnly()
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.send("cmd.usage", Map.of("usage", "/ketchupzombies refresh <name>"));
                                return;
                            }
                            copies.refresh(ctx.player(), name);
                        }))
                .sub("recapture", sub -> sub.playerOnly()
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.send("cmd.usage", Map.of("usage", "/ketchupzombies recapture <name>"));
                                return;
                            }
                            copies.recapture(ctx.player(), name);
                        }))
                .sub("weapon", sub -> sub.playerOnly()
                        .tabComplete(ctx -> Arrays.stream(WeaponId.values()).map(WeaponId::id).toList())
                        .execute(ctx -> giveWeapon(ctx.player(), ctx.arg(0), ctx.arg(1))))
                .sub("zombie", sub -> sub.playerOnly().execute(ctx -> spawnZombie(ctx.player(), ctx.arg(0))))
                .sub("gold", sub -> sub.playerOnly().execute(ctx -> setGold(ctx.player(), ctx.arg(0))))
                .sub("shop", sub -> sub.playerOnly().execute(ctx -> plugin.getShopMenu().weapons(ctx.player())))
                .sub("perks", sub -> sub.playerOnly().execute(ctx -> plugin.getShopMenu().perks(ctx.player())))
                .sub("score", sub -> sub.playerOnly().execute(ctx -> score(ctx.player(), ctx.arg(0), ctx.arg(1), ctx.arg(2))))
                .sub("top", sub -> sub.playerOnly().execute(ctx -> top(ctx.player())))
                .sub("editarena", sub -> sub.playerOnly()
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.send("cmd.usage", Map.of("usage", "/ketchupzombies editarena <name>"));
                                return;
                            }
                            editor.openEdit(ctx.player(), name);
                        }))
                .register();
    }

    private void giveWeapon(Player player, String idText, String levelText) {
        WeaponId id = WeaponId.byId(idText);
        if (id == null) {
            reply(player, "&cUnknown weapon. Use knife, pistol, rifle, shotgun, sniper, rocket, flamethrower, dart, soaker, zapper, puncher, elder or digger.");
            return;
        }
        int level = 0;
        if (id == WeaponId.DIGGER && levelText != null && !levelText.isBlank()) {
            if (!levelText.matches("[0-5]")) {
                reply(player, "&cGold digger level is 0 to 5.");
                return;
            }
            level = Integer.parseInt(levelText);
        }
        var stats = switch (id) {
            case KNIFE -> plugin.config().weapons.knife;
            case PISTOL -> plugin.config().weapons.pistol;
            case RIFLE -> plugin.config().weapons.rifle;
            case SHOTGUN -> plugin.config().weapons.shotgun;
            case SNIPER -> plugin.config().weapons.sniper;
            case ROCKET -> plugin.config().weapons.rocketLauncher;
            case FLAMETHROWER -> plugin.config().weapons.flamethrower;
            case DART -> plugin.config().weapons.blowDart;
            case SOAKER -> plugin.config().weapons.zombieSoaker;
            case ZAPPER -> plugin.config().weapons.zombieZapper;
            case PUNCHER -> plugin.config().weapons.puncher;
            case ELDER -> plugin.config().weapons.elderGun;
            case DIGGER -> plugin.config().weapons.goldDigger;
        };
        if (level > 0) {
            var step = plugin.config().weapons.goldDigger.level(level);
            stats = new dev.rajce.ketchupZombies.config.PluginConfig.Weapon(stats.price, stats.gold, step.damage, step.magazine, step.ammo, step.delay, step.reload, stats.pellets, stats.targets);
        }
        player.getInventory().addItem(plugin.getWeaponItems().create(id, stats, level));
        plugin.getWeaponCombat().hud(player);
        plugin.getMessageManager().send(player, "weapon.given", Map.of("weapon", id.title()));
    }

    private void spawnZombie(Player player, String kind) {
        org.bukkit.Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        boolean fire = "fire".equalsIgnoreCase(kind);
        player.getWorld().spawn(at, Zombie.class, zombie -> {
            if (fire) {
                zombie.setFireTicks(200);
                zombie.addScoreboardTag("kz-fire");
            }
        });
        plugin.getMessageManager().send(player, "weapon.zombie");
    }

    private void setGold(Player player, String text) {
        if (text == null || !text.matches("[0-9]{1,9}")) {
            reply(player, "&cUsage: /ketchupzombies gold <amount>");
            return;
        }
        plugin.getProfiles().gold(player.getUniqueId(), Integer.parseInt(text));
        plugin.getMessageManager().send(player, "shop.gold", Map.of("gold", text));
    }

    private void score(Player player, String modeText, String first, String second) {
        if (!plugin.getDatabase().ready()) {
            reply(player, "&cDatabase is offline. Check config and console.");
            return;
        }
        SqlDatabase.Mode mode;
        int round;
        long timeMs;
        if ("normal".equalsIgnoreCase(modeText)) {
            if (first == null || !first.matches("[0-9]{1,9}")) {
                reply(player, "&cUsage: /ketchupzombies score normal <seconds>");
                return;
            }
            mode = SqlDatabase.Mode.NORMAL;
            round = 30;
            timeMs = Long.parseLong(first) * 1000L;
        } else if ("endless".equalsIgnoreCase(modeText)) {
            if (first == null || second == null || !first.matches("[0-9]{1,9}") || !second.matches("[0-9]{1,9}")) {
                reply(player, "&cUsage: /ketchupzombies score endless <round> <seconds>");
                return;
            }
            mode = SqlDatabase.Mode.ENDLESS;
            round = Integer.parseInt(first);
            timeMs = Long.parseLong(second) * 1000L;
        } else {
            reply(player, "&cUsage: /ketchupzombies score <normal|endless> ...");
            return;
        }
        plugin.getDatabase().record(player.getUniqueId(), player.getName(), mode, round, timeMs).whenComplete((saved, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                reply(player, "&cCould not save that score. Check console.");
                return;
            }
            plugin.getMessageManager().send(player, Boolean.TRUE.equals(saved) ? "score.saved" : "score.kept");
        }));
    }

    private void top(Player player) {
        sendBoard(player, "score.normal-head", SqlDatabase.Mode.NORMAL);
        sendBoard(player, "score.endless-head", SqlDatabase.Mode.ENDLESS);
    }

    private void sendBoard(Player player, String head, SqlDatabase.Mode mode) {
        plugin.getMessageManager().send(player, head);
        var board = plugin.getDatabase().top(mode);
        if (board.isEmpty()) {
            plugin.getMessageManager().send(player, "score.empty");
            return;
        }
        int place = 1;
        for (SqlDatabase.Entry entry : board) {
            String value = mode == SqlDatabase.Mode.NORMAL
                    ? (entry.timeMs() / 1000 / 60) + ":" + String.format("%02d", (entry.timeMs() / 1000) % 60)
                    : Integer.toString(entry.round());
            plugin.getMessageManager().send(player, "score.line", Map.of(
                    "place", Integer.toString(place++),
                    "name", entry.name(),
                    "value", value
            ));
        }
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }
}
