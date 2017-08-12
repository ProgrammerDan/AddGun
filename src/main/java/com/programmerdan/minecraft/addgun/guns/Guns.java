package com.programmerdan.minecraft.addgun.guns;

import static com.programmerdan.minecraft.addgun.guns.Utilities.detailedHitBoxLocation;
import static com.programmerdan.minecraft.addgun.guns.Utilities.getGunData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.sigmoid;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.google.common.collect.Sets;
import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.AmmoType;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
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

			gun.flightPath(begin, end, bulletType, false);
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
			
			gun.flightPath(begin, end, bulletType, true);
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
		if (event.isBlockInHand() || !event.hasItem() || !EquipmentSlot.HAND.equals(event.getHand()))
			return;
		
		ItemStack item = event.getItem();
		StandardGun gun = findGun(item);
		if (gun == null) // can't match it
			return;
		
		Map<String, Object> gunData = getGunData(item);
		
		Player player = event.getPlayer();

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
					
					double offset = player.isSneaking() ? 1.1d : 1.25d;
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
	
					if (player.hasPermission("addgun.data")) {
						player.sendMessage(ChatColor.GOLD + String.format("Shot specifics | accuracy: %.5f | yawV: %.5f | pitchV: %.5f | velocity: %.5f,%.5f,%.5f",
								accuracy, yawJitter, pitchJitter, baseVector.getX(), baseVector.getY(), baseVector.getZ()));
					}
	
					Projectile bullet = gun.shoot(origin, bulletType, player, baseVector, false);
					
					AddGun.getPlugin().debug(" Spawning new bullet at {0} with velocity {1}", bullet.getLocation(),
							bullet.getVelocity());
					Location loc = bullet.getLocation().clone();
					travelPaths.put(bullet.getUniqueId(), loc);
					inFlightBullets.put(bullet.getUniqueId(), bulletType);
					
					gun.postShoot(loc, player, bullet, bulletType);
					
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
		
		// Prevent normal effects of this tool
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setCancelled(true);
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
	 * Using a supplied item, identifies which gun, or if none, null.
	 * 
	 * @param gun the ItemStack to check
	 * @return a StandardGun or null.
	 */
	public StandardGun findGun(ItemStack gun) {
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
}
