package com.epicOneMace;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class EpicOneMace extends JavaPlugin implements Listener {

    private boolean maceExists = false;
    private UUID maceOwnerUUID = null;
    private static final String MACE_OWNER_CONFIG_PATH = "mace-owner";
    private static final String MACE_EXISTS_CONFIG_PATH = "mace-exists";
    private static final Material MACE_MATERIAL = Material.MACE;
    private static final String NOTIFICATION_MESSAGE = "§c§lOnly one mace can exist on the server!";

    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        getCommand("checkmace").setExecutor(this);
        getCommand("resetmace").setExecutor(this);
        
        // Load mace status from config
        loadMaceStatus();
        
        getLogger().info("EpicOneMace plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save mace status to config
        saveMaceStatus();
        
        getLogger().info("EpicOneMace plugin has been disabled!");
    }

    /**
     * Load mace status from config file
     */
    private void loadMaceStatus() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        
        maceExists = config.getBoolean(MACE_EXISTS_CONFIG_PATH, false);
        String ownerUUIDString = config.getString(MACE_OWNER_CONFIG_PATH);
        
        if (ownerUUIDString != null && !ownerUUIDString.isEmpty()) {
            try {
                maceOwnerUUID = UUID.fromString(ownerUUIDString);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID in config: " + ownerUUIDString);
                maceOwnerUUID = null;
            }
        }
        
        // Verify mace still exists if maceExists is true
        if (maceExists && maceOwnerUUID != null) {
            Player owner = Bukkit.getPlayer(maceOwnerUUID);
            if (owner != null && !playerHasMace(owner)) {
                // Owner is online but doesn't have the mace
                maceExists = false;
                maceOwnerUUID = null;
            }
        }
    }

    /**
     * Save mace status to config file
     */
    private void saveMaceStatus() {
        FileConfiguration config = getConfig();
        
        config.set(MACE_EXISTS_CONFIG_PATH, maceExists);
        config.set(MACE_OWNER_CONFIG_PATH, maceOwnerUUID != null ? maceOwnerUUID.toString() : null);
        
        saveConfig();
    }

    /**
     * Check if a player has a mace in their inventory
     */
    private boolean playerHasMace(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == MACE_MATERIAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Update mace owner information
     */
    private void updateMaceOwner(Player player) {
        maceExists = true;
        maceOwnerUUID = player.getUniqueId();
        saveMaceStatus();
    }

    /**
     * Remove mace from player inventory
     */
    private void removeMaceFromPlayer(Player player) {
        player.getInventory().remove(MACE_MATERIAL);
        player.sendMessage(NOTIFICATION_MESSAGE);
    }

    /**
     * Check if a player can have a mace
     */
    private boolean canPlayerHaveMace(Player player) {
        if (!maceExists) {
            return true;
        }
        
        return player.getUniqueId().equals(maceOwnerUUID);
    }

    /**
     * Handle player join event to check for maces
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // If player has a mace but shouldn't, remove it
        if (playerHasMace(player) && !canPlayerHaveMace(player)) {
            removeMaceFromPlayer(player);
        }
        
        // If player is the owner and has a mace, update status
        if (player.getUniqueId().equals(maceOwnerUUID) && playerHasMace(player)) {
            maceExists = true;
        }
    }

    /**
     * Handle crafting events to prevent crafting a second mace
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (event.getRecipe().getResult().getType() == MACE_MATERIAL) {
            if (maceExists) {
                Player player = (Player) event.getWhoClicked();
                if (!canPlayerHaveMace(player)) {
                    event.setCancelled(true);
                    player.sendMessage(NOTIFICATION_MESSAGE);
                }
            } else if (event.getWhoClicked() instanceof Player) {
                // First mace being crafted
                updateMaceOwner((Player) event.getWhoClicked());
            }
        }
    }

    /**
     * Handle item pickup events to prevent picking up a second mace
     */
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        
        if (item.getItemStack().getType() == MACE_MATERIAL) {
            if (maceExists) {
                if (!canPlayerHaveMace(player)) {
                    event.setCancelled(true);
                    player.sendMessage(NOTIFICATION_MESSAGE);
                }
            } else {
                // First mace being picked up
                updateMaceOwner(player);
            }
        }
    }

    /**
     * Handle item drop events to track mace ownership
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Item droppedItem = event.getItemDrop();
        
        if (droppedItem.getItemStack().getType() == MACE_MATERIAL) {
            if (player.getUniqueId().equals(maceOwnerUUID)) {
                // The mace owner dropped the mace
                // We'll keep tracking it until someone else picks it up
            }
        }
    }

    /**
     * Handle inventory click events to prevent duplication through inventory manipulation
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem != null && clickedItem.getType() == MACE_MATERIAL) {
            if (maceExists && !canPlayerHaveMace(player)) {
                // Prevent inventory manipulation of maces for non-owners
                if (event.getInventory().getType() != InventoryType.PLAYER) {
                    event.setCancelled(true);
                    player.sendMessage(NOTIFICATION_MESSAGE);
                }
            }
        }
    }

    /**
     * Handle commands
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("checkmace")) {
            if (!maceExists) {
                sender.sendMessage("§eThere is no mace on the server currently.");
                return true;
            }
            
            Player owner = Bukkit.getPlayer(maceOwnerUUID);
            if (owner != null) {
                sender.sendMessage("§eThe mace is currently held by §6" + owner.getName() + "§e.");
            } else {
                sender.sendMessage("§eThe mace owner is offline or the mace may be lost.");
            }
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("resetmace")) {
            if (!sender.hasPermission("epiconemace.reset")) {
                sender.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }
            
            maceExists = false;
            maceOwnerUUID = null;
            saveMaceStatus();
            sender.sendMessage("§aThe mace status has been reset. A new mace can now be crafted.");
            return true;
        }
        
        return false;
    }
}