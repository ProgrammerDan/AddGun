package com.programmerdan.minecraft.addgun.listeners;

import com.programmerdan.minecraft.addgun.AddGun;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;


public class PlayerListener implements Listener {
	
	private AddGun plugin;
	
	
	/**
	 * If the gun uses stability mechanics, tracks which players are sneaking and since when.
	 */
	private Map<UUID, Long> sneakingSince = new ConcurrentHashMap<>();
	/**
	 * If the gun uses stability mechanics, tracks which players are still and since when.
	 */
	private Map<UUID, Long> stillSince = new ConcurrentHashMap<>();

	
	public PlayerListener(FileConfiguration config) {
		plugin = AddGun.getPlugin();
		
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	public void shutdown() {
		sneakingSince.clear();
		stillSince.clear();
	}
	
	/**
	 * Returns the time this player started being still, or null
	 * @param player the UUID of the player to check
	 * @return the time since the player started being still
	 */
	public Long getStillSince(UUID player) {
		return stillSince.get(player);
	}
	
	/**
	 * Returns the time this player started sneaking, or null
	 * @param player the UUID of the player to check
	 * @return the time since the player started sneaking
	 */
	public Long getSneakingSince(UUID player) {
		return sneakingSince.get(player);
	}
	
	
	/**
	 * Keeps track of player sneaking; if they are sneaking, we track when the
	 * snuck, or, clear if unsnuck.
	 * 
	 * @param event
	 *            the sneak toggle event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void toggleSneakEvent(PlayerToggleSneakEvent event) {
		if (event.isSneaking()) {
			sneakingSince.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + " sneak started"); }
				return System.currentTimeMillis();
			});
		} else {
			if (sneakingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " sneak cleared");
			}
			sneakingSince.remove(event.getPlayer().getUniqueId());
		}
	}

	/**
	 * We keep track of players being still; if they haven't moved much but did
	 * look around, we start tracking, otherwise we clear them.
	 * 
	 * @param event
	 *            the movement event.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void playerMoveEvent(PlayerMoveEvent event) {
		if (event.getFrom().distanceSquared(event.getTo()) <= .000001) {
			stillSince.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + " still started"); }
				return System.currentTimeMillis(); 
			});
		} else {
			if (stillSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) {
				event.getPlayer().sendMessage(ChatColor.GOLD + " still cleared");
			}
			stillSince.remove(event.getPlayer().getUniqueId());
		}
	}

	/**
	 * We keep track of players being still; if they haven't moved much but did
	 * look around, we start tracking, otherwise we clear them.
	 * 
	 * @param event
	 *            the movement event.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void vehicleMoveEvent(VehicleMoveEvent event) {
		if (event.getVehicle() != null) {
			List<Entity> passengers = event.getVehicle().getPassengers();
			if (passengers == null || passengers.isEmpty()) return;

			if (event.getFrom().distanceSquared(event.getTo()) <= .000001) {
				for (Entity e : passengers) {
					if (e instanceof LivingEntity) {
						stillSince.computeIfAbsent(e.getUniqueId(), u -> {
							return System.currentTimeMillis(); 
						});
					}
				}
			} else {
				for (Entity e : passengers) {
					if (e instanceof LivingEntity) {		
						stillSince.remove(e.getUniqueId());
					}
				}
			}
		}
	}
	
}
