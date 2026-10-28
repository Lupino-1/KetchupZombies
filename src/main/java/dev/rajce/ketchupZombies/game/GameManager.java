package dev.rajce.ketchupZombies.game;

import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.config.SoundKit;
import dev.rajce.ketchupZombies.managers.ArenaCopies;
import dev.rajce.ketchupZombies.models.ArenaData;
import dev.rajce.ketchupZombies.models.ArenaSection;
import dev.rajce.ketchupZombies.models.FinePoint;
import dev.rajce.ketchupZombies.util.ItemCopies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GameManager {

    private final KetchupZombies plugin;
    private final ArenaCopies copies;
    private final Isolation isolation;
    private final Parties parties = new Parties();
    /** Offline member → party-kick task. Not a server kick. */
    private final Map<UUID, ScheduledTask> partyOffline = new ConcurrentHashMap<>();
    /** Shown once on the next login after a party removal while offline. */
    private final Map<UUID, String> partyNote = new ConcurrentHashMap<>();
    private final Queues queues = new Queues();
    private final RoundBook book;
    private final NamespacedKey mobKey;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();
    private final Map<String, Match> byId = new ConcurrentHashMap<>();
    private final Set<UUID> preparing = ConcurrentHashMap.newKeySet();
    /** Queue slot id → waiting room on a reserved copy. */
    private final Map<UUID, WaitingRoom> waiting = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerSlot = new ConcurrentHashMap<>();
    /** Where a player must land on next join — set when a restore teleport could not finish (quit). */
    private final Map<UUID, Location> pendingReturn = new ConcurrentHashMap<>();

    public GameManager(KetchupZombies plugin, ArenaCopies copies) {
        this.plugin = plugin;
        this.copies = copies;
        this.isolation = new Isolation(plugin);
        book = new RoundBook(plugin);
        mobKey = new NamespacedKey(plugin, "match");
    }

    public Isolation isolation() {
        return isolation;
    }

    public boolean downed(Player player) {
        Match match = matches.get(player.getUniqueId());
        return match != null && match.downed(player);
    }

    public void gold(Player player) {
        Match match = matches.get(player.getUniqueId());
        if (match != null) {
            match.gold(player);
        }
    }

    public void click(Player player, org.bukkit.Location at, String tag) {
        Match match = matches.get(player.getUniqueId());
        if (match == null || tag == null || !tag.equals(match.arena() + ":" + match.index())) {
            return;
        }
        match.click(player, at);
    }

    /** @return true when a door was bought / handled from a door block click */
    public boolean clickDoorBlock(Player player, org.bukkit.Location block) {
        Match match = matches.get(player.getUniqueId());
        return match != null && match.clickDoorBlock(player, block);
    }

    public void hurt(Player player) {
        Match match = matches.get(player.getUniqueId());
        if (match != null) {
            match.hurt(player);
        }
    }

    public void killed(org.bukkit.entity.LivingEntity mob) {
        String id = mob.getPersistentDataContainer().get(mobKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (id == null) {
            return;
        }
        Match match = byId.get(id);
        if (match != null) {
            Player killer = mob.getKiller();
            match.killed(mob, killer);
        }
    }

    public boolean inMatch(Player player) {
        return matches.containsKey(player.getUniqueId());
    }

    public boolean inQueue(Player player) {
        return playerSlot.containsKey(player.getUniqueId());
    }

    public boolean preparing(Player player) {
        return preparing.contains(player.getUniqueId());
    }

    public Match match(Player player) {
        return matches.get(player.getUniqueId());
    }

    public void forget(Match match) {
        byId.remove(match.id());
        matches.values().removeIf(current -> current == match);
    }

    public void unlink(Player player) {
        matches.remove(player.getUniqueId());
    }

    /** Void / stuck: put a busy player back on the lobby or arena spawn. */
    public void rescue(Player player) {
        Location spot = respawnSpot(player);
        if (spot == null) {
            return;
        }
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.teleportAsync(spot).thenRun(() -> player.getScheduler().run(plugin, task -> player.setFallDistance(0f), null));
    }

    /** Respawn / rescue target for queue or match players; null when not busy. */
    public Location respawnSpot(Player player) {
        Match match = matches.get(player.getUniqueId());
        if (match != null) {
            return match.respawnSpot(player);
        }
        UUID slotId = playerSlot.get(player.getUniqueId());
        WaitingRoom room = slotId == null ? null : waiting.get(slotId);
        Location lobby = room == null ? null : lobbySpawn(room);
        return lobby != null ? lobby : plugin.hubSpawn();
    }

    /** After a real death in queue/match: lobby = full reset, match = knocked down at respawn spot. */
    public void afterRespawn(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Match match = matches.get(player.getUniqueId());
        if (match != null) {
            match.afterRespawn(player);
            return;
        }
        if (inQueue(player)) {
            resetVitals(player);
        }
    }

    public void markReturn(UUID id, Location dest) {
        if (dest != null) {
            pendingReturn.put(id, dest.clone());
        }
    }

    public void clearReturn(UUID id) {
        pendingReturn.remove(id);
    }

    /**
     * Member went offline: remove them from the party after {@code party.offline-seconds}.
     * Leader went offline: pass the party to another member now and drop the leader.
     * Neither path kicks the player from the server.
     */
    public void partyDisconnect(Player player) {
        UUID id = player.getUniqueId();
        Parties.Party party = parties.party(id);
        if (party == null) {
            return;
        }
        cancelPartyOffline(id);
        if (party.leader().equals(id)) {
            passParty(id, player.getName());
            return;
        }
        long ticks = Math.max(20L, Math.round(plugin.config().party.offlineSeconds * 20.0));
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduled -> {
            partyOffline.remove(id);
            Player back = Bukkit.getPlayer(id);
            if (back != null && back.isOnline()) {
                return;
            }
            if (parties.party(id) == null) {
                return;
            }
            String name = parties.name(id);
            List<UUID> left = parties.remove(id, null);
            partyNote.put(id, "party.you-kicked");
            tellParty(left, "party.offline-removed", Map.of("name", name));
        }, ticks);
        partyOffline.put(id, task);
    }

    /** Back online before the offline timer: stay in the party. Pending note is from a kick that already happened. */
    public void partyReconnect(Player player) {
        cancelPartyOffline(player.getUniqueId());
        String key = partyNote.remove(player.getUniqueId());
        if (key != null) {
            plugin.getMessageManager().send(player, key);
        }
    }

    private void passParty(UUID id, String name) {
        Parties.Party party = parties.party(id);
        UUID next = null;
        if (party != null) {
            for (UUID member : party.members()) {
                if (member.equals(id)) {
                    continue;
                }
                Player online = Bukkit.getPlayer(member);
                if (online != null && online.isOnline()) {
                    next = member;
                    break;
                }
            }
        }
        List<UUID> left = parties.remove(id, next);
        partyNote.put(id, "party.you-kicked");
        if (left.isEmpty()) {
            return;
        }
        Parties.Party updated = parties.party(left.get(0));
        UUID leaderId = updated == null ? left.get(0) : updated.leader();
        String leaderName = parties.name(leaderId);
        tellParty(left, "party.leader-passed", Map.of("name", name, "leader", leaderName));
        Player leader = Bukkit.getPlayer(leaderId);
        if (leader != null && leader.isOnline()) {
            tell(leader, "party.now-leader", Map.of());
        }
    }

    private void cancelPartyOffline(UUID id) {
        ScheduledTask task = partyOffline.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private void tellParty(List<UUID> ids, String key, Map<String, String> values) {
        for (UUID id : ids) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                tell(player, key, values);
            }
        }
    }

    private void tell(Player player, String key, Map<String, String> values) {
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                plugin.getMessageManager().send(player, key, values);
            }
        }, null);
    }

    /** Finish a restore teleport that was cut off by disconnect. */
    public void returnOnJoin(Player player) {
        Location dest = pendingReturn.remove(player.getUniqueId());
        if (dest == null || busy(player)) {
            return;
        }
        player.teleportAsync(dest);
    }

    /** Plugin disable: hand hub kits back to everyone still in a queue or match. */
    public void shutdown() {
        for (Match match : List.copyOf(byId.values())) {
            try {
                match.shutdown();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Match shutdown failed: " + ex.getMessage());
            }
        }
        for (WaitingRoom room : List.copyOf(waiting.values())) {
            for (UUID id : List.copyOf(room.players)) {
                Player player = Bukkit.getPlayer(id);
                SavedBack back = room.backs.remove(id);
                if (player == null || back == null) {
                    continue;
                }
                try {
                    applyBack(player, back);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Queue restore failed for " + player.getName() + ": " + ex.getMessage());
                }
            }
        }
        waiting.clear();
        playerSlot.clear();
        preparing.clear();
        for (ScheduledTask task : partyOffline.values()) {
            task.cancel();
        }
        partyOffline.clear();
    }

    private static void resetVitals(Player player) {
        org.bukkit.attribute.AttributeInstance max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20.0 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    public void invite(Player leader, String name) {
        if (busy(leader)) {
            reply(leader, "&cLeave the queue or match first.");
            return;
        }
        Player target = Bukkit.getPlayerExact(name == null ? "" : name);
        if (target == null) {
            reply(leader, "&cThat player is not online.");
            return;
        }
        if (busy(target)) {
            reply(leader, "&cThat player is in a queue or match.");
            return;
        }
        String error = parties.invite(leader, target);
        if ("self".equals(error)) {
            reply(leader, "&cYou cannot invite yourself.");
            return;
        }
        if ("leader".equals(error)) {
            reply(leader, "&cOnly the party leader can invite.");
            return;
        }
        if ("full".equals(error)) {
            reply(leader, "&cThe party is full.");
            return;
        }
        if ("busy".equals(error)) {
            reply(leader, "&cThat player is already in a party.");
            return;
        }
        plugin.getMessageManager().send(leader, "party.invited", Map.of("name", target.getName()));
        plugin.getMessageManager().send(target, "party.invite", Map.of("name", leader.getName()));
    }

    public void accept(Player player) {
        if (busy(player)) {
            reply(player, "&cLeave the queue or match first.");
            return;
        }
        String error = parties.accept(player);
        if ("none".equals(error)) {
            reply(player, "&cYou have no party invite.");
            return;
        }
        if ("gone".equals(error)) {
            reply(player, "&cThat party is full or gone.");
            return;
        }
        plugin.getMessageManager().send(player, "party.joined");
        Parties.Party party = parties.party(player);
        if (party != null) {
            Player leader = Bukkit.getPlayer(party.leader());
            if (leader != null && leader.isOnline() && !leader.equals(player)) {
                plugin.getMessageManager().send(leader, "party.member-joined", Map.of("name", player.getName()));
            }
        }
    }

    public void deny(Player player) {
        String error = parties.deny(player);
        if (error != null) {
            reply(player, "&cYou have no party invite.");
            return;
        }
        plugin.getMessageManager().send(player, "party.denied");
    }

    public void kick(Player leader, String name) {
        Player target = Bukkit.getPlayerExact(name == null ? "" : name);
        if (target == null) {
            reply(leader, "&cThat player is not online.");
            return;
        }
        String error = parties.kick(leader, target);
        if ("none".equals(error)) {
            reply(leader, "&cYou are not in a party.");
            return;
        }
        if ("leader".equals(error)) {
            reply(leader, "&cOnly the party leader can kick.");
            return;
        }
        if ("self".equals(error)) {
            reply(leader, "&cUse /kz party leave or disband.");
            return;
        }
        if ("missing".equals(error)) {
            reply(leader, "&cThat player is not in your party.");
            return;
        }
        leaveQueue(target, false);
        plugin.getMessageManager().send(leader, "party.kicked", Map.of("name", target.getName()));
        plugin.getMessageManager().send(target, "party.you-kicked");
    }

    public void disband(Player leader) {
        List<UUID> members = parties.disband(leader);
        if (members == null) {
            reply(leader, "&cOnly the party leader can disband.");
            return;
        }
        if (members.isEmpty()) {
            reply(leader, "&cYou are not in a party.");
            return;
        }
        for (UUID id : members) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                leaveQueue(member, false);
                plugin.getMessageManager().send(member, "party.disbanded");
            } else {
                queues.drop(id);
                playerSlot.remove(id);
            }
        }
    }

    public void partyLeave(Player player) {
        leaveQueue(player, false);
        Parties.Party before = parties.party(player);
        UUID leaderId = before == null ? null : before.leader();
        parties.leave(player);
        plugin.getMessageManager().send(player, "party.left");
        if (leaderId != null && !leaderId.equals(player.getUniqueId())) {
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader != null && leader.isOnline()) {
                plugin.getMessageManager().send(leader, "party.member-left", Map.of("name", player.getName()));
            }
        }
    }

    public void list(Player player) {
        Parties.Party party = parties.party(player);
        if (party == null) {
            plugin.getMessageManager().send(player, "party.list-alone");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (UUID id : party.members()) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            Player member = Bukkit.getPlayer(id);
            boolean online = member != null && member.isOnline();
            if (id.equals(party.leader())) {
                names.append("*");
            }
            names.append(online ? member.getName() : parties.name(id));
            if (!online) {
                names.append(" (offline)");
            }
        }
        plugin.getMessageManager().send(player, "party.list", Map.of("names", names.toString()));
    }

    public void partyHelp(Player player) {
        plugin.getMessageManager().send(player, "party.help");
    }

    public void join(Player player, String arena, String mode, String countText) {
        if (plugin.getEditor().inSession(player)) {
            reply(player, "&cClose the arena editor first.");
            return;
        }
        if (inMatch(player) || preparing.contains(player.getUniqueId()) || inQueue(player)) {
            reply(player, "&cYou are already in a game or queue.");
            return;
        }
        if (!"normal".equalsIgnoreCase(mode) || countText == null || !countText.matches("[1-4]")) {
            reply(player, "&cUsage: /ketchupzombies join <arena> normal <1-4>");
            return;
        }
        int count = Integer.parseInt(countText);
        Parties.Party party = parties.party(player);
        if (party != null && !party.leader().equals(player.getUniqueId())) {
            reply(player, "&cOnly the party leader can join.");
            return;
        }
        List<Player> group = parties.online(player);
        for (Player member : group) {
            if (busy(member) && !member.equals(player)) {
                reply(player, "&cA party member is already in a queue or match.");
                return;
            }
        }
        if (count != 4 && group.size() != count) {
            reply(player, "&cYour party has " + group.size() + " players. That join needs exactly " + count + ".");
            return;
        }
        if (count == 4 && group.size() < 4) {
            Set<UUID> ids = new LinkedHashSet<>();
            for (Player member : group) {
                ids.add(member.getUniqueId());
            }
            Queues.Offer offer = queues.offer(arena, ids);
            if (!offer.start()) {
                enterWaiting(player, arena, group, offer);
                return;
            }
            WaitingRoom room = waiting.get(offer.slotId());
            if (room != null) {
                room.needed = 4;
                room.queueMode = true;
                for (Player member : group) {
                    playerSlot.put(member.getUniqueId(), offer.slotId());
                    room.players.add(member.getUniqueId());
                    room.backs.putIfAbsent(member.getUniqueId(), snapshot(member));
                    if (room.index >= 0) {
                        landWaiting(member, room);
                    }
                }
            }
            beginCountdown(offer.slotId());
            return;
        }
        start(player, arena, group, count);
    }

    public void leave(Player player) {
        if (inQueue(player)) {
            leaveQueue(player, true);
            return;
        }
        Match match = matches.get(player.getUniqueId());
        if (match != null) {
            match.quit(player);
            return;
        }
        plugin.getMessageManager().send(player, "game.left");
    }

    private void enterWaiting(Player leader, String arena, List<Player> group, Queues.Offer offer) {
        WaitingRoom room = waiting.computeIfAbsent(offer.slotId(), id -> new WaitingRoom(arena, id));
        room.needed = 4;
        room.queueMode = true;
        for (Player member : group) {
            playerSlot.put(member.getUniqueId(), offer.slotId());
            room.players.add(member.getUniqueId());
            room.backs.putIfAbsent(member.getUniqueId(), snapshot(member));
        }
        plugin.getMessageManager().send(leader, "join.queued", Map.of("count", Integer.toString(offer.count())));
        for (Player member : group) {
            if (!member.equals(leader)) {
                plugin.getMessageManager().send(member, "join.queued", Map.of("count", Integer.toString(offer.count())));
            }
        }
        if (room.index >= 0 && room.data != null) {
            for (Player member : group) {
                landWaiting(member, room);
            }
            paintLobby(room);
            broadcastQueue(room, offer.count());
            return;
        }
        if (room.claiming) {
            return;
        }
        room.claiming = true;
        for (Player member : group) {
            preparing.add(member.getUniqueId());
        }
        copies.playCopy(leader, arena, (index, data) -> {
            room.index = index;
            room.data = data;
            room.claiming = false;
            for (UUID id : Set.copyOf(room.players)) {
                preparing.remove(id);
                Player member = Bukkit.getPlayer(id);
                if (member != null && member.isOnline()) {
                    landWaiting(member, room);
                }
            }
            if (room.startWhenReady) {
                beginCountdown(room.slotId);
            } else {
                paintLobby(room);
                broadcastQueue(room, room.players.size());
            }
        }, () -> {
            room.claiming = false;
            for (UUID id : Set.copyOf(room.players)) {
                preparing.remove(id);
                Player member = Bukkit.getPlayer(id);
                if (member != null) {
                    leaveQueue(member, true);
                }
            }
            waiting.remove(offer.slotId());
        });
    }

    private void landWaiting(Player player, WaitingRoom room) {
        isolation.set(player, Isolation.queue(room.slotId));
        if (plugin.getHubBoard() != null) {
            plugin.getHubBoard().hide(player);
        }
        Location spawn = lobbySpawn(room);
        if (spawn == null) {
            return;
        }
        player.teleportAsync(spawn).thenRun(() -> player.getScheduler().run(plugin, task -> {
            if (!player.isOnline() || !inQueue(player)) {
                return;
            }
            // Waiting lobby: no game hotbar. Hub inv is in room.backs; match loadout runs on launch.
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.setItemOnCursor(null);
            player.setGameMode(GameMode.ADVENTURE);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setWalkSpeed(0.2f);
            resetVitals(player);
            paintLobbyPlayer(player, room);
        }, null));
    }

    private Location lobbySpawn(WaitingRoom room) {
        if (room.data == null || room.index < 0) {
            return null;
        }
        return arenaSpawn(room.data, room.index);
    }

    /** Arena player spawn for copy index, or section-center fallback. */
    public Location arenaSpawn(ArenaData data, int index) {
        if (data == null) {
            return null;
        }
        int offsetX = plugin.config().copies.offsetX * index;
        int offsetZ = plugin.config().copies.offsetZ * index;
        if (data.spawn() != null) {
            FinePoint point = data.spawn();
            World world = plugin.getServer().getWorld(point.world());
            if (world == null) {
                return null;
            }
            return new Location(world, point.x() + offsetX, point.y(), point.z() + offsetZ, point.yaw(), point.pitch());
        }
        if (data.sections().isEmpty()) {
            return null;
        }
        ArenaSection section = data.sections().values().iterator().next();
        World world = plugin.getServer().getWorld(section.world());
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                (section.x1() + section.x2()) / 2.0 + offsetX + 0.5,
                section.y1() + 1,
                (section.z1() + section.z2()) / 2.0 + offsetZ + 0.5
        );
    }

    private void broadcastQueue(WaitingRoom room, int count) {
        for (UUID id : room.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                plugin.getMessageManager().send(player, "join.queue-update", Map.of("count", Integer.toString(count)));
            }
        }
    }

    private void beginCountdown(UUID slotId) {
        WaitingRoom room = waiting.get(slotId);
        if (room == null) {
            return;
        }
        if (room.index < 0 || room.data == null) {
            room.startWhenReady = true;
            return;
        }
        room.startWhenReady = false;
        room.counting = true;
        room.lastTitleSecond = -1;
        int seconds = Math.max(1, plugin.config().preGame.countdownSeconds);
        room.endsAt = System.currentTimeMillis() + seconds * 1000L;
        boolean titleNow = seconds <= 5;
        if (titleNow) {
            room.lastTitleSecond = seconds;
        }
        for (UUID id : room.players) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                plugin.getMessageManager().send(member, "join.countdown", Map.of("time", Integer.toString(seconds)));
                if (titleNow) {
                    showLobbyCountdownTitle(member, seconds);
                }
            }
        }
        paintLobby(room);
        scheduleLobbyTick(room);
    }

    private void scheduleLobbyTick(WaitingRoom room) {
        Player host = null;
        for (UUID id : room.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                host = player;
                break;
            }
        }
        if (host == null) {
            return;
        }
        // Retired = host disconnected before the tick: hand the countdown to someone else.
        host.getScheduler().runDelayed(plugin, task -> {
            WaitingRoom current = waiting.get(room.slotId);
            if (current == null || !current.counting || current.endsAt <= 0L) {
                return;
            }
            long leftMs = current.endsAt - System.currentTimeMillis();
            int seconds = (int) Math.ceil(leftMs / 1000.0);
            if (seconds <= 0) {
                launchMatch(current);
                return;
            }
            paintLobby(current);
            boolean titleTick = seconds <= 5 && seconds != current.lastTitleSecond;
            if (titleTick) {
                current.lastTitleSecond = seconds;
            }
            for (UUID id : current.players) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    SoundKit.play(player, player.getLocation(), plugin.config().sounds.lobbyTick);
                    if (titleTick) {
                        showLobbyCountdownTitle(player, seconds);
                    }
                }
            }
            scheduleLobbyTick(current);
        }, () -> {
            WaitingRoom current = waiting.get(room.slotId);
            if (current != null && current.counting) {
                scheduleLobbyTick(current);
            }
        }, 20L);
    }

    /** Big number title for the last 5 seconds of the pre-game countdown. */
    private void showLobbyCountdownTitle(Player player, int seconds) {
        String titleRaw = plugin.getMessageManager().getRaw("join.countdown-title", "&e&l%time%")
                .replace("%time%", Integer.toString(seconds));
        String subRaw = plugin.getMessageManager().getRaw("join.countdown-subtitle", "")
                .replace("%time%", Integer.toString(seconds));
        Component title = titleRaw.isBlank() ? Component.empty() : ColorParser.translateColors(titleRaw);
        Component subtitle = subRaw.isBlank() ? Component.empty() : ColorParser.translateColors(subRaw);
        Title shown = Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(0),
                Duration.ofMillis(25 * 50L),
                Duration.ofMillis(5 * 50L)
        ));
        player.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                player.showTitle(shown);
            }
        }, null);
    }

    private void paintLobby(WaitingRoom room) {
        int seconds = -1;
        if (room.counting && room.endsAt > 0L) {
            seconds = Math.max(0, (int) Math.ceil((room.endsAt - System.currentTimeMillis()) / 1000.0));
        }
        for (UUID id : room.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                paintLobbyPlayer(player, room, seconds);
            }
        }
    }

    private void paintLobbyPlayer(Player player, WaitingRoom room) {
        int seconds = -1;
        if (room.counting && room.endsAt > 0L) {
            seconds = Math.max(0, (int) Math.ceil((room.endsAt - System.currentTimeMillis()) / 1000.0));
        }
        paintLobbyPlayer(player, room, seconds);
    }

    private void paintLobbyPlayer(Player player, WaitingRoom room, int seconds) {
        if (plugin.getLobbyBoard() != null) {
            plugin.getLobbyBoard().show(player, seconds, room.players.size(), Math.max(1, room.needed));
        }
    }

    private void launchMatch(WaitingRoom room) {
        waiting.remove(room.slotId);
        room.counting = false;
        List<Player> group = new ArrayList<>();
        Map<UUID, Match.Saved> returns = new ConcurrentHashMap<>();
        for (UUID id : Set.copyOf(room.players)) {
            playerSlot.remove(id);
            preparing.remove(id);
            Player member = Bukkit.getPlayer(id);
            SavedBack back = room.backs.get(id);
            if (back != null) {
                // Deep-copy again so match restore cannot share arrays with queue leave.
                returns.put(id, new Match.Saved(
                        ItemCopies.of(back.contents),
                        ItemCopies.of(back.armor),
                        back.back.clone(),
                        back.mode
                ));
            }
            if (member != null && member.isOnline()) {
                if (plugin.getLobbyBoard() != null) {
                    plugin.getLobbyBoard().hide(member);
                }
                SoundKit.play(member, member.getLocation(), plugin.config().sounds.lobbyGo);
                group.add(member);
            }
        }
        if (group.isEmpty()) {
            if (room.index >= 0) {
                copies.reset(room.arena, room.index);
            }
            return;
        }
        begin(group.getFirst(), room.arena, room.index, room.data, group, returns);
    }

    private void leaveQueue(Player player, boolean message) {
        UUID slotId = playerSlot.remove(player.getUniqueId());
        queues.drop(player.getUniqueId());
        preparing.remove(player.getUniqueId());
        if (plugin.getLobbyBoard() != null) {
            plugin.getLobbyBoard().hide(player);
        }
        if (slotId == null) {
            isolation.hub(player);
            if (plugin.getHubBoard() != null) {
                plugin.getHubBoard().show(player);
            }
            return;
        }
        WaitingRoom room = waiting.get(slotId);
        SavedBack back = null;
        if (room != null) {
            room.players.remove(player.getUniqueId());
            back = room.backs.remove(player.getUniqueId());
            boolean wasCounting = room.counting;
            room.counting = false;
            room.endsAt = 0L;
            if (room.players.isEmpty()) {
                waiting.remove(slotId);
                if (room.index >= 0) {
                    copies.reset(room.arena, room.index);
                }
            } else if (!room.queueMode) {
                // Direct lobby (solo / exact party): anyone leaving cancels for the rest
                List<UUID> rest = List.copyOf(room.players);
                waiting.remove(slotId);
                for (UUID id : rest) {
                    playerSlot.remove(id);
                    SavedBack otherBack = room.backs.remove(id);
                    Player other = Bukkit.getPlayer(id);
                    if (other != null && other.isOnline()) {
                        if (plugin.getLobbyBoard() != null) {
                            plugin.getLobbyBoard().hide(other);
                        }
                        isolation.hub(other);
                        if (plugin.getHubBoard() != null) {
                            plugin.getHubBoard().show(other);
                        }
                        if (otherBack != null) {
                            restoreBack(other, otherBack);
                        }
                        plugin.getMessageManager().send(other, "join.countdown-cancel");
                    }
                }
                if (room.index >= 0) {
                    copies.reset(room.arena, room.index);
                }
            } else {
                paintLobby(room);
                broadcastQueue(room, room.players.size());
                if (wasCounting) {
                    for (UUID id : room.players) {
                        Player other = Bukkit.getPlayer(id);
                        if (other != null) {
                            plugin.getMessageManager().send(other, "join.countdown-cancel");
                        }
                    }
                }
            }
        }
        isolation.hub(player);
        if (plugin.getHubBoard() != null) {
            plugin.getHubBoard().show(player);
        }
        if (back != null) {
            restoreBack(player, back);
        }
        if (message) {
            plugin.getMessageManager().send(player, "join.queue-left");
        }
    }

    private void start(Player leader, String arena, List<Player> group, int needed) {
        UUID slotId = UUID.randomUUID();
        WaitingRoom room = new WaitingRoom(arena, slotId);
        room.needed = needed;
        room.queueMode = false;
        waiting.put(slotId, room);
        for (Player member : group) {
            preparing.add(member.getUniqueId());
            playerSlot.put(member.getUniqueId(), slotId);
            room.players.add(member.getUniqueId());
            room.backs.put(member.getUniqueId(), snapshot(member));
        }
        copies.playCopy(leader, arena, (index, data) -> {
            room.index = index;
            room.data = data;
            for (UUID id : Set.copyOf(room.players)) {
                preparing.remove(id);
                Player member = Bukkit.getPlayer(id);
                if (member != null && member.isOnline()) {
                    landWaiting(member, room);
                }
            }
            beginCountdown(slotId);
        }, () -> {
            for (UUID id : Set.copyOf(room.players)) {
                preparing.remove(id);
                Player member = Bukkit.getPlayer(id);
                if (member != null) {
                    leaveQueue(member, true);
                }
            }
            waiting.remove(slotId);
        });
    }

    private void begin(Player leader, String arena, int index, ArenaData data, List<Player> group, Map<UUID, Match.Saved> returns) {
        for (Player member : group) {
            preparing.remove(member.getUniqueId());
        }
        World world = plugin.getServer().getWorld(data.sections().values().iterator().next().world());
        if (world == null) {
            if (leader != null) {
                reply(leader, "&cThat world is not loaded.");
            }
            copies.reset(arena, index);
            return;
        }
        Match match = new Match(plugin, book, mobKey, arena, index, data, world);
        byId.put(match.id(), match);
        for (Player member : group) {
            if (member.isOnline()) {
                matches.put(member.getUniqueId(), match);
                isolation.set(member, Isolation.match(match.id()));
            }
        }
        match.begin(group, returns == null ? Map.of() : returns);
    }

    public void onMatchRestore(Player player) {
        isolation.hub(player);
    }

    public boolean busy(Player player) {
        return inMatch(player) || inQueue(player) || preparing.contains(player.getUniqueId());
    }

    private static List<Player> onlinePlayers(List<UUID> ids) {
        List<Player> out = new ArrayList<>();
        for (UUID id : ids) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                out.add(member);
            }
        }
        return out;
    }

    private static SavedBack snapshot(Player player) {
        return new SavedBack(
                ItemCopies.of(player.getInventory().getContents()),
                ItemCopies.of(player.getInventory().getArmorContents()),
                player.getLocation().clone(),
                player.getGameMode()
        );
    }

    private void restoreBack(Player player, SavedBack back) {
        // Quit event runs on the player's region: apply now, a scheduled task would be retired
        // and the empty lobby inventory would be saved to playerdata.
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            restoreBackNow(player, back);
            return;
        }
        player.getScheduler().run(plugin, task -> restoreBackNow(player, back), null);
    }

    private void restoreBackNow(Player player, SavedBack back) {
        applyBack(player, back);
        Location hub = plugin.hubSpawn();
        Location dest = hub != null ? hub : (back.back != null ? back.back.clone() : null);
        if (dest == null) {
            return;
        }
        UUID id = player.getUniqueId();
        markReturn(id, dest);
        player.teleportAsync(dest).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                clearReturn(id);
            }
        });
    }

    /** Always clear, then hub kit. Used before and after teleport. */
    private static void applyBack(Player player, SavedBack back) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        if (back != null) {
            player.getInventory().setContents(ItemCopies.of(back.contents));
            player.getInventory().setArmorContents(ItemCopies.of(back.armor));
            player.setGameMode(back.mode);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.setFlying(false);
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE);
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }

    private static final class WaitingRoom {
        private final String arena;
        private final UUID slotId;
        private final Set<UUID> players = ConcurrentHashMap.newKeySet();
        private final Map<UUID, SavedBack> backs = new ConcurrentHashMap<>();
        private int index = -1;
        private int needed = 4;
        private ArenaData data;
        private boolean claiming;
        private boolean queueMode = true;
        private boolean counting;
        private boolean startWhenReady;
        private long endsAt;
        /** Last second that already got a countdown title (avoid duplicates). */
        private int lastTitleSecond = -1;

        private WaitingRoom(String arena, UUID slotId) {
            this.arena = arena;
            this.slotId = slotId;
        }
    }

    private record SavedBack(ItemStack[] contents, ItemStack[] armor, Location back, GameMode mode) {
    }
}
