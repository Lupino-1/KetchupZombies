package dev.rajce.ketchupZombies.game;

import dev.rajce.ketchupZombies.KetchupZombies;
import net.kyori.adventure.text.Component;
import net.megavex.scoreboardlibrary.api.team.ScoreboardTeam;
import net.megavex.scoreboardlibrary.api.team.TeamDisplay;
import net.megavex.scoreboardlibrary.api.team.TeamManager;
import net.megavex.scoreboardlibrary.api.team.enums.CollisionRule;
import net.megavex.scoreboardlibrary.api.team.enums.NameTagVisibility;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

/**
 * Entity push without Bukkit scoreboard teams. Folia throws from
 * {@code CraftScoreboard.registerNewTeam}. The client still predicts a solid hitbox
 * unless it receives a team packet with collision never. Zombie entries are UUIDs.
 * Player names sit on a second team with the same rule so players do not push each other.
 * {@code setCollidable(false)} is the server half.
 */
public final class SoftCollision {

    private static final Object LOCK = new Object();
    private static volatile Field collides;
    private static TeamManager teams;
    private static ScoreboardTeam soft;
    private static ScoreboardTeam players;

    private SoftCollision() {
    }

    public static void start(KetchupZombies plugin) {
        if (plugin.getScoreboards() == null) {
            return;
        }
        try {
            TeamManager manager = plugin.getScoreboards().createTeamManager();
            ScoreboardTeam team = manager.createIfAbsent("kz-soft");
            TeamDisplay display = team.defaultDisplay();
            display.displayName(Component.empty());
            display.collisionRule(CollisionRule.NEVER);
            display.nameTagVisibility(NameTagVisibility.NEVER);
            ScoreboardTeam people = manager.createIfAbsent("kz-players");
            TeamDisplay peopleDisplay = people.defaultDisplay();
            peopleDisplay.displayName(Component.empty());
            peopleDisplay.collisionRule(CollisionRule.NEVER);
            peopleDisplay.nameTagVisibility(NameTagVisibility.ALWAYS);
            synchronized (LOCK) {
                teams = manager;
                soft = team;
                players = people;
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Soft collision team unavailable: " + ex.getMessage());
        }
    }

    public static void joinZombie(KetchupZombies plugin, LivingEntity entity) {
        apply(entity);
        entry(entity, true);
    }

    public static void leaveZombie(KetchupZombies plugin, Entity entity) {
        entry(entity, false);
    }

    public static void player(Player player, boolean push) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (push) {
            player.setCollidable(true);
            synchronized (LOCK) {
                if (players != null) {
                    players.defaultDisplay().removeEntry(player.getName());
                }
                if (teams != null) {
                    teams.removePlayer(player);
                }
            }
            return;
        }
        apply(player);
        synchronized (LOCK) {
            if (players != null) {
                players.defaultDisplay().addEntry(player.getName());
            }
            if (teams != null) {
                teams.addPlayer(player);
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            if (teams != null) {
                teams.close();
            }
            teams = null;
            soft = null;
            players = null;
        }
    }

    public static void apply(LivingEntity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.setCollidable(false);
        try {
            Object handle = entity.getClass().getMethod("getHandle").invoke(entity);
            Field field = collides;
            if (field == null) {
                Class<?> type = handle.getClass();
                while (type != null) {
                    try {
                        field = type.getDeclaredField("collides");
                        field.setAccessible(true);
                        collides = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        type = type.getSuperclass();
                    }
                }
            }
            if (field != null) {
                field.setBoolean(handle, false);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** Mob scoreboard name is the UUID. Player names are never added. */
    private static void entry(Entity entity, boolean join) {
        if (entity == null || entity instanceof Player) {
            return;
        }
        String name = entity.getUniqueId().toString();
        synchronized (LOCK) {
            if (soft == null) {
                return;
            }
            TeamDisplay display = soft.defaultDisplay();
            if (join) {
                display.addEntry(name);
            } else {
                display.removeEntry(name);
            }
        }
    }
}
