package dev.rajce.ketchupZombies.game;

import dev.lupino1.folia.FoliaManager;
import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.util.Heads;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Lying player-head corpse + timer hologram.
 * <p>
 * Lock = SPECTATOR + fly/walk speed 0 + teleport feet so eyes sit on the death spot.
 * No armor-stand sit / no spectatorTarget. Free look keeps yaw/pitch.
 */
final class DownedBodies {

    /** Small AS buried so only the helmet shows. */
    private static final double CORPSE_FEET_Y = -0.55;
    /** Eyes above the death feet. 0.35 sat inside the corpse head; 0.70 clears it and stays under the timer at 1.15. */
    private static final double EYE_ABOVE_DEATH = 0.70;
    private static final double FALLBACK_EYE = 1.62;

    private final KetchupZombies plugin;
    private final NamespacedKey key;
    private final Map<UUID, Pack> packs = new ConcurrentHashMap<>();

    DownedBodies(KetchupZombies plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "downed-body");
    }

    void spawn(Player player, Location death) {
        spawn(player, death, () -> true);
    }

    /**
     * @param active return false to abort (e.g. match already ended / wipe) so a late Folia
     *               callback cannot re-bind spectator after restore.
     */
    void spawn(Player player, Location death, BooleanSupplier active) {
        clear(player.getUniqueId());
        World world = death.getWorld();
        if (world == null) {
            return;
        }
        // Exact death X/Z/Y — do NOT snap to block center (that caused ~1 block diagonal offset).
        Location exact = death.clone();
        float yaw = exact.getYaw();
        // Corpse under the death point; camera eyes aim at death + small Y (ground look).
        Location eyeTarget = exact.clone().add(0, EYE_ABOVE_DEATH, 0);
        FoliaManager.runAtLocation(exact, () -> {
            if (!player.isOnline() || !active.getAsBoolean()) {
                return;
            }
            ArmorStand corpse = world.spawn(exact.clone().add(0, CORPSE_FEET_Y, 0), ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setBasePlate(false);
                stand.setArms(false);
                stand.setInvulnerable(true);
                stand.setCollidable(false);
                stand.setSilent(true);
                stand.setRotation(yaw, 0f);
                stand.getEquipment().setHelmet(Heads.playerHead(player));
                stand.setHeadPose(new EulerAngle(0, 0, 0));
                stand.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
            });
            TextDisplay hologram = world.spawn(exact.clone().add(0, 1.15, 0), TextDisplay.class, display -> {
                display.text(Component.empty());
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(true);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
            });
            if (!active.getAsBoolean()) {
                remove(corpse);
                remove(hologram);
                return;
            }
            packs.put(player.getUniqueId(), new Pack(corpse, hologram, exact.clone(), eyeTarget.clone()));
            player.getScheduler().run(plugin, task -> {
                if (!active.getAsBoolean() || !packs.containsKey(player.getUniqueId())) {
                    return;
                }
                bindSpectator(player, cameraFeet(player, eyeTarget));
            }, null);
        });
    }

    void bindSpectator(Player player, Location feet) {
        if (!player.isOnline() || feet == null || feet.getWorld() == null) {
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
        player.setFlySpeed(0f);
        player.setWalkSpeed(0f);
        snapLook(player, feet);
    }

    void keepSpectator(Player player) {
        Pack pack = packs.get(player.getUniqueId());
        if (pack == null || pack.eyeTarget == null || pack.eyeTarget.getWorld() == null) {
            return;
        }
        Location feet = cameraFeet(player, pack.eyeTarget);
        if (player.getGameMode() != GameMode.SPECTATOR) {
            bindSpectator(player, feet);
            return;
        }
        if (player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }
        Location here = player.getLocation();
        if (here.getWorld() == null || !here.getWorld().equals(feet.getWorld())
                || here.distanceSquared(feet) > 0.04) {
            snapLook(player, feet);
        }
        player.setFlySpeed(0f);
        player.setWalkSpeed(0f);
    }

    /**
     * Spectator eyes are {@code eyeHeight} above feet. Put feet so eyes land on {@code eyeTarget}.
     */
    private static Location cameraFeet(Player player, Location eyeTarget) {
        double eye = player.getEyeHeight();
        if (eye < 0.5) {
            eye = FALLBACK_EYE;
        }
        Location feet = eyeTarget.clone().subtract(0, eye, 0);
        feet.setYaw(player.getLocation().getYaw());
        feet.setPitch(player.getLocation().getPitch());
        return feet;
    }

    private void snapLook(Player player, Location feet) {
        Location dest = feet.clone();
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        // Folia forbids sync teleport on the region thread.
        player.teleportAsync(dest);
    }

    void timer(UUID id, Component text) {
        Pack pack = packs.get(id);
        if (pack == null || pack.hologram == null || !pack.hologram.isValid()) {
            return;
        }
        pack.hologram.getScheduler().run(plugin, task -> pack.hologram.text(text), null);
    }

    void timerRaw(UUID id, String legacy) {
        timer(id, ColorParser.translateColors(legacy == null ? "" : legacy));
    }

    Location death(UUID id) {
        Pack pack = packs.get(id);
        return pack == null ? null : pack.death.clone();
    }

    void clear(UUID id) {
        Pack pack = packs.remove(id);
        if (pack == null) {
            return;
        }
        remove(pack.corpse);
        remove(pack.hologram);
    }

    void clearAll() {
        for (UUID id : packs.keySet().toArray(UUID[]::new)) {
            clear(id);
        }
    }

    void release(Player player) {
        clear(player.getUniqueId());
        unlockMovement(player);
    }

    /** Clear spectator lock and restore normal movement (also used after wipe). */
    void unlockMovement(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFlySpeed(0.1f);
        player.setWalkSpeed(0.2f);
    }

    private void remove(org.bukkit.entity.Entity entity) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        entity.getScheduler().run(plugin, task -> entity.remove(), null);
    }

    /** eyeTarget = where spectator eyes should sit (death + small Y). */
    private record Pack(ArmorStand corpse, TextDisplay hologram, Location death, Location eyeTarget) {
    }
}
