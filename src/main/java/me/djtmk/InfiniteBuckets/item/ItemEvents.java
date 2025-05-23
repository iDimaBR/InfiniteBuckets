package me.djtmk.InfiniteBuckets.item;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import me.djtmk.InfiniteBuckets.Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

public class ItemEvents implements Listener {

    private final Main plugin;
    private final NamespacedKey infiniteKey;

    public ItemEvents(Main plugin) {
        this.plugin = plugin;
        this.infiniteKey = new NamespacedKey(plugin, "infinite");
    }

    private boolean isSuperiorSkyblockInstalled() {
        return plugin.getServer().getPluginManager().getPlugin("SuperiorSkyblock2") != null;
    }

    private boolean islandCheck(final @NotNull Player player) {
        if (!isSuperiorSkyblockInstalled()) {
            return true;
        }
        Island island = SuperiorSkyblockAPI.getIslandAt(player.getLocation());
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player.getUniqueId());
        if(island==null) return true;
        if(island.getOwner().getUniqueId() == player.getUniqueId()) return true;

        if(superiorPlayer == null) return false;
        if(island.isMember(superiorPlayer) && island.hasPermission(superiorPlayer, IslandPrivilege.getByName("Build"))) return true;
        if(superiorPlayer.hasBypassModeEnabled()) return true;
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Block clickedBlock = event.getClickedBlock();
        plugin.debugLog("PlayerInteractEvent for " + player.getName() + ", block: " + (clickedBlock != null ? clickedBlock.getType() : "null"), item);

        if (item == null || (item.getType() != Material.WATER_BUCKET && item.getType() != Material.LAVA_BUCKET)) {
            plugin.debugLog("Not a bucket item");
            return;
        }

        if (!isInfinite(item)) {
            plugin.debugLog("Bucket is not infinite", item);
            return;
        }

        plugin.debugLog("Infinite bucket detected. Type: " + item.getType());

        if(clickedBlock != null && clickedBlock.getType().name().contains("CHEST")){
            event.setCancelled(true);
            player.updateInventory();
            plugin.debugLog("Detected chest, cancelling the interaction.", new ItemStack(clickedBlock.getType()));
            return;
        }

        // Check island API
        if(!islandCheck(player)) return;

        // If event is cancelled, then don't proceed.
        if(event.isCancelled()) return;

        // If usage is denied, then don't proceed.
        if(event.useInteractedBlock() == Event.Result.DENY ||
                event.useItemInHand() == Event.Result.DENY) return;

        if (clickedBlock == null) {
            plugin.debugLog("No block clicked");
            return;
        }

        Integer bucketType = item.getItemMeta().getPersistentDataContainer().get(infiniteKey, PersistentDataType.INTEGER);
        plugin.debugLog("Bucket infinite type: " + (bucketType == 0 ? "Water" : "Lava"));

        BlockFace face = event.getBlockFace();
        Block targetBlock = clickedBlock.getRelative(face);
        plugin.debugLog("Target block: " + targetBlock.getType());

        // Handle waterlogging for water buckets
        if (bucketType == 0) {
            if (clickedBlock.getBlockData() instanceof Waterlogged waterlogged) {
                plugin.debugLog("Clicked block waterloggable: " + clickedBlock.getType() + ", Current waterlogged: " + waterlogged.isWaterlogged());
                if (!waterlogged.isWaterlogged()) {
                    waterlogged.setWaterlogged(true);
                    clickedBlock.setBlockData(waterlogged);
                    plugin.debugLog("Set clicked block to waterlogged: " + clickedBlock.getType());
                    event.setCancelled(true);
                    preserveBucket(player, item);
                    plugin.debugLog("Waterlogging complete, bucket preserved");
                    return;
                } else {
                    plugin.debugLog("Clicked block already waterlogged");
                }
            } else if (targetBlock.getBlockData() instanceof Waterlogged waterlogged) {
                plugin.debugLog("Target block waterloggable: " + targetBlock.getType() + ", Current waterlogged: " + waterlogged.isWaterlogged());
                if (!waterlogged.isWaterlogged()) {
                    waterlogged.setWaterlogged(true);
                    targetBlock.setBlockData(waterlogged);
                    plugin.debugLog("Set target block to waterlogged: " + targetBlock.getType());
                    event.setCancelled(true);
                    preserveBucket(player, item);
                    plugin.debugLog("Waterlogging complete, bucket preserved");
                    return;
                } else {
                    plugin.debugLog("Target block already waterlogged");
                }
            }
        }

        // Handle cauldron interactions
        if (clickedBlock.getType() == Material.CAULDRON) {
            plugin.debugLog("Interacting with cauldron");
            if (bucketType == 0) {
                clickedBlock.setType(Material.WATER_CAULDRON);
                Levelled cauldronData = (Levelled) clickedBlock.getBlockData();
                cauldronData.setLevel(cauldronData.getMaximumLevel());
                clickedBlock.setBlockData(cauldronData);
                plugin.debugLog("Set cauldron to WATER_CAULDRON, level: " + cauldronData.getLevel());
            } else if (bucketType == 1) {
                clickedBlock.setType(Material.LAVA_CAULDRON);
                plugin.debugLog("Set cauldron to LAVA_CAULDRON");
            }
            event.setCancelled(true);
            preserveBucket(player, item);
            plugin.debugLog("Cauldron interaction complete, bucket preserved");
            return;
        }

        // Handle placement in air or replaceable blocks
        if (bucketType == 0) {
            if (targetBlock.getType().isAir() || targetBlock.getType() == Material.WATER) {
                plugin.debugLog("Placing water at target: " + targetBlock.getType());
                targetBlock.setType(Material.WATER);
                event.setCancelled(true);
                preserveBucket(player, item);
                plugin.debugLog("Water placement complete, bucket preserved");
            }
        } else if (bucketType == 1) {
            if (targetBlock.getType().isAir() || targetBlock.getType() == Material.LAVA) {
                plugin.debugLog("Placing lava at target: " + targetBlock.getType());
                targetBlock.setType(Material.LAVA);
                event.setCancelled(true);
                preserveBucket(player, item);
                plugin.debugLog("Lava placement complete, bucket preserved");
            }
        }
    }

    @EventHandler
    public void onBucketDrain(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();

        plugin.debugLog("PlayerBucketEmptyEvent for " + player.getName() + ", bucket type: " + event.getBucket(), bucket);

        if (isInfinite(bucket)) {
            plugin.debugLog("Infinite bucket detected, cancelling to defer to PlayerInteractEvent", bucket);
            event.setCancelled(true);
            return;
        }

        plugin.debugLog("Non-infinite bucket, allowing default behavior", bucket);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Block block = event.getBlock();

        plugin.debugLog("BlockPlaceEvent for block: " + block.getType(), item);

        if (isInfinite(item)) {
            plugin.debugLog("Infinite bucket detected, cancelling to prevent consumption", item);
            event.setCancelled(true);
        } else {
            plugin.debugLog("Non-infinite item, allowing default behavior", item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ClickType clickType = event.getClick();
        int slot = event.getSlot();

        boolean isCursorInfinite = isInfinite(cursor);
        boolean isCurrentInfinite = isInfinite(current);

        // Debug logs
        plugin.debugLog("InventoryClickEvent for " + player.getName() + ", inventory: " + event.getInventory().getType() +
                ", clicked: " + clickedInventory.getType() + ", click: " + clickType +
                ", slot: " + slot + ", current: " + (current != null ? current.getType() : "null") +
                ", cursor: " + (cursor != null ? cursor.getType() : "null"));

        // Handle trade inventories (AxTrade GUI)
        boolean isTradeInventory = event.getInventory().getType() == InventoryType.MERCHANT ||
                event.getView().getTitle().toLowerCase().contains("trade");

        if (isTradeInventory && (isCurrentInfinite || isCursorInfinite)) {
            ItemStack bucket = isCurrentInfinite ? current : cursor;
            plugin.debugLog("Infinite bucket detected in trade inventory, click: " + clickType + ", slot: " + slot, bucket);

            // Prevent movement or removal of infinite bucket in trade slots
            if (isValidTradeSlot(slot)) {
                event.setCancelled(false);
                plugin.debugLog("Valid trade slot, allowing bucket interaction.");
            } else {
                event.setCancelled(true);
                plugin.debugLog("Invalid trade slot, cancelling interaction with infinite bucket.");
            }
            return;
        }

        // Handle infinite bucket interactions in normal inventories (chests, player inventory, etc.)
        if (!isCursorInfinite && !isCurrentInfinite) return;

        // Prevent stacking infinite buckets
        if (isCursorInfinite && isCurrentInfinite && current.isSimilar(cursor)) {
            plugin.debugLog("Attempt to stack infinite buckets, cancelling");
            event.setCancelled(true);
            return;
        }

        // Allow movement of infinite buckets in chest inventories
        if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
            if (isCurrentInfinite && event.getInventory().getType() == InventoryType.CHEST &&
                    clickedInventory.getType() == InventoryType.PLAYER) {

                ItemStack itemToMove = current.clone();
                itemToMove.setAmount(1);

                // Handle moving the item to an empty slot in chest
                int emptySlot = clickedInventory.firstEmpty();
                if (emptySlot != -1) {
                    clickedInventory.setItem(emptySlot, itemToMove);
                    player.updateInventory();
                    plugin.debugLog("Moved infinite bucket to chest.");
                }
                event.setCancelled(true);
            }
        }
    }

    private void preserveBucket(Player player, ItemStack bucket) {
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getHeldItemSlot();
        inventory.setItem(slot, bucket);
        player.updateInventory();
        plugin.debugLog("Synchronously preserved bucket in slot " + slot, bucket);
    }

    private boolean isInfinite(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(infiniteKey, PersistentDataType.INTEGER);
    }

    private boolean isValidTradeSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    private int findValidTradeSlot(Inventory inventory) {
        // Find an empty slot in the trade offer area (slots 0-8)
        for (int i = 0; i <= 8; i++) {
            if (inventory.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }
}