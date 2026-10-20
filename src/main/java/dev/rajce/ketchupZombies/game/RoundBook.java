package dev.rajce.ketchupZombies.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rajce.ketchupZombies.KetchupZombies;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RoundBook {

    public record Mob(String type, int count, double health, double damage, double armor, double speed) {
    }

    public record Wave(List<Mob> mobs) {
    }

    private final List<List<Wave>> rounds = new ArrayList<>();

    public RoundBook(KetchupZombies plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("zombies/dead-end.json"), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("rounds")) {
                JsonObject round = element.getAsJsonObject();
                List<Wave> waves = new ArrayList<>();
                for (JsonElement waveElement : round.getAsJsonArray("waves")) {
                    List<Mob> mobs = new ArrayList<>();
                    JsonArray groups = waveElement.getAsJsonObject().getAsJsonArray("mobs");
                    for (JsonElement groupElement : groups) {
                        JsonObject group = groupElement.getAsJsonObject();
                        mobs.add(new Mob(
                                group.get("type").getAsString(),
                                group.get("count").getAsInt(),
                                group.get("health").getAsDouble(),
                                group.get("attackDamage").getAsDouble(),
                                group.has("armor") ? group.get("armor").getAsDouble() : 0,
                                group.has("speed") ? group.get("speed").getAsDouble() : 0.23
                        ));
                    }
                    waves.add(new Wave(List.copyOf(mobs)));
                }
                rounds.add(List.copyOf(waves));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Round book: " + e.getMessage());
        }
    }

    public List<Wave> waves(int number) {
        if (number < 1 || number > rounds.size()) {
            return List.of(new Wave(List.of(new Mob("ZOMBIE", 4, 20, 3, 0, 0.23))));
        }
        return rounds.get(number - 1);
    }
}
