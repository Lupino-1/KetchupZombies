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

    private final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();

    public Party party(Player player) {
        return party(player.getUniqueId());
    }

    public Party party(UUID id) {
        return byMember.get(id);
    }

    public String name(UUID id) {
        String stored = names.get(id);
        return stored == null || stored.isBlank() ? id.toString().substring(0, 8) : stored;
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
        remember(leader);
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
            return "gone";
        }
        remember(player);
        party.members.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), party);
        return null;
    }

    public String deny(Player player) {
        UUID leader = invites.remove(player.getUniqueId());
        return leader == null ? "none" : null;
    }

    public String kick(Player leader, Player target) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) {
            return "none";
        }
        if (!party.leader.equals(leader.getUniqueId())) {
            return "leader";
        }
        if (leader.equals(target)) {
            return "self";
        }
        if (!party.members.contains(target.getUniqueId())) {
            return "missing";
        }
        remove(target.getUniqueId(), null);
        return null;
    }

    /**
     * Drop a player from their party. Party kick only — does not touch the server session.
     * If they led the party, leadership moves to {@code preferredLeader} when that id is still a member,
     * otherwise to another remaining member.
     *
     * @return members still in the party, empty when the party is gone
     */
    public List<UUID> remove(UUID id, UUID preferredLeader) {
        invites.remove(id);
        Party party = byMember.remove(id);
        if (party == null) {
            names.remove(id);
            return List.of();
        }
        party.members.remove(id);
        invites.entrySet().removeIf(entry -> entry.getValue().equals(id));
        names.remove(id);
        if (party.members.isEmpty()) {
            return List.of();
        }
        if (party.leader.equals(id)) {
            if (preferredLeader != null && party.members.contains(preferredLeader)) {
                party.leader = preferredLeader;
            } else {
                party.leader = party.members.iterator().next();
            }
        }
        return new ArrayList<>(party.members);
    }

    /** Leader only. Returns kicked member UUIDs (excluding leader). */
    public List<UUID> disband(Player leader) {
        Party party = byMember.get(leader.getUniqueId());
        if (party == null) {
            return List.of();
        }
        if (!party.leader.equals(leader.getUniqueId())) {
            return null;
        }
        List<UUID> members = new ArrayList<>(party.members);
        for (UUID id : members) {
            byMember.remove(id);
            names.remove(id);
            invites.entrySet().removeIf(entry -> entry.getValue().equals(id) || entry.getKey().equals(id));
        }
        return members;
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

        public Set<UUID> members() {
            return Set.copyOf(members);
        }
    }

    private void remember(Player player) {
        names.put(player.getUniqueId(), player.getName());
    }
}
