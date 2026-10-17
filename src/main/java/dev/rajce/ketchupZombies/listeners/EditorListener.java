package dev.rajce.ketchupZombies.listeners;

import dev.rajce.ketchupZombies.editor.Editor;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EditorListener implements Listener {

    private final Editor editor;

    public EditorListener(Editor editor) {
        this.editor = editor;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!editor.capturingChat(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        editor.onChat(event.getPlayer(), text);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (editor.hologramMark(event.getPlayer())) {
            event.setCancelled(true);
            editor.confirmHologram(event.getPlayer());
            return;
        }
        if (!editor.shouldCancelBreak(event.getPlayer(), event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        editor.leftClickBlock(event.getPlayer(), event.getBlock());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) {
            return;
        }
        if (editor.hologramMark(event.getPlayer())) {
            event.setCancelled(true);
            editor.confirmHologram(event.getPlayer());
            return;
        }
        if (action != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null || !editor.marking(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        editor.leftClickBlock(event.getPlayer(), event.getClickedBlock());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        editor.placeDoorBlock(event.getPlayer(), event.getBlockPlaced());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editor.onQuit(event.getPlayer());
    }
}
