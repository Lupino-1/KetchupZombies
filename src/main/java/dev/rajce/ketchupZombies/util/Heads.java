package dev.rajce.ketchupZombies.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.rajce.ketchupZombies.models.Blueprint;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public final class Heads {

    private Heads() {
    }

    public static ItemStack playerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(player.getPlayerProfile());
        item.setItemMeta(meta);
        return item;
    }

    public static Blueprint.SkullSkin read(Block block) {
        if (!(block.getState() instanceof Skull skull)) {
            return null;
        }
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return null;
        }
        String textures = null;
        String signature = null;
        for (ProfileProperty property : profile.getProperties()) {
            if ("textures".equals(property.getName())) {
                textures = property.getValue();
                signature = property.getSignature();
                break;
            }
        }
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        UUID id = profile.getId();
        if (id == null) {
            id = UUID.nameUUIDFromBytes(("kz-skull:" + textures).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String name = profile.getName();
        if (name == null || name.isEmpty()) {
            name = "skull";
        }
        return new Blueprint.SkullSkin(id, name, textures, signature);
    }

    public static void apply(Block block, Blueprint.SkullSkin skin) {
        if (skin == null || !(block.getState() instanceof Skull skull)) {
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(skin.id(), skin.name());
        if (skin.signature() != null && !skin.signature().isEmpty()) {
            profile.setProperty(new ProfileProperty("textures", skin.textures(), skin.signature()));
        } else {
            profile.setProperty(new ProfileProperty("textures", skin.textures()));
        }
        skull.setPlayerProfile(profile);
        skull.update(true, false);
    }
}
