package dev.rajce.ketchupZombies.util;

import dev.rajce.ketchupZombies.models.Blueprint;
import org.bukkit.DyeColor;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;

import java.util.ArrayList;
import java.util.List;

public final class Banners {

    private Banners() {
    }

    public static Blueprint.BannerSkin read(Block block) {
        if (!(block.getState() instanceof Banner banner)) {
            return null;
        }
        List<Pattern> patterns = banner.getPatterns();
        if (patterns == null || patterns.isEmpty()) {
            // Base color is in the block material (e.g. white_banner); nothing extra to store.
            return null;
        }
        List<Blueprint.BannerLayer> layers = new ArrayList<>(patterns.size());
        for (Pattern pattern : patterns) {
            if (pattern == null || pattern.getColor() == null || pattern.getPattern() == null) {
                continue;
            }
            layers.add(new Blueprint.BannerLayer(pattern.getColor().name(), patternKey(pattern.getPattern())));
        }
        return new Blueprint.BannerSkin(List.copyOf(layers));
    }

    public static void apply(Block block, Blueprint.BannerSkin skin) {
        if (skin == null || !(block.getState() instanceof Banner banner)) {
            return;
        }
        List<Pattern> patterns = new ArrayList<>();
        for (Blueprint.BannerLayer layer : skin.patterns()) {
            DyeColor color = dye(layer.color());
            PatternType type = patternType(layer.pattern());
            if (color == null || type == null) {
                continue;
            }
            patterns.add(new Pattern(color, type));
        }
        banner.setPatterns(patterns);
        banner.update(true, false);
    }

    private static String patternKey(PatternType type) {
        try {
            String id = type.getIdentifier();
            if (id != null && !id.isEmpty()) {
                return id;
            }
        } catch (Throwable ignored) {
        }
        return type.name();
    }

    private static PatternType patternType(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        PatternType byId = PatternType.getByIdentifier(key);
        if (byId != null) {
            return byId;
        }
        try {
            return PatternType.valueOf(key.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static DyeColor dye(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return DyeColor.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
