package dev.rajce.ketchupZombies.game;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Parties {

    private final ConcurrentHashMap<UUID, Party> byMember = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> invites = new ConcurrentHashMap<>();

    public Party party(Player player) {
        return byMember.get(player.getUniqueId());
    }

    public List<Player> online(Player player) {
        Party party = party(player);
        List<Player> players = new ArrayList<>();
        if (party == null) {
            if (player.isOnline()) {
                players.add(player);
            }
            return players;
        }
        for (UUID id : party.members) {
            Player member = player.getServer().getPlayer(id);
            if (member != null && member.isOnline()) {
                players.add(member);
            }
        }
        return players;
    }

    public String invite(Player leader, Player target) {
        if (leader.equals(target)) {
            return "self";
        }
        Party party = byMember.computeIfAbsent(leader.getUniqueId(), id -> new Party(id));
        if (!party.leader.equals(leader.getUniqueId())) {
            return "leader";
        }
        if (party.members.size() >= 4) {
            return "full";
        }
        if (byMember.containsKey(target.getUniqueId())) {
            return "busy";
        }
        invites.put(target.getUniqueId(), leader.getUniqueId());
        return null;
    }

    public String accept(Player player) {
        UUID leader = invites.remove(player.getUniqueId());
        if (leader == null) {
            return "none";
        }
        Party party = byMember.get(leader);
        if (party == null || party.members.size() >= 4) {
            return "full";
        }
        party.members.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), party);
        return null;
    }

    public void leave(Player player) {
        invites.remove(player.getUniqueId());
        Party party = byMember.remove(player.getUniqueId());
        if (party == null) {
            return;
        }
        party.members.remove(player.getUniqueId());
        if (party.members.isEmpty()) {
            return;
        }
        if (party.leader.equals(player.getUniqueId())) {
            party.leader = party.members.iterator().next();
        }
        for (UUID id : Set.copyOf(party.members)) {
            byMember.put(id, party);
        }
    }

    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();

        private Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }

        public UUID leader() {
            return leader;
        }
    }
}
