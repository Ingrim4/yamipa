package io.josemmo.bukkit.plugin.utils;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class SelectBlockTask {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final Map<UUID, SelectBlockTask> instances = new HashMap<>();
    private static SelectBlockTaskListener listener = null;
    private final Player player;
    private BiConsumer<Location, BlockFace> success;
    private Runnable failure;
    private ActionBar actionBar;

    /**
     * Class constructor
     * @param player Target player instance
     */
    public SelectBlockTask(@NotNull Player player) {
        this.player = player;
    }

    /**
     * Set on success callback
     * @param callback Success callback
     */
    public void onSuccess(@Nullable BiConsumer<@NotNull Location, @NotNull BlockFace> callback) {
        this.success = callback;
    }

    /**
     * Set on failure (e.g. canceled) callback
     * @param callback Failure callback
     */
    public void onFailure(@Nullable Runnable callback) {
        this.failure = callback;
    }

    /**
     * Run task
     * @param helpMessage Help message for the player
     */
    public void run(@NotNull String helpMessage) {
        UUID uuid = player.getUniqueId();

        // Has this player another active task?
        if (instances.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "You already have a pending action!");
            return;
        }

        // Create listener singleton if needed
        if (listener == null) {
            listener = new SelectBlockTaskListener();
            listener.register();
            plugin.fine("Created PlayerInteractionListener singleton");
        }

        // Start task
        instances.put(uuid, this);
        actionBar = ActionBar.repeat(player, ChatColor.GREEN + helpMessage + ChatColor.RESET +
            " - " + ChatColor.RED + "Left click to cancel");
    }

    /**
     * Cancel task
     */
    public void cancel() {
        if (actionBar != null) {
            actionBar.clear();
            actionBar = null;
        }
        instances.remove(player.getUniqueId());

        // Destroy listener singleton if no more active tasks
        if (instances.isEmpty()) {
            listener.unregister();
            listener = null;
            plugin.fine("Destroyed SelectBlockTaskListener singleton");
        }
    }

    /**
     * Internal listener for handling player events
     */
    private static class SelectBlockTaskListener extends InteractWithEntityListener implements Listener {
        @Override
        public void register() {
            super.register();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }

        @Override
        public void unregister() {
            super.unregister();
            HandlerList.unregisterAll(this);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
        public void onBlockInteraction(@NotNull PlayerInteractEvent event) {
            Action action = event.getAction();
            Player player = event.getPlayer();
            Block block = event.getClickedBlock();
            if (block == null) return;
            BlockFace face = event.getBlockFace();

            // Handle failure event
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                handle(player, null, null);
                return;
            }

            // Handle success event
            if (action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                handle(player, block, face);
            }
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
        public void onArmSwing(@NotNull PlayerAnimationEvent event) {
            if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
                // Sanity check, vanilla Minecraft does not have any other player animation type
                return;
            }
            handle(event.getPlayer(), null, null);
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean onAttack(@NotNull Player player, @NotNull Block block, @NotNull BlockFace face) {
            handle(player, null, null);
            return false;
        }

        @Override
        public boolean onInteract(@NotNull Player player, @NotNull Block block, @NotNull BlockFace face) {
            handle(player, block, face);
            return false;
        }

        private void handle(@NotNull Player player, @Nullable Block block, @Nullable BlockFace face) {
            // Get task responsible for handling this event
            UUID uuid = player.getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task == null) return;

            // Cancel task
            task.cancel();

            // Notify failure listener
            if (block == null || face == null) {
                if (task.failure != null) {
                    task.failure.run();
                }
                return;
            }

            // Notify success listener
            if (task.success != null) {
                task.success.accept(block.getLocation(), face);
            }
        }
    }
}
