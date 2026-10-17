package dev.rajce.ketchupZombies.managers;

import dev.lupino1.command.LPCommand;
import dev.rajce.ketchupZombies.KetchupZombies;
import dev.rajce.ketchupZombies.editor.BlueprintTest;
import dev.rajce.ketchupZombies.editor.Editor;

public class CommandManager {

    private final KetchupZombies plugin;
    private final Editor editor;
    private final BlueprintTest test;

    public CommandManager(KetchupZombies plugin, Editor editor, BlueprintTest test) {
        this.plugin = plugin;
        this.editor = editor;
        this.test = test;
    }

    public void register() {
        LPCommand.create(plugin, "ketchupzombies")
                .aliases("kz")
                .playerOnly()
                .description("Arena editor")
                .usage("/ketchupzombies createarena|editarena <name>|test")
                .sub("createarena", sub -> sub.playerOnly().execute(ctx -> editor.openCreate(ctx.player())))
                .sub("test", sub -> sub.playerOnly().execute(ctx -> test.open(ctx.player())))
                .sub("editarena", sub -> sub.playerOnly()
                        .tabComplete(ctx -> editor.completeArenas())
                        .execute(ctx -> {
                            String name = ctx.arg(0);
                            if (name == null || name.isBlank()) {
                                ctx.reply("&cUsage: /ketchupzombies editarena <name>");
                                return;
                            }
                            editor.openEdit(ctx.player(), name);
                        }))
                .register();
    }
}
