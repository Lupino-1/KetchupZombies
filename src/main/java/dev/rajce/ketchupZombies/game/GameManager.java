package dev.rajce.ketchupZombies.game;

import dev.lupino1.messages.ColorParser;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.managers.ArenaCopies;
import dev.rajce.ketchupZombies.models.ArenaData;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

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
    private final Parties parties = new Parties();
    private final Queues queues = new Queues();
    private final RoundBook book;
    private final NamespacedKey mobKey;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();
    private final Map<String, Match> byId = new ConcurrentHashMap<>();
    private final Set<UUID> preparing = ConcurrentHashMap.newKeySet();

    public GameManager(KetchupZombies plugin, ArenaCopies copies) {
        this.plugin = plugin;
        this.copies = copies;
        book = new RoundBook(plugin);
        mobKey = new NamespacedKey(plugin, "match");
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

    public void forget(Match match) {
        byId.remove(match.id());
        matches.values().removeIf(current -> current == match);
    }

    public void unlink(Player player) {
        matches.remove(player.getUniqueId());
    }

    public void invite(Player leader, String name) {
        Player target = Bukkit.getPlayerExact(name == null ? "" : name);
        if (target == null) {
            reply(leader, "&cThat player is not online.");
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
        String error = parties.accept(player);
        if (error != null) {
            reply(player, "&cYou have no party invite.");
            return;
        }
        plugin.getMessageManager().send(player, "party.joined", Map.of("name", player.getName()));
    }

    public void partyLeave(Player player) {
        queues.drop(player.getUniqueId());
        parties.leave(player);
        plugin.getMessageManager().send(player, "party.left");
    }

    public void list(Player player) {
        List<Player> group = parties.online(player);
        StringBuilder names = new StringBuilder();
        for (Player member : group) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(member.getName());
        }
        plugin.getMessageManager().send(player, "party.list", Map.of("names", names.toString()));
    }

    public void join(Player player, String arena, String mode, String countText) {
        if (plugin.getEditor().inSession(player)) {
            reply(player, "&cClose the arena editor first.");
            return;
        }
        if (inMatch(player) || preparing.contains(player.getUniqueId())) {
            reply(player, "&cYou are already in a game.");
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
                plugin.getMessageManager().send(player, "join.queued", Map.of("count", Integer.toString(offer.count())));
                return;
            }
            group = new ArrayList<>();
            for (UUID id : offer.players()) {
                Player member = Bukkit.getPlayer(id);
                if (member != null && member.isOnline()) {
                    group.add(member);
                }
            }
        }
        start(player, arena, group);
    }

    public void leave(Player player) {
        queues.drop(player.getUniqueId());
        Match match = matches.get(player.getUniqueId());
        if (match != null) {
            match.quit(player);
            return;
        }
        plugin.getMessageManager().send(player, "game.left");
    }

    private void start(Player leader, String arena, List<Player> group) {
        for (Player member : group) {
            preparing.add(member.getUniqueId());
        }
        copies.playCopy(leader, arena, (index, data) -> begin(leader, arena, index, data, group), () -> {
            for (Player member : group) {
                preparing.remove(member.getUniqueId());
            }
        });
    }

    private void begin(Player leader, String arena, int index, ArenaData data, List<Player> group) {
        for (Player member : group) {
            preparing.remove(member.getUniqueId());
        }
        World world = plugin.getServer().getWorld(data.sections().values().iterator().next().world());
        if (world == null) {
            reply(leader, "&cThat world is not loaded.");
            copies.reset(arena, index);
            return;
        }
        Match match = new Match(plugin, book, mobKey, arena, index, data, world);
        byId.put(match.id(), match);
        for (Player member : group) {
            if (member.isOnline()) {
                matches.put(member.getUniqueId(), match);
            }
        }
        match.begin(group);
    }

    private void reply(Player player, String message) {
        player.sendMessage(ColorParser.translateColors(message));
    }
}
