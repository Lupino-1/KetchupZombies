package dev.rajce.ketchupZombies.managers;

import dev.lupino1.command.LPCommand;
import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.db.SqlDatabase;
import dev.rajce.ketchupZombies.editor.BlueprintTest;
import dev.rajce.ketchupZombies.editor.Editor;
import dev.rajce.ketchupZombies.weapons.WeaponId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                .permission("ketchupzombies.command")
                .messages(plugin.getMessageManager())
                .description("Arena editor")
                .usage("/ketchupzombies join <arena> normal <1-4>|createarena|editarena <name>|copy <name>|setspawn")
                .sub("createarena", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.createarena")
                        .execute(ctx -> editor.openCreate(ctx.player())))
                .sub("test", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.test")
                        .execute(ctx -> test.open(ctx.player())))
                .sub("copy", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.copy")
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
                        .permission("ketchupzombies.command.join")
                        .tabComplete(ctx -> joinComplete(ctx.args()))
                        .execute(ctx -> plugin.getGames().join(ctx.player(), ctx.arg(0), ctx.arg(1), ctx.arg(2))))
                .sub("setspawn", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.setspawn")
                        .execute(ctx -> setSpawn(ctx.player())))
                .sub("scoreboard", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.scoreboard")
                        .execute(ctx -> scoreboard(ctx.player())))
                .sub("reload", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.reload")
                        .execute(ctx -> reload(ctx.player())))
                .sub("refresh", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.refresh")
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
                        .permission("ketchupzombies.command.recapture")
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.send("cmd.usage", Map.of("usage", "/ketchupzombies recapture <name>"));
                                return;
                            }
                            copies.recapture(ctx.player(), name);
                        }))
                .sub("blueprint", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.blueprint")
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.send("cmd.usage", Map.of("usage", "/ketchupzombies blueprint <name>"));
                                return;
                            }
                            copies.teleportBlueprint(ctx.player(), name);
                        }))
                .sub("weapon", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.weapon")
                        .tabComplete(ctx -> Arrays.stream(WeaponId.values()).map(WeaponId::id).toList())
                        .execute(ctx -> giveWeapon(ctx.player(), ctx.arg(0), ctx.arg(1))))
                .sub("zombie", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.zombie")
                        .execute(ctx -> spawnZombie(ctx.player(), ctx.arg(0))))
                .sub("gold", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.gold")
                        .execute(ctx -> setGold(ctx.player(), ctx.arg(0))))
                .sub("shop", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.shop")
                        .execute(ctx -> plugin.getShopMenu().weapons(ctx.player())))
                .sub("perks", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.perks")
                        .execute(ctx -> plugin.getShopMenu().perks(ctx.player())))
                .sub("score", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.score")
                        .execute(ctx -> score(ctx.player(), ctx.arg(0), ctx.arg(1), ctx.arg(2))))
                .sub("top", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.top")
                        .execute(ctx -> top(ctx.player())))
                .sub("editarena", sub -> sub.playerOnly()
                        .permission("ketchupzombies.command.editarena")
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

        LPCommand.create(plugin, "leave")
                .playerOnly()
                .permission("ketchupzombies.command.leave")
                .messages(plugin.getMessageManager())
                .description("Leave the queue or match")
                .execute(ctx -> plugin.getGames().leave(ctx.player()))
                .register();

        LPCommand.create(plugin, "spawn")
                .playerOnly()
                .permission("ketchupzombies.command.spawn")
                .messages(plugin.getMessageManager())
                .description("Teleport to the hub spawn")
                .execute(ctx -> goSpawn(ctx.player()))
                .register();

        // Standalone /party (not /kz party). Clickable accept/deny live in messages.yml.
        LPCommand.create(plugin, "party")
                .playerOnly()
                .permission("ketchupzombies.command.party")
                .messages(plugin.getMessageManager())
                .description("Party commands")
                .usage("/party invite|accept|deny|list|leave|kick|disband")
                .execute(ctx -> plugin.getGames().partyHelp(ctx.player()))
                .sub("invite", inv -> inv.playerOnly()
                        .permission("ketchupzombies.command.party.invite")
                        .tabComplete(ctx -> onlineNames(ctx.player()))
                        .execute(ctx -> plugin.getGames().invite(ctx.player(), ctx.arg(0))))
                .sub("accept", acc -> acc.playerOnly()
                        .permission("ketchupzombies.command.party.accept")
                        .execute(ctx -> plugin.getGames().accept(ctx.player())))
                .sub("deny", deny -> deny.playerOnly()
                        .permission("ketchupzombies.command.party.deny")
                        .execute(ctx -> plugin.getGames().deny(ctx.player())))
                .sub("leave", leave -> leave.playerOnly()
                        .permission("ketchupzombies.command.party.leave")
                        .execute(ctx -> plugin.getGames().partyLeave(ctx.player())))
                .sub("kick", kick -> kick.playerOnly()
                        .permission("ketchupzombies.command.party.kick")
                        .tabComplete(ctx -> onlineNames(ctx.player()))
                        .execute(ctx -> plugin.getGames().kick(ctx.player(), ctx.arg(0))))
                .sub("disband", dis -> dis.playerOnly()
                        .permission("ketchupzombies.command.party.disband")
                        .execute(ctx -> plugin.getGames().disband(ctx.player())))
                .sub("list", list -> list.playerOnly()
                        .permission("ketchupzombies.command.party.list")
                        .execute(ctx -> plugin.getGames().list(ctx.player())))
                .register();
    }

    private void setSpawn(Player player) {
        plugin.setHubSpawn(player.getLocation()).whenComplete((ok, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().warning("setspawn: " + error.getMessage());
                reply(player, "&cCould not save hub spawn.");
                return;
            }
            plugin.getMessageManager().send(player, "cmd.setspawn");
        }));
    }

    private void goSpawn(Player player) {
        if (plugin.getGames().busy(player)) {
            plugin.getMessageManager().send(player, "cmd.spawn-busy");
            return;
        }
        if (plugin.getEditor().inSession(player)) {
            plugin.getMessageManager().send(player, "cmd.spawn-busy");
            return;
        }
        org.bukkit.Location hub = plugin.hubSpawn();
        if (hub == null) {
            plugin.getMessageManager().send(player, "cmd.spawn-unset");
            return;
        }
        player.teleportAsync(hub).thenRun(() -> FoliaManager.runAtEntity(player, () -> {
            if (player.isOnline()) {
                plugin.getMessageManager().send(player, "cmd.spawn");
            }
        }));
    }

    private void scoreboard(Player player) {
        if (plugin.getGames().inMatch(player)) {
            reply(player, "&cLeave the match before testing the scoreboard preview.");
            return;
        }
        boolean on = plugin.getBoardPreview().toggle(player);
        plugin.getMessageManager().send(player, on ? "cmd.scoreboard-on" : "cmd.scoreboard-off");
    }

    private void reload(Player player) {
        plugin.reloadSettings().whenComplete((ok, error) -> FoliaManager.runAtEntity(player, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().warning("Reload failed: " + error.getMessage());
                reply(player, "&cReload failed. Check console.");
                return;
            }
            plugin.getMessageManager().send(player, "cmd.reloaded");
        }));
    }

    private void giveWeapon(Player player, String idText, String levelText) {
        WeaponId id = WeaponId.byId(idText);
        if (id == null) {
            reply(player, "&cUnknown weapon. Use knife, pistol, rifle, shotgun, sniper, rocket, flamethrower, dart, soaker, zapper, puncher, elder or digger.");
            return;
        }
        if (plugin.getGames().inQueue(player)) {
            reply(player, "&cYou are in waiting lobby — /leave first, then test in hub.");
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
        final int upgrade = level;
        final WeaponId weapon = id;
        // Folia: inventory on player entity thread.
        player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) {
                return;
            }
            var stats = switch (weapon) {
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
            if (upgrade > 0) {
                var step = plugin.config().weapons.goldDigger.level(upgrade);
                stats = new dev.rajce.ketchupZombies.config.PluginConfig.Weapon(
                        stats.price, stats.gold, step.damage, step.magazine, step.ammo, step.delay, step.reload, stats.pellets, stats.targets);
            }
            var created = plugin.getWeaponItems().create(weapon, stats, upgrade);
            if (plugin.getGames().inMatch(player) && upgrade == 0) {
                plugin.getShopMenu().giveWeapon(player, weapon);
            } else {
                player.getInventory().setItemInMainHand(created);
                if (!weapon.melee()) {
                    plugin.getWeaponCombat().bindAmmo(player, created);
                }
                if (plugin.getGames().inMatch(player)) {
                    dev.rajce.ketchupZombies.weapons.WeaponSlots.fillEmpties(plugin, player);
                }
            }
            plugin.getWeaponCombat().hud(player);
            plugin.getWeaponCombat().held(player);
            plugin.getMessageManager().send(player, "weapon.given", Map.of(
                    "weapon", dev.rajce.ketchupZombies.weapons.WeaponCatalog.displayName(plugin, weapon, upgrade)));
        }, null);
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

    private List<String> joinComplete(String[] args) {
        if (args == null || args.length <= 1) {
            return editor.completeArenas();
        }
        if (args.length == 2) {
            return List.of("normal");
        }
        if (args.length == 3 && "normal".equalsIgnoreCase(args[1])) {
            return List.of("1", "2", "3", "4");
        }
        return List.of();
    }

    private static List<String> onlineNames(Player except) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> except == null || !player.getUniqueId().equals(except.getUniqueId()))
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }
}
