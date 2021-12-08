package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.Objects;

public class ItemService implements Listener {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final NamespacedKey NSK_FILENAME = new NamespacedKey(plugin, "filename");
    private static final NamespacedKey NSK_WIDTH = new NamespacedKey(plugin, "width");
    private static final NamespacedKey NSK_HEIGHT = new NamespacedKey(plugin, "height");

    /**
     * Get image item
     * @param  image  Image file
     * @param  amount Stack amount
     * @param  width  Image width in blocks
     * @param  height Image height in blocks
     * @return        Image item
     */
    public static @NotNull ItemStack getImageItem(@NotNull ImageFile image, int amount, int width, int height) {
        ItemStack itemStack = new ItemStack(Material.GLOW_ITEM_FRAME, amount);
        ItemMeta itemMeta = Objects.requireNonNull(itemStack.getItemMeta());

        // Set metadata
        PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
        itemMeta.setDisplayName(image.getName() + ChatColor.AQUA + " (" + width + "x" + height + ")");
        itemMeta.setLore(Collections.singletonList("Yamipa image"));
        itemData.set(NSK_FILENAME, PersistentDataType.STRING, image.getName());
        itemData.set(NSK_WIDTH, PersistentDataType.INTEGER, width);
        itemData.set(NSK_HEIGHT, PersistentDataType.INTEGER, height);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    /**
     * Start service
     */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Stop service
     */
    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraftItem(@NotNull PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        for (@Nullable ItemStack item : inventory.getMatrix()) {
            if (item == null) continue;

            // Get metadata from item
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null) continue;

            // Prevent crafting recipes with image items
            if (itemMeta.getPersistentDataContainer().has(NSK_FILENAME, PersistentDataType.STRING)) {
                inventory.setResult(new ItemStack(Material.AIR));
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlaceItem(@NotNull HangingPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemStack();
        if (player == null || item == null) return;

        // Get metadata from item
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return;
        PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
        String filename = itemData.get(NSK_FILENAME, PersistentDataType.STRING);
        if (filename == null) return;
        Integer width = itemData.get(NSK_WIDTH, PersistentDataType.INTEGER);
        Integer height = itemData.get(NSK_HEIGHT, PersistentDataType.INTEGER);
        if (width == null || height == null) {
            plugin.warning(player + " tried to place corrupted image item (missing width/height properties)");
            return;
        }

        // Validate filename
        ImageFile image = YamipaPlugin.getInstance().getStorage().get(filename);
        if (image == null) {
            plugin.warning(player + " tried to place corrupted image item (\"" + filename + "\" no longer exists)");
            player.sendMessage(ChatColor.RED + "Image file \"" + filename + "\" no longer exists");
            return;
        }

        // Prevent item frame placing
        event.setCancelled(true);

        // Try to place image in world
        Location location = event.getBlock().getLocation();
        int flags = FakeImage.DEFAULT_FLAGS | FakeImage.FLAG_REMOVABLE | FakeImage.FLAG_DROPPABLE;
        boolean success = ImageCommand.placeImage(player, image, width, height, location, event.getBlockFace(), flags);
        if (!success) return;

        // Decrement item from player's inventory
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerInventory inventory = player.getInventory();
        int itemIndex = inventory.first(item);
        int amount = item.getAmount();
        if (amount > 1) {
            item.setAmount(amount - 1);
            inventory.setItem(itemIndex, item);
        } else {
            inventory.setItem(itemIndex, new ItemStack(Material.AIR));
        }
    }
}
