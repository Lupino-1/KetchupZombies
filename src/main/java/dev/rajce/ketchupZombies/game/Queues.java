package dev.rajce.ketchupZombies.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Queues {

    private final java.util.Map<String, List<Slot>> rows = new java.util.HashMap<>();

    public synchronized Offer offer(String arena, Set<UUID> party) {
        List<Slot> slots = rows.computeIfAbsent(arena, key -> new ArrayList<>());
        Slot open = null;
        for (Slot slot : slots) {
            if (slot.count() < 4) {
                open = slot;
                break;
            }
        }
        if (open != null && open.count() + party.size() <= 4) {
            open.parties.add(Set.copyOf(party));
            if (open.count() == 4) {
                slots.remove(open);
                return new Offer(true, 4, open.members(), open.id);
            }
            return new Offer(false, open.count(), open.members(), open.id);
        }
        Slot waiting = new Slot();
        waiting.parties.add(Set.copyOf(party));
        slots.add(waiting);
        return new Offer(false, waiting.count(), waiting.members(), waiting.id);
    }

    public synchronized void drop(UUID player) {
        for (List<Slot> slots : rows.values()) {
            slots.removeIf(slot -> {
                slot.parties.removeIf(party -> party.contains(player));
                return slot.parties.isEmpty();
            });
        }
    }

    public synchronized UUID slotOf(UUID player) {
        for (List<Slot> slots : rows.values()) {
            for (Slot slot : slots) {
                for (Set<UUID> party : slot.parties) {
                    if (party.contains(player)) {
                        return slot.id;
                    }
                }
            }
        }
        return null;
    }

    public record Offer(boolean start, int count, List<UUID> players, UUID slotId) {
    }

    private static final class Slot {
        private final UUID id = UUID.randomUUID();
        private final List<Set<UUID>> parties = new ArrayList<>();

        private int count() {
            int total = 0;
            for (Set<UUID> party : parties) {
                total += party.size();
            }
            return total;
        }

        private List<UUID> members() {
            Set<UUID> all = new LinkedHashSet<>();
            for (Set<UUID> party : parties) {
                all.addAll(party);
            }
            return List.copyOf(all);
        }
    }
}
