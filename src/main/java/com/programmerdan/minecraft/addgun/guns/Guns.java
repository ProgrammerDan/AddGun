package com.programmerdan.minecraft.addgun.guns;

import static com.programmerdan.minecraft.addgun.guns.Utilities.detailedHitBoxLocation;
import static com.programmerdan.minecraft.addgun.guns.Utilities.getGunData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.sigmoid;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import com.google.common.collect.Sets;
import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.AmmoType;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Clip;
import com.programmerdan.minecraft.addgun.listeners.PlayerListener;

public class Guns implements Listener {

	/**
	 * Using each gun's bulletTag, maps a bullet fired back to the gun type that fired it.
	 * 
	 */
	private Map<String, StandardGun> bulletToGunMap = new ConcurrentHashMap<>();
	
	/**
	 * Here we keep a special list of registered guntypes.
	 */
	private Map<Material, Set<StandardGun>> gunMap = new ConcurrentHashMap<>();

	/**
	 * this keeps track of travel paths for bullets (TODO: refactor)
	 */
	private Map<UUID, Location> travelPaths = new ConcurrentHashMap<>();
	
	/**
	 * This keeps track of which Bullet type is represented by the inflight bullet.
	 * 
	 * TODO: evaluate refactoring and adding Bullet type as metadata.
	 */
	private Map<UUID, Bullet> inFlightBullets = new ConcurrentHashMap<>();
	
	private final ScheduledExecutorService scheduler;

	public Guns() {
		scheduler = Executors.newScheduledThreadPool(5);
	}

	/**
	 * It hit the ground maybe!
	 * 
	 * @param event the impact event, we ignore any that aren't just blockhits
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitGroundEvent(ProjectileHitEvent event) {
		if (!(event.getEntity() instanceof Projectile)) return;
		if (event.getHitBlock() == null) return;

		Projectile bullet = (Projectile) event.getEntity();
		
		StandardGun gun = bulletToGunMap.get(bullet.getName());
		if (gun == null) return; // not a bullet from a gun.
		
		Location begin = this.travelPaths.remove(bullet.getUniqueId());
		Bullet bulletType = this.inFlightBullets.remove(bullet.getUniqueId());
		
		if (begin == null || bulletType == null) {
			AddGun.getPlugin().debug("Warning: bullet {1} claiming to be {0} but untracked -- from unloaded chunk?", gun.getBulletTag(), bullet.getUniqueId());
			//bullet.remove();
			return;
		}
		
		if (!bullet.getType().equals(bulletType.getEntityType())) {
			AddGun.getPlugin().debug("Bullet {1} matching {0} but has different type?!", bulletType.getName(), bullet.getUniqueId());
			//bullet.remove();
			return;
		}

		Material block = event.getHitBlock().getType();
		if (!block.isSolid() && block.isTransparent()) {
			if (Material.LAVA.equals(block) || Material.STATIONARY_LAVA.equals(block)) {
				// lava, it dies.
			} else {
				// respawn it
				/*Location newBegin = event.getHitBlock().getLocation().clone();
				if (hit instanceof Damageable) {
					Damageable dhit = (Damageable) hit;
					newBegin.add(bullet.getVelocity().normalize().multiply(dhit.getWidth() * 2));
				} else {
					newBegin.add(bullet.getVelocity().normalize().multiply(1.42)); // diagonalize!
				}
				AddGun.getPlugin().debug(" Just Missed at location {0}, spawning continue at {1} with velocity {2}", 
						end, newBegin, bullet.getVelocity());
				
				Projectile continueBullet = gun.shoot(newBegin, bulletType, bullet.getShooter(), bullet.getVelocity(), true);
				
				gun.postMiss(whereEnd, hit, bullet, continueBullet, bulletType);
*/	
			}
		}
		
		Location end = event.getHitBlock().getLocation().clone().add(0.5, 0.5, 0.5);

		AddGun.getPlugin().debug("Warning: bullet {1} of {0} hit ground {2}", gun.getBulletTag(), bullet.getUniqueId(), end);
		
		//gun.flightPath(begin, end, bulletType, true);
		
		gun.postHit(new HitDigest(HitPart.MISS, end), null, bullet, bulletType );

		//bullet.remove();
		return;
	}
	
	/**
	 * something probably!
	 * 
	 * @param event the hit event.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitEvent(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Projectile)) return;
		Projectile bullet = (Projectile) event.getDamager();
		
		StandardGun gun = bulletToGunMap.get(bullet.getName());
		if (gun == null) return; // not a bullet from a gun.
		
		Location begin = this.travelPaths.remove(bullet.getUniqueId());
		Bullet bulletType = this.inFlightBullets.remove(bullet.getUniqueId());
		if (begin == null || bulletType == null) {
			AddGun.getPlugin().debug("Warning: bullet {1} claiming to be {0} but untracked -- from unloaded chunk?", gun.getBulletTag(), bullet.getUniqueId());
			bullet.remove();
			event.setCancelled(true);
			return;
		}
		
		if (!bullet.getType().equals(bulletType.getEntityType())) {
			AddGun.getPlugin().debug("Bullet {1} matching {0} but has different type?!", bulletType.getName(), bullet.getUniqueId());
			bullet.remove();
			event.setCancelled(true);
			return;
		}

		Entity hit = event.getEntity();

		HitDigest whereEnd = detailedHitBoxLocation(bullet.getLocation().clone(), bullet.getVelocity(), hit);
		Location end = whereEnd.hitLocation;

		if (HitPart.MISS.equals(whereEnd.nearestHitPart)) {
			gun.nearMiss(whereEnd, hit, bullet, bulletType);
		} else {
			gun.preHit(whereEnd, hit, bullet, bulletType);
		}
		
		// in general we remove this instance of the bullet from the world, and cancel the event so any "normal" damage doesn't happen
		bullet.remove();
		event.setCancelled(true); // hmmm. TODO: check

		if (HitPart.MISS.equals(whereEnd.nearestHitPart)) {
			Location newBegin = end.clone();
			if (hit instanceof Damageable) {
				Damageable dhit = (Damageable) hit;
				newBegin.add(bullet.getVelocity().normalize().multiply(dhit.getWidth() * 2));
			} else {
				newBegin.add(bullet.getVelocity().normalize().multiply(1.42)); // diagonalize!
			}
			AddGun.getPlugin().debug(" Just Missed at location {0}, spawning continue at {1} with velocity {2}", 
					end, newBegin, bullet.getVelocity());
			
			Projectile continueBullet = gun.shoot(newBegin, bulletType, bullet.getShooter(), bullet.getVelocity(), true);
			
			gun.postMiss(whereEnd, hit, bullet, continueBullet, bulletType);

			//gun.flightPath(begin, end, bulletType, false);
		} else {
			if (hit instanceof Damageable) {
				Damageable dhit = (Damageable) hit;
				AddGun.getPlugin().debug("Processing damage for {0} at {1} due to {2} intersection with {3} bullet", dhit,
						dhit.getLocation(), whereEnd.nearestHitPart, bulletType.getName());
				gun.manageDamage(whereEnd, dhit, bullet, bulletType);
			} else {
				AddGun.getPlugin().debug("Processing damage for {0} due to intersection with {1} bullet", hit, bulletType.getName());
				gun.manageHit(whereEnd, hit, bullet, bulletType);
			}
			
			gun.postHit(whereEnd, hit, bullet, bulletType);
			
			//gun.flightPath(begin, end, bulletType, true);
		}
	}

	
	/**
	 * This shoots the gun. 
	 * 
	 * Optionally, accuracy is based on how long you've been still and if you're crouching.
	 * 
	 * If you aren't crouching and you haven't been still... well, you're unlikely to hit your target.
	 * 
	 * If you are crouching, and have been still for a while, you'll do well.
	 * 
	 * @param event the interact event with this gun.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void gunPlayerInteractEvent(PlayerInteractEvent event) {
		if (!event.hasItem() || !EquipmentSlot.HAND.equals(event.getHand()))
			return;
		
		ItemStack item = event.getItem();
		StandardGun gun = findGun(item);
		if (gun == null) // can't match it
			return;
		
		Map<String, Object> gunData = getGunData(item);
		
		Player player = event.getPlayer();

		event.setUseInteractedBlock(Event.Result.DENY);
		event.setCancelled(true);
		
		if (!gun.isAlive(gunData)) {
			player.sendMessage(
					ChatColor.AQUA + gun.getName() + ChatColor.RED + " needs repair before it can be used again!");
			return;
		}
		
		AmmoType gunType = (AmmoType) gunData.get("type");
		
		if (!AmmoType.INVENTORY.equals(gunType) && !gun.isLoaded(gunData)) {
			player.sendMessage(
					ChatColor.AQUA + gun.getName() + ChatColor.RED + " is out of ammo, reload it!");
			return;
		}
		
		Bullet bulletType = AmmoType.INVENTORY.equals(gunType) ? gun.getAmmo(player) : gun.getAmmo(gunData);
		
		if (bulletType == null) {
			player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.RED + " has no useable ammo!");
			return;
		}
		
		if (!gun.hasFuel(player, bulletType)) {
			player.sendMessage(
					ChatColor.AQUA + gun.getName() + ChatColor.RED + " requires more fuel (XP) to fire then you have!");
			return;
		}

		Long nextUse = gun.getCooldown(player.getUniqueId());
		if (nextUse == null || nextUse < System.currentTimeMillis()) {
			gun.setCooldown(player.getUniqueId(), System.currentTimeMillis() + gun.getCooldown());

			// TODO: fire an event off here to see if anyone wants to prevent firing
			// FireStandardGunEvent event = new FireStandardGunEvent(gun, bulletType, entity)
			// Bukkit.getPluginManager().callEvent(event);
			// if (event.isCancelled()) { /* send message */ return; }
			
			// consume bullet and XP now.
			if (!gun.payForShot(player, bulletType, item, gunData, event.getHand())) {
				player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.RED + " jammed! Bullet and fuel wasted.");
			} else {
				
				// test for misfire
				if (gun.misfire(player, bulletType, item, gunData, event.getHand())) {
					// test for blowout
					if (gun.blowout(player, bulletType, item, gunData, event.getHand())) {
						player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.RED + " explosively misfired! Please repair the gun before continuing");
					} else {
						player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.RED + " misfired! Try again.");
					}
				} else {
					// based on their stillness and settledness.
					double accuracy = computeAccuracyFor(gun, bulletType, player);
					
					double offset = player.isSneaking() ? 1.2d : 1.35d;
					Vector bbOff = player.getEyeLocation().getDirection().normalize();
					bbOff.multiply(player.getWidth() / 2);
					Location origin = player.getLocation().clone().add(bbOff.getX(), offset, bbOff.getZ());
					
					Location baseLocation = player.getEyeLocation().clone();
					
					double minJitter = gun.getMinMissRadius() + bulletType.getMinMissRadius();
					double maxJitter = gun.getMaxMissRadius() + bulletType.getMaxMissRadius();
					
					if (minJitter < 0.0d) {
						minJitter = 0.0d;
					}
					if (maxJitter < 0.0d) {
						maxJitter = 0.0d;
					}
					if (maxJitter < minJitter) {
						maxJitter = minJitter;
					}
					
					double rand1 = Math.random() - 0.5;
					double rand2 = Math.random() - 0.5;
					
					float yawJitter = (float) (((rand1 * (maxJitter - minJitter)) + Math.signum(rand1) * minJitter) * accuracy);
					float pitchJitter = (float) (((rand2 * (maxJitter - minJitter)) + Math.signum(rand2) * minJitter) * accuracy);
					baseLocation.setYaw(baseLocation.getYaw() + yawJitter);
					baseLocation.setPitch(baseLocation.getPitch() + pitchJitter);
					if (accuracy > 0.25) {
						player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.RED + " isn't easy to aim. Crouch and hold still to improve accuracy.");
					} else if (accuracy < 0.01) {
						// TODO: remove
						player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.GREEN + " is shooting straight, good work.");
					}
					
					double speed = (gun.getMaxSpeed() - gun.getMinSpeed()) * Math.random() + gun.getMinSpeed();
					
					Vector baseVector = baseLocation.getDirection().normalize().multiply(speed);
	
					/*if (player.hasPermission("addgun.data")) {
						player.sendMessage(ChatColor.GOLD + String.format("Shot specifics | accuracy: %.5f | yawV: %.5f | pitchV: %.5f | velocity: %.5f,%.5f,%.5f",
								accuracy, yawJitter, pitchJitter, baseVector.getX(), baseVector.getY(), baseVector.getZ()));
					}*/
	
					Projectile bullet = gun.shoot(origin, bulletType, player, baseVector, false);
					
					AddGun.getPlugin().debug(" Spawning new bullet at {0} with velocity {1}, acc {2}, yaw {3}, pitch {4}", bullet.getLocation(),
							bullet.getVelocity(), accuracy, yawJitter, pitchJitter);
					Location loc = bullet.getLocation().clone();
					travelPaths.put(bullet.getUniqueId(), loc);
					inFlightBullets.put(bullet.getUniqueId(), bulletType);
					
					gun.postShoot(loc, player, bullet, bulletType);
					
					scheduler.scheduleWithFixedDelay(new Runnable() {
						private Location last = loc.clone();
						private long lastUpdate = System.currentTimeMillis();
						@Override
						public void run() {
							if (bullet != null && !bullet.isDead() && bullet.isValid()) {
								UUID bID = bullet.getUniqueId();
								Bullet definition = inFlightBullets.get(bID);
								
								AddGun.getPlugin().debug("bullet {0} at {1} with V {2}", definition.getName(), bullet.getLocation(), bullet.getVelocity());
								Location now = bullet.getLocation().clone();
								StandardGun gun = bulletToGunMap.get(bullet.getName());
								if (now.equals(last)) {
									if (System.currentTimeMillis() - lastUpdate > 250l) {
										gun.flightPath(last.clone(), now.clone(), definition, true);
										bullet.remove();
										travelPaths.remove(bID);
										inFlightBullets.remove(bID);
										return;
									}
								} else {
									lastUpdate = System.currentTimeMillis();
									gun.flightPath(last.clone(), now.clone(), definition, false);
									
									last = now.clone();
								}
								
								Chunk chunk = now.add(bullet.getVelocity()).getChunk();
								if (!chunk.isLoaded()) {
									bullet.remove();
									travelPaths.remove(bID);
									inFlightBullets.remove(bID);
									return;
								}
							} else {
								AddGun.getPlugin().debug("a bullet died");
								throw new RuntimeException("bullet term");
							}
						}
					}, 0l, 50l, TimeUnit.MILLISECONDS);
					
					player.setCooldown(gun.getMinimalGun().getType(), (int) (gun.getCooldown() / 50));

					// jerk player's view back and reset still
					gun.knockback(player, baseLocation.getDirection());

					AddGun.getPlugin().getPlayerListener().resetStillSince(player.getUniqueId());
				}
			}
		} else {
			player.sendMessage(ChatColor.AQUA + gun.getName() + ChatColor.RED + " isn't ready to fire, wait another "
					+ ChatColor.WHITE + String.format("%.1fs", ((float) ((long) ((nextUse - System.currentTimeMillis()) / 100))/10f)));

			player.setCooldown(gun.getMinimalGun().getType(), (int) ((nextUse - System.currentTimeMillis()) / 50));
		}
	}

	/**
	 * Prevent using the gun-item to damage blocks (cancels even damaging)
	 * @param event the damage event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void preventBlockDamageWithGun(BlockDamageEvent event) {
		if (event.getItemInHand() == null)
			return;
		
		ItemStack item = event.getItemInHand();
		StandardGun gun = findGun(item);
		if (gun == null) // can't match it
			return;
		
		event.setCancelled(true);
	}

	/**
	 * Prevent using the gun-item to break blocks
	 * @param event the break event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void preventBlockBreakWithGun(BlockBreakEvent event) {
		if (event.getPlayer() == null || event.getPlayer().getInventory().getItemInMainHand() == null)
			return;
		
		ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
		StandardGun gun = findGun(item);
		if (gun == null) // can't match it
			return;
		
		event.setCancelled(true);
	}

	/**
	 * Doesn't seem to work right now but basically, if you smack someone over the head
	 * with the gun instead of shoot it, do less damage.
	 * 
	 * @param event The hit event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void weakDamageDirectWithGun(EntityDamageByEntityEvent event) {
		if (event.getDamager() instanceof LivingEntity) {
			LivingEntity damager = (LivingEntity) event.getDamager();
			EntityEquipment equips = damager.getEquipment();
			StandardGun gun = findGun(equips.getItemInMainHand());
			if (gun != null) {
				// modify damage!
				event.setDamage(gun.getBluntDamage());
			}
		}
	}


	/**
	 * Registers a gun for future use.
	 * 
	 * @param gun the gun.
	 */
	public void registerGun(StandardGun gun) {
		ItemStack gunItem = gun.getMinimalGun();
		
		gunMap.compute(gunItem.getType(), (type, set) -> {
			if (set == null) {
				set = Sets.newConcurrentHashSet();
			}
			set.add(gun);
			return set;
		});
		
		bulletToGunMap.put(gun.getBulletTag(), gun);
	}

	/**
	 * This handles load / unload events.
	 * 
	 * @param event The inventory click event
	 * 
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void interactWeaponEvent(InventoryClickEvent event) {
		if (!(InventoryAction.SWAP_WITH_CURSOR.equals(event.getAction()) || 
				InventoryAction.PICKUP_ALL.equals(event.getAction()) || 
				InventoryAction.PICKUP_HALF.equals(event.getAction()) || 
				InventoryAction.PICKUP_SOME.equals(event.getAction()) || 
				InventoryAction.PICKUP_ONE.equals(event.getAction())) || !event.isRightClick()) {
			return;
		}
		
		//HumanEntity human = event.getWhoClicked();

		ItemStack current = event.getCurrentItem();
		ItemStack cursor = event.getCursor();
		
		StandardGun currentGun = findGun(current);
		if (currentGun == null) return;
		
		Bullet cursorBullet = null;
		Clip cursorClip = null;
		
		if (cursor != null && !Material.AIR.equals(cursor.getType())) {
			cursorClip = AddGun.getPlugin().getAmmo().findClip(cursor);
			cursorBullet = AddGun.getPlugin().getAmmo().findBullet(cursor);
			if (cursorClip != null || cursorBullet != null) {
				// load / swap event.
				ItemStack[] outcome = currentGun.loadAmmo(current, cursor);
				event.setCurrentItem(outcome[0]);
				event.setCursor(outcome[1]); // why tf is this deprecated?!
				event.setResult(Result.DENY);
			}
		} else {
			// unload event.
			ItemStack[] outcome = currentGun.unloadAmmo(current);
			event.setCurrentItem(outcome[0]);
			event.setCursor(outcome[1]); // why tf is this deprecated?!
			event.setResult(Result.DENY);
		}
	}
	
	/**
	 * This tries to keep player from equipping more then one of the Gun.
	 * 
	 * @param event The inventory click event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
	public void equipWeaponEvent(InventoryClickEvent event) {
		HumanEntity human = event.getWhoClicked();

		ItemStack current = event.getCurrentItem();
		ItemStack cursor = event.getCursor();
		
		StandardGun currentGun = findGun(current);
		StandardGun cursorGun = findGun(cursor);
		StandardGun denyGun = null;
		
		if (currentGun == null & cursorGun == null) return;
		
		if (event.getInventory() != null && !InventoryType.CRAFTING.equals(event.getInventory().getType())) {
			if (event.getClickedInventory() != null && !InventoryType.PLAYER.equals(event.getClickedInventory().getType())) {
				// Player has some inventory open, and they do a click in that other inventory.
				// So for these, we want to identify cases where the player has the item in cursor and is placing it into their inventory.
				// if they don't have one yet, we're OK to continue, otherwise, lets check.
				switch(event.getAction()) {
				case MOVE_TO_OTHER_INVENTORY: // shift-click
				case HOTBAR_SWAP: // press # key while hovered
				case HOTBAR_MOVE_AND_READD: // press # key where target has item
					if (currentGun != null && currentGun.isLimitToOne() && currentGun.hasGun(human)) { // "current" gun & already in inv
						event.setResult(Result.DENY);
						denyGun = currentGun;
					}
					break;
				default: 
					break;
				}
			} else if (event.getClickedInventory() != null && InventoryType.PLAYER.equals(event.getClickedInventory().getType())) {
				switch(event.getAction()) {
				case PLACE_ALL:
				case PLACE_ONE:
				case PLACE_SOME:
				case SWAP_WITH_CURSOR:
					if (cursorGun != null && cursorGun.isLimitToOne() && cursorGun.hasGun(human)) { // "cursor" gun & already in inv
						event.setResult(Result.DENY);
						denyGun = cursorGun;
					}
					break;
				default:
					break;
				}
			}
		}

		if (Result.DENY.equals(event.getResult())) {
			human.sendMessage(ChatColor.RED + "A " + ChatColor.AQUA + denyGun.getName() + ChatColor.RED + " is already in inventory, you cannot equip more then one.");
		} else {
			boolean isEquip = false;
			StandardGun equpGun = null;
			switch(event.getAction()) {
			case PLACE_ALL:
			case PLACE_ONE:
			case PLACE_SOME:
			case SWAP_WITH_CURSOR:
				if (cursorGun != null && SlotType.QUICKBAR.equals(event.getSlotType())) {
					isEquip = true;
					equpGun = cursorGun;
				}
				break;
			case MOVE_TO_OTHER_INVENTORY:
			case HOTBAR_MOVE_AND_READD:
			case HOTBAR_SWAP:
				if (currentGun != null) {
					isEquip = true;
					equpGun = currentGun;
				}
				break;
			default:
				break;
			}
			if (equpGun != null && equpGun.isCooldownOnEquip() && isEquip) {
				final StandardGun equipGun = equpGun;
				Bukkit.getScheduler().runTask(AddGun.getPlugin(), new Runnable() {
					@Override
					public void run() {
						PlayerInventory inv = human.getInventory();
						for (int i = 0; i < 9; i++) { // check hotbar
							if (equipGun.isGun(inv.getItem(i))) {
								human.sendMessage(ChatColor.GREEN + "A " + ChatColor.AQUA + equipGun.getName() + ChatColor.GREEN + " has been equipped!");
								equipGun.setCooldown(human.getUniqueId(), System.currentTimeMillis() + equipGun.getCooldown());
								human.setCooldown(equipGun.getMinimalGun().getType(), (int) (equipGun.getCooldown() / 50));
								break;
							}
						}
					}
				});
			}
			
		}
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void equipWeaponEvent(InventoryDragEvent event) {
		HumanEntity human = event.getWhoClicked();
		
		Inventory inv = event.getInventory();
		ItemStack prior = event.getOldCursor();
		StandardGun priorGun = findGun(prior); 
		
		// Don't really care what sort of drag, if we have a gun in inv and this was a gun, deny.
		if (priorGun != null && inv != null && !InventoryType.PLAYER.equals(inv.getType()) && priorGun.isLimitToOne() && priorGun.hasGun(human)) {
			event.setResult(Result.DENY);
			// yes this will prevent drag style of weapon in chest invs. Oh well.
		}
		
	}

	/**
	 * This prevents from picking up a gun if you already have one
	 * 
	 * @param event The pickup event.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void pickupWeaponEvent(EntityPickupItemEvent event) {
		if (event.getItem() == null) return;
		StandardGun gun = findGun(event.getItem().getItemStack());
		if (gun != null && gun.isLimitToOne() && gun.hasGun(event.getEntity())) {
			event.setCancelled(true);
			gun.optionallyWarn(event.getEntity(), ChatColor.RED + "A " + ChatColor.AQUA + gun.getName() + ChatColor.RED + " is already in inventory, you cannot pick up another.");
		}
	}

	/**
	 * This eliminates pending bullets on chunk unload
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void chunkUnloadClearBullets(ChunkUnloadEvent event) {
		if (event.getChunk() == null) return;
		Entity[] entities = event.getChunk().getEntities();
		for (Entity e : entities) {
			if (inFlightBullets.containsKey(e.getUniqueId())) {
				inFlightBullets.remove(e.getUniqueId());
				travelPaths.remove(e.getUniqueId());
				e.remove();
			}
		}
	}
	
	/**
	 * Using a supplied item, identifies which gun, or if none, null.
	 * 
	 * @param gun the ItemStack to check
	 * @return a StandardGun or null.
	 */
	public StandardGun findGun(ItemStack gun) {
		if (gun == null) return null;
		
		Set<StandardGun> set = gunMap.get(gun.getType());
		if (set == null) return null;
		// TODO: Can we do better?
		for (StandardGun sgun : set) {
			if (sgun.isGun(gun)) {
				return sgun;
			}
		}
		return null;
	}

	/**
	 * Returns a value from 1.0 to 0.0. Zero is best, least jitter. One is worst, most jitter.
	 * @param gun the Gun firing
	 * @param bullet the bullet being fired
	 * @param entity the entities UUID as used by tracking.
	 * @return value from 0 to 1
	 */
	public double computeAccuracyFor(StandardGun gun, Bullet bullet, LivingEntity entity) {
		// TODO: factor in bullet and gun
		double internalAccuracy = computeAccuracyFor(entity.getUniqueId(), gun, bullet);
		
		// TODO: fire an event off here to see if anyone wants to modify jitter
		// DetermineAccuracyEvent event = new DetermineAccuracyEvent(gun, bullet, entity, internalAccuracy)
		// Bukkit.getPluginManager().callEvent(event);
		// internalAccuracy = event.getAccuracy();
		
		return internalAccuracy;
	}
	

	/**
	 * Internally computes jitter based only on stillness and sneakness.
	 * 
	 * @param entity the entity's UUID to check
	 * @return value from 0 to 1 where 1 is worse and 0 is best
	 */
	private double computeAccuracyFor(UUID entity, StandardGun gun, Bullet bullet) {
		long now = System.currentTimeMillis();
		PlayerListener listener = AddGun.getPlugin().getPlayerListener();
		Long sneak = listener.getSneakingSince(entity);
		Long still = listener.getStillSince(entity);
		
		double base = 1.0d;
		if (sneak == null && still == null) { // not sneaking, not still.
			return 1.0d;
		}
		
		double combineSneakInflection = gun.getSneakInflection() + bullet.getSneakInflection();
		if (combineSneakInflection < 0.0d) combineSneakInflection = 0.0d;
		
		double combineStillInflection = gun.getStillInflection() + bullet.getStillInflection();
		if (combineStillInflection < 0.0d) combineStillInflection = 0.0d;
		
		double combineSneakSpread = gun.getSneakSpread() + bullet.getSneakSpread();
		if (combineSneakSpread < 0.00001d) combineSneakSpread = 0.00001d;
		
		double combineStillSpread = gun.getStillSpread() + bullet.getStillSpread();
		if (combineStillSpread < 0.00001d) combineStillSpread = 0.00001d;
		
		if (sneak != null) { // sneaking
			base -= sigmoid((now - sneak) / 1000.0d, combineSneakInflection, 0.25d, combineSneakSpread);
		}
		if (still != null) { // still
			base -= sigmoid((now - still) / 1000.0d, combineStillInflection, 0.25d, combineStillSpread);
		}
		
		return base > 0.0d ? base : 0.0d;
	}
	
	/**
	 * True is guns are configured, false otherwise.
	 * @return true for yes
	 */
	public boolean hasGuns() {
		return !this.gunMap.isEmpty();
	}

	
	private transient Set<String> gunNameList = null;
	/**
	 * Gets all guns by their name. Caches the answer.
	 * 
	 * @return the list of gun names
	 */
	public Set<String> getGunNames() {
		if (gunNameList != null) return gunNameList;
		Set<String> ret = new HashSet<String>();
		if (hasGuns() ) {
			for (Material mat : gunMap.keySet()) {
				for (StandardGun gun : gunMap.get(mat)) {
					ret.add(gun.getName());
				}
			}
		}
		gunNameList = ret;
		return ret;
	}
	
	/**
	 * Command accessor to get a StandardGun by name
	 * 
	 * @param name the name to lookup
	 * @return a StandardGun or null.
	 */
	public StandardGun getGun(String name) {
		if (hasGuns() ) {
			for (Material mat : gunMap.keySet()) {
				for (StandardGun gun : gunMap.get(mat)) {
					if (gun.getName().equals(name)) {
						return gun;
					}
				}
			}
		}
		return null;
	}
}
