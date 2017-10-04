package com.programmerdan.minecraft.addgun.listeners;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.guns.Animation;
import com.programmerdan.minecraft.addgun.guns.Recticle;
import com.programmerdan.minecraft.addgun.guns.ShotTracker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
	private Map<UUID, Long> sneakingEnd = new ConcurrentHashMap<>();

	
	/**
	 * If the gun uses stability mechanics, tracks which players are running and since when.
	 */
	private Map<UUID, Long> walkingSince = new ConcurrentHashMap<>();
	private Map<UUID, Long> walkingEnd = new ConcurrentHashMap<>();

	/**
	 * If the gun uses stability mechanics, tracks which players are running and since when.
	 */
	private Map<UUID, Long> sprintingSince = new ConcurrentHashMap<>();
	private Map<UUID, Long> sprintingEnd = new ConcurrentHashMap<>();
	
	/**
	 * Tracks player flight.
	 */
	private Map<UUID, Long> glidingSince = new ConcurrentHashMap<>();
	private Map<UUID, Long> glidingEnd = new ConcurrentHashMap<>();
	
	/**
	 * Tracks when players enter or leave vehicles
	 */
	private Map<UUID, Long> vehicleSince = new ConcurrentHashMap<>();
	private Map<UUID, Long> vehicleLeft = new ConcurrentHashMap<>();

	/**
	 * Tracks when player is still
	 */
	private Map<UUID, Long> stillSince = new ConcurrentHashMap<>();
	private Map<UUID, Long> stillEnd = new ConcurrentHashMap<>();
	
	/**
	 * Tracks various player head transmissions
	 */
	private final ScheduledExecutorService scheduler;
	private Map<UUID, ScheduledFuture<?>> activeKnockbackTasks = new ConcurrentHashMap<>();
	private Map<UUID, ScheduledFuture<?>> aimIndicators = new ConcurrentHashMap<>();
	
	private Map<UUID, ScheduledFuture<?>> activeShotTasks = new ConcurrentHashMap<>();
	private Map<UUID, ShotTracker> activeShotTrackers = new ConcurrentHashMap<>();
	
	private Map<UUID, ScheduledFuture<?>> activeStillTasks = new ConcurrentHashMap<>();
	
	public PlayerListener(FileConfiguration config) {
		plugin = AddGun.getPlugin();
		scheduler = Executors.newScheduledThreadPool(50);
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	public void shutdown() {
		sneakingSince.clear();
		walkingSince.clear();
		glidingSince.clear();
		vehicleSince.clear();
		sprintingSince.clear();
		stillSince.clear();
		sneakingEnd.clear();
		walkingEnd.clear();
		glidingEnd.clear();
		vehicleLeft.clear();
		sprintingEnd.clear();
		stillEnd.clear();
		
		scheduler.shutdown();
	}
	
	/**
	 * Each shot has a unique impact on the player's ability to aim. These impacts are recorded and
	 * managed.
	 * 
	 * @param player
	 * @return
	 */
	public double getShotImpact(UUID player) {
		ShotTracker tracker = activeShotTrackers.get(player);
		if (tracker == null) {
			return 0.0d;
		} else {
			return tracker.getAimOffset();
		}
	}
	
	/**
	 * Records a new shot impact. This isn't generalized and this API is likely to change as my understanding matures.
	 * 
	 * @param player
	 * @param impact
	 * @param decay
	 * @param ticks
	 */
	public void recordShotImpact(UUID player, double impact, double decay, int ticks) {
		StringBuffer record = new StringBuffer(player.toString());
		ShotTracker tracker = activeShotTrackers.compute(player, (u, s) -> {
			ShotTracker shotTracker = s;
			if (s == null) {
				shotTracker = new ShotTracker();
				record.append(" new ST");
			}
			shotTracker.addShot(impact, decay, ticks);
			record.append(String.format(" add %.5f %.5f %d", impact, decay, ticks));
			return shotTracker;
		});
		
		if (tracker != null) {
			activeShotTasks.compute(player, (p, s) -> {
				if (s == null || (s != null && s.isDone())) {
					record.append(" new scheduler");
					return scheduler.scheduleAtFixedRate(tracker, 50l, 50l, TimeUnit.MILLISECONDS); // we "do by ticks"
				} else {
					record.append(" scheduler active");
					return s;
				}
			});
		}
		AddGun.getPlugin().debug(record.toString());
	}
	
	/**
	 * Returns the time this player started being still, or null
	 * @param player the UUID of the player to check
	 * @return the time since the player started being still
	 */
	public Long getStillSince(UUID player) {
		return stillSince.get(player);
	}
	public Long getStillEnd(UUID player) {
		return stillEnd.get(player);
	}
	
	/**
	 * Returns the time this player started sneaking, or null
	 * @param player the UUID of the player to check
	 * @return the time since the player started sneaking
	 */
	public Long getSneakingSince(UUID player) {
		return sneakingSince.get(player);
	}
	public Long getSneakingEnd(UUID player) {
		return sneakingEnd.get(player);
	}
	
	/**
	 * External hard reset of stillness. Typically used when firing a gun.
	 * @param player the UUID of the player to reset
	 */
	public void resetStillSince(UUID player) {
		sneakingSince.put(player, System.currentTimeMillis());
	}

	/**
	 * This is constructed, but basically picks the most recent time when you stopped being still, sneaking, sprinting, or gliding...
	 * which means you are walking.
	 */
	public Long getWalkingSince(UUID player) {
		Long stillE = stillEnd.get(player);
		if (stillE == null) return null;
		Long sneakE = sneakingEnd.get(player);
		Long latestE = sneakE == null ? stillE : Math.max(sneakE, stillE);
		Long sprintE = sprintingEnd.get(player);
		latestE = sprintE == null ? latestE : Math.max(sprintE, latestE);
		Long glidingE = glidingEnd.get(player);
		latestE = glidingE == null ? latestE : Math.max(glidingE, latestE);
		return latestE;
	}
	
	public Long getSprintingSince(UUID player) {
		return sprintingSince.get(player);
	}
	public Long getSprintingEnd(UUID player) {
		return sprintingEnd.get(player);
	}
	
	public Long getGlidingSince(UUID player) {
		return glidingSince.get(player);
	}
	public Long getGlidingEnd(UUID player) {
		return glidingEnd.get(player);
	}
	
	public Long getVehicleSince(UUID player) {
		return vehicleSince.get(player);
	}
	public Long getVehicleEnd(UUID player) {
		return vehicleLeft.get(player);
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
			sneakingEnd.remove(event.getPlayer().getUniqueId());
		} else {
			/*if (sneakingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " sneak cleared");
			}*/
			sneakingSince.remove(event.getPlayer().getUniqueId());
			sneakingEnd.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				return System.currentTimeMillis();
			});

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
			sprintingEnd.remove(event.getPlayer().getUniqueId());
		} else {
			/*if (sprintingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " sprint cleared");
			}*/
			sprintingSince.remove(event.getPlayer().getUniqueId());
			sprintingEnd.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				return System.currentTimeMillis();
			});
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
			glidingEnd.remove(event.getPlayer().getUniqueId());
		} else {
			/*if (glidingSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) { 
				event.getPlayer().sendMessage(ChatColor.GOLD + " glide cleared");
			}*/
			glidingSince.remove(event.getPlayer().getUniqueId());
			glidingEnd.computeIfAbsent(event.getPlayer().getUniqueId(), u -> {
				return System.currentTimeMillis();
			});
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
			glidingEnd.remove(eventPlayer.getUniqueId());
		} else {
			/*if (glidingSince.containsKey(eventPlayer.getUniqueId()) && eventPlayer.hasPermission("addgun.data")) { 
				eventPlayer.sendMessage(ChatColor.GOLD + " glide cleared");
			}*/
			glidingSince.remove(eventPlayer.getUniqueId());
			glidingEnd.computeIfAbsent(eventPlayer.getUniqueId(), u -> {
				return System.currentTimeMillis();
			});
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
		Player player = event.getPlayer();
		if (event.getFrom().getWorld().equals(event.getTo().getWorld()) &&
				event.getFrom().distanceSquared(event.getTo()) <= .000001) {
			stillSince.computeIfAbsent(player.getUniqueId(), u -> {
				//if (event.getPlayer().hasPermission("addgun.data")) { event.getPlayer().sendMessage(ChatColor.GOLD + " still started"); }
				return System.currentTimeMillis(); 
			});
			stillEnd.remove(player.getUniqueId());
			
			activeStillTasks.computeIfPresent(player.getUniqueId(), (u, f) -> {
				if (f != null && !f.isDone()) {
					f.cancel(true);
				}
				return null;
			});
		} else {
			/*if (stillSince.containsKey(event.getPlayer().getUniqueId()) && event.getPlayer().hasPermission("addgun.data")) {
				event.getPlayer().sendMessage(ChatColor.GOLD + " still cleared");
			}*/
			stillSince.remove(player.getUniqueId());
			stillEnd.computeIfAbsent(player.getUniqueId(), u -> {
				return System.currentTimeMillis(); 
			});
			activeStillTasks.compute(player.getUniqueId(), (u, f) -> {
				if (f != null && !f.isDone()) {
					f.cancel(true);
					
				}
				return scheduler.schedule(new Runnable() {
					final Location last = event.getTo().clone();
					final UUID uuid = player.getUniqueId();
					@Override
					public void run() {
						Player player = Bukkit.getPlayer(uuid);
						if (player != null) {
							Location now = player.getLocation();
							if (now.getWorld().equals(last.getWorld()) &&
									now.distanceSquared(last) <= .000001) {
								stillSince.computeIfAbsent(uuid, u -> {
									return System.currentTimeMillis() - 250l; 
								});
								stillEnd.remove(player.getUniqueId());
							}
						}
					}
				}, 250l, TimeUnit.MILLISECONDS);
			});
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
						stillEnd.remove(e.getUniqueId());
					}
				}
			} else {
				for (Entity e : passengers) {
					if (e instanceof LivingEntity) {		
						stillSince.remove(e.getUniqueId());
						stillEnd.computeIfAbsent(e.getUniqueId(), u -> {
							return System.currentTimeMillis(); 
						});
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
			vehicleLeft.remove(eventPlayer.getUniqueId());
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
			vehicleLeft.computeIfAbsent(eventPlayer.getUniqueId(), u -> {
				return System.currentTimeMillis(); 
			});
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
