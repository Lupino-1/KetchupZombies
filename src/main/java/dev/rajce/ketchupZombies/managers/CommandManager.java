package dev.rajce.ketchupZombies.managers;

import dev.lupino1.command.CommandContext;
import dev.lupino1.command.LPCommand;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.models.Blueprint;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class CommandManager {

    private static final int SPAN_XZ = 156;
    private static final int SPAN_Y = 32;

    private final KetchupZombies plugin;
    private final BlueprintManager blueprints;
    private volatile Blueprint clipboard;

    public CommandManager(KetchupZombies plugin, BlueprintManager blueprints) {
        this.plugin = plugin;
        this.blueprints = blueprints;
    }

    public void register() {
        LPCommand.create(plugin, "kz")
                .playerOnly()
                .description("Blueprint test")
                .usage("/kz copy|paste")
                .sub("copy", sub -> sub.playerOnly().execute(this::copy))
                .sub("paste", sub -> sub.playerOnly().execute(this::paste))
                .register();
    }

    private void copy(CommandContext ctx) {
        Player player = ctx.player();
        World world = player.getWorld();
        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        int halfXz = SPAN_XZ / 2;
        int halfY = SPAN_Y / 2;

        Blueprint blueprint = new Blueprint(new Location(world, x - halfXz, y - halfY, z - halfXz), new Location(world, x + halfXz - 1, y + halfY - 1, z + halfXz - 1));
        ctx.reply("Copying " + SPAN_XZ + "x" + SPAN_Y + "x" + SPAN_XZ + "...");
        blueprints.capture(blueprint).thenAccept(captured -> {
            clipboard = captured;
            ctx.reply("Copied blocks: " + captured.getBlocks().size());
        });
    }

    private void paste(CommandContext ctx) {
        Blueprint blueprint = clipboard;
        if (blueprint == null || blueprint.getBlocks().isEmpty()) {
            ctx.reply("Nothing to paste.");
            return;
        }
        ctx.reply("Pasting...");
        blueprints.paste(blueprint, ctx.player().getLocation(), () -> ctx.reply("Pasted."));
    }
}
