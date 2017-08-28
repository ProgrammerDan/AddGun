package com.programmerdan.minecraft.addgun.listeners;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.guns.Animation;
import com.programmerdan.minecraft.addgun.guns.Recticle;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;


/**
 * Handles players, and updating players. NMS backed.
 * 
 * @author ProgrammerDan
 *
 */
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

	/**
	 * If the gun uses stability mechanics, tracks which players are running and since when.
	 */
	private Map<UUID, Long> sprintingSince = new ConcurrentHashMap<>();
	
	/**
	 * Tracks player flight.
	 */
	private Map<UUID, Long> glidingSince = new ConcurrentHashMap<>();
	
	/*/*
	 * Might not use this but tracks falling.
	 */
	//private Map<UUID, Long> fallingSince = new ConcurrentHashMap<>();
	
	/**
	 * Tracks when players enter or leave vehicles
	 */
	private Map<UUID, Long> vehicleSince = new ConcurrentHashMap<>();
	
	/**
	 * Tracks various player head transmissions
	 */
	private Map<UUID, Animation> activeKnockbacks = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	private Map<UUID, ScheduledFuture<?>> activeKnockbackTasks = new ConcurrentHashMap<>();
	private Map<UUID, ScheduledFuture<?>> aimIndicators = new ConcurrentHashMap<>();
	
	public PlayerListener(FileConfiguration config) {
		plugin = AddGun.getPlugin();
		scheduler = Executors.newScheduledThreadPool(10);
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	public void shutdown() {
		sneakingSince.clear();
		stillSince.clear();
		glidingSince.clear();
		vehicleSince.clear();
		sprintingSince.clear();
		scheduler.shutdown();
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
	 * External hard reset of stillness. Typically used when firing a gun.
	 * @param player the UUID of the player to reset
	 */
	public void resetStillSince(UUID player) {
		sneakingSince.put(player, System.currentTimeMillis());
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
				//if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + " sneak started"); }
				this.aimIndicators.compute(event.getPlayer().getUniqueId(), (p, track) -> {
					if (track != null) {
						try {
							track.cancel(true);
						} catch (Exception e) {
							
						}
					}
					return this.scheduler.scheduleAtFixedRate(new Recticle(event.getPlayer()), 0l, (long) Recticle.FRAME_DELAY, TimeUnit.MILLISECONDS);
				});
				return System.currentTimeMillis();
			});
		} else {
			/*if (sneakingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " sneak cleared");
			}*/
			sneakingSince.remove(event.getPlayer().getUniqueId());
			this.aimIndicators.computeIfPresent(event.getPlayer().getUniqueId(), (p, track) -> {
				try {
					track.cancel(true);
				} catch (Exception e) {
					
				}
				return null;
			});
		}
	}

	/**
	 * Keeps track of player sprint; if they are sprinting we track when they sprinted, or, clear if no longer sprinting.
	 * 
	 * @param event
	 *            the sprint toggle event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void toggleSprintEvent(PlayerToggleSprintEvent event) {
		if (event.isSprinting()) {
			sprintingSince.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				//if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + " sprint started"); }
				return System.currentTimeMillis();
			});
		} else {
			/*if (sprintingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " sprint cleared");
			}*/
			sprintingSince.remove(event.getPlayer().getUniqueId());
		}
	}

	/**
	 * Keeps track of player glide; if they are gliding we track when they glided, or, clear if no longer gliding.
	 * 
	 * @param event
	 *            the flying toggle event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void toggleGlideEvent(PlayerToggleFlightEvent event) {
		if (event.isFlying()) {
			glidingSince.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				//if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + "glide started"); }
				return System.currentTimeMillis();
			});
		} else {
			/*if (glidingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " glide cleared");
			}*/
			glidingSince.remove(event.getPlayer().getUniqueId());
		}
	}
	

	/**
	 * Keeps track of player glide part 2; if they are gliding we track when they glided, or, clear if no longer gliding.
	 * 
	 * @param event
	 *            the glide toggle event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void toggleGlideEvent(EntityToggleGlideEvent event) {
		if (!(event.getEntity() instanceof Player)) return;
		Player eventPlayer = (Player) event.getEntity();
		if (event.isGliding()) {
			glidingSince.computeIfAbsent(eventPlayer.getUniqueId(), u -> {
				//if (eventPlayer.hasPermission("addgun.data")) { eventPlayer.sendMessage(ChatColor.GOLD + "glide started"); }
				return System.currentTimeMillis();
			});
		} else {
			/*if (glidingSince.containsKey(eventPlayer.getUniqueId()) && eventPlayer.hasPermission("addgun.data")) { 
				eventPlayer.sendMessage(ChatColor.GOLD + " glide cleared");
			}*/
			glidingSince.remove(eventPlayer.getUniqueId());
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
				//if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + " still started"); }
				return System.currentTimeMillis(); 
			});
		} else {
			/*if (stillSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) {
				event.getPlayer().sendMessage(ChatColor.GOLD + " still cleared");
			}*/
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
	
	/**
	 * We keep track of players entering and exiting vehicles.
	 * 
	 * @param event
	 *  the entrance event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void vehicleEnterEvent(VehicleEnterEvent event) {
		if (event.getEntered() != null && event.getVehicle() != null && event.getEntered() instanceof Player) {
			Player eventPlayer = (Player) event.getEntered();
			vehicleSince.computeIfAbsent(eventPlayer.getUniqueId(), u -> {
				//if (eventPlayer.hasPermission("addgun.data")) {eventPlayer.sendMessage(ChatColor.GOLD + " entered vehicle"); }
				return System.currentTimeMillis(); 
			});
		}
	}

	/**
	 * We keep track of players entering and exiting vehicles.
	 * 
	 * @param event
	 *  the exit event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void vehicleExitEvent(VehicleExitEvent event) {
		if (event.getExited() != null && event.getVehicle() != null && event.getExited() instanceof Player) {
			Player eventPlayer = (Player) event.getExited();
			/*if (vehicleSince.containsKey(eventPlayer.getUniqueId()) && eventPlayer.hasPermission("addgun.data")) {
				eventPlayer.sendMessage(ChatColor.GOLD + " exited vehicle");
			}*/
			vehicleSince.remove(eventPlayer.getUniqueId());
		}
	}

	/**
	 * 
	 * @param player
	 * @param animation
	 */
	public void playAnimation(Player player, Animation animation) {
		if (player == null || animation == null) return;
		
		this.activeKnockbackTasks.compute(player.getUniqueId(), (p, anim) -> {
			if (anim != null) {
				try {
					anim.cancel(true);
				} catch (Exception e) {
					
				}
			}
			return this.scheduler.scheduleAtFixedRate(animation, 0l, (long) Animation.FRAME_DELAY, TimeUnit.MILLISECONDS);
		});
	}
}
