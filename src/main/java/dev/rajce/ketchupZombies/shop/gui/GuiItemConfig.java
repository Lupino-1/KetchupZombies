package dev.rajce.ketchupZombies.shop.gui;

import dev.lupino1.config.Naming;
import dev.lupino1.config.NamingStrategy;
import dev.lupino1.config.YamlConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Naming(NamingStrategy.KEBAB)
public final class GuiItemConfig extends YamlConfig {

    public String material = "STONE";
    public String name = " ";
    public int modelData = 0;
    public List<String> lore = new ArrayList<>();
    public List<String> ownedLore = new ArrayList<>();
    public List<String> slots = new ArrayList<>();
    public List<String> leftClickActions = new ArrayList<>();
    public List<String> rightClickActions = new ArrayList<>();

    public Set<Integer> parsedSlots() {
        Set<Integer> out = new HashSet<>();
        for (String entry : slots) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String token = entry.trim();
            if (token.contains("-")) {
                String[] split = token.split("-", 2);
                try {
                    int start = Integer.parseInt(split[0].trim());
                    int end = Integer.parseInt(split[1].trim());
                    for (int i = Math.min(start, end); i <= Math.max(start, end); i++) {
                        out.add(i);
                    }
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    out.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }
}
