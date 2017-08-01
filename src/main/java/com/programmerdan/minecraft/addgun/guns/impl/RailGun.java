package com.programmerdan.minecraft.addgun.guns.impl;

import static com.programmerdan.minecraft.addgun.guns.Utilities.detailedHitBoxLocation;
import static com.programmerdan.minecraft.addgun.guns.Utilities.computeTotalXP;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValueAdapter;
import org.bukkit.util.Vector;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.guns.BasicGun;
import com.programmerdan.minecraft.addgun.guns.HitDigest;
import com.programmerdan.minecraft.addgun.guns.HitPart;

/**
 * Welcome to the next generation of warfare. Say hello to my little friend, the
 * rail gun. <br/>
 * Features:
 * <ul>
 * <li>Configurable passthrough -- bullets are stopped by blocks, not fleshy
 * meattargets
 * <li>Massive damage -- configure instakill if you like. Bypasses all armor.
 * <li>Limited uses -- due to its high power, wears out as you use it. Repair it
 * to go again
 * <li>Power-up -- uses player's XP to "charge up" when equipped
 * <li>Expensive -- uses custom ammo, control how players can find / produce it
 * <li>Heavy -- will drift the player's head pitch down unless crouching
 * <li>Server friendly -- bullets will be removed after a second, to prevent
 * chunkloading from firin' em off
 * <li>... more stuff I'm forgetting
 * </ul>
 * 
 * @author ProgrammerDan
 */
public class RailGun implements BasicGun {

	private Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

	/*
	 * These two maps keep track of player sneaking and stillness, which
	 * determine the accuracy of the weapon
	 */
	private Map<UUID, Long> sneakingSince = new ConcurrentHashMap<>();
	private Map<UUID, Long> stillSince = new ConcurrentHashMap<>();

	private Map<UUID, Location> travelPaths = new ConcurrentHashMap<>();
	private boolean enabled = false;

	private ItemStack gunExample = null;
	private ItemStack bulletExample = null;

	private double maxSpeed = 100.0;
	private int maxUses = 120;
	private int minUses = 20;
	private int damagePerUse = (int) (1562 / maxUses);
	private long cooldown = 500;
	private int xpdraw = 30;
	private double damagePerHit = 100.0d;
	private int passthrough = 3;
	private double bluntDamage = 3.0d;

	private String tag = ChatColor.BLACK + "Gun: "
			+ Integer.toHexString(this.getName().hashCode() + this.getName().length());

	private String bulletTag = ChatColor.BLACK + "Bullet: "
			+ Integer.toHexString(this.getName().hashCode() + this.getName().length());

	public RailGun() {
	}

	@Override
	public void configure(ConfigurationSection config) {
		if (config != null) {
			enabled = true;

			maxSpeed = config.getDouble("maxVelocity", maxSpeed);
			cooldown = config.getLong("cooldown", cooldown);
			xpdraw = config.getInt("xpPerShot", xpdraw);
			minUses = config.getInt("minUses", minUses);
			maxUses = config.getInt("maxUses", maxUses);
			damagePerUse = 1562 / maxUses;
			damagePerHit = config.getDouble("damage", damagePerHit);
			bluntDamage = config.getDouble("blunt", bluntDamage);
			passthrough = config.getInt("passthrough", passthrough);

			ItemStack preExample = new ItemStack(Material.DIAMOND_HOE, 1);
			ItemMeta meta = preExample.getItemMeta();
			meta.setDisplayName("Rail Gun Mark1");
			List<String> lores = meta.getLore();
			if (lores == null) {
				lores = new ArrayList<String>();
			}
			lores.add(gunTag());
			meta.setUnbreakable(false);
			meta.setLore(lores);
			preExample.setItemMeta(meta);

			this.gunExample = preExample;

			preExample = new ItemStack(Material.IRON_NUGGET, 1);
			meta = preExample.getItemMeta();
			meta.setDisplayName("Depleted Uranium Slug");
			lores = meta.getLore();
			if (lores == null) {
				lores = new ArrayList<String>();
			}
			lores.add(bulletTag());
			meta.setLore(lores);
			this.bulletExample = preExample;
		} else {
			enabled = false;
			throw new IllegalArgumentException("RailGun is not configured.");
		}
	}

	@Override
	public String getName() {
		return "RailGun";
	}

	@Override
	public ItemStack getMinimalGun() {
		return gunExample;
	}

	@Override
	public ItemStack getMinimalBullet() {
		return bulletExample;
	}

	@Override
	public boolean isGun(ItemStack toCheck) {
		if (!enabled || toCheck == null)
			return false;

		if (!Material.DIAMOND_HOE.equals(toCheck.getType()))
			return false;

		if (!toCheck.hasItemMeta())
			return false;

		ItemMeta meta = toCheck.getItemMeta();

		if (meta.getLore().contains(gunTag()))
			return true;

		return false;
	}

	@Override
	public boolean hasBullet(LivingEntity entity) {
		if (entity == null || !enabled)
			return false;

		ItemStack[] inv;
		if (entity instanceof InventoryHolder) {
			// complex inventory
			InventoryHolder holder = (InventoryHolder) entity;
			inv = holder.getInventory().getContents();
		} else {
			// simple inventory
			inv = entity.getEquipment().getArmorContents();
		}

		if (inv != null) {
			for (ItemStack item : inv) {
				if (isBullet(item)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasGun(LivingEntity entity) {
		if (entity == null || !enabled)
			return false;

		ItemStack[] inv;
		if (entity instanceof InventoryHolder) {
			// complex inventory
			InventoryHolder holder = (InventoryHolder) entity;
			inv = holder.getInventory().getContents();
		} else {
			// simple inventory
			inv = entity.getEquipment().getArmorContents();
		}

		if (inv != null) {
			for (ItemStack item : inv) {
				if (isGun(item)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isBullet(ItemStack toCheck) {
		if (!enabled || toCheck == null)
			return false;

		if (!Material.IRON_NUGGET.equals(toCheck.getType()))
			return false;

		if (!toCheck.hasItemMeta())
			return false;

		ItemMeta meta = toCheck.getItemMeta();

		if (meta.getLore().contains(bulletTag()))
			return true;

		return false;
	}
	
	/**
	 * Estimates the XP this entity in inventory.
	 * 
	 * @param entity
	 * @return
	 */
	private int getInvXp(LivingEntity entity) {
		if (entity == null || !enabled)
			return 0;

		ItemStack[] inv;
		if (entity instanceof InventoryHolder) {
			// complex inventory
			InventoryHolder holder = (InventoryHolder) entity;
			inv = holder.getInventory().getContents();
		} else {
			// simple inventory
			inv = entity.getEquipment().getArmorContents();
		}

		int total = 0;
		if (inv != null) {
			for (ItemStack item : inv) {
				if (Material.EXP_BOTTLE.equals(item)) {
					total += item.getAmount();
				}
			}
		}
		return total;
	}
	
	/**
	 * Looks through inventory for bullets, and xp, if the entity in question can pay up.
	 * 
	 * @param entity
	 * @return
	 */
	private boolean payForShot(LivingEntity entity) {
		if (entity == null || !enabled) {
			return false;
		}

		if (entity instanceof InventoryHolder) {
			// complex inventory
			InventoryHolder holder = (InventoryHolder) entity;
			boolean foundBullet = false;
			boolean xp = !(entity instanceof Player);
			for (Map.Entry<Integer,? extends ItemStack> bullet : holder.getInventory().all(Material.IRON_NUGGET).entrySet()) {
				ItemStack maybeBullets = bullet.getValue();
				if (isBullet(maybeBullets) && maybeBullets.getAmount() > 0) {
					maybeBullets.setAmount(maybeBullets.getAmount() - 1);
					if (maybeBullets.getAmount() > 0) {
						holder.getInventory().setItem(bullet.getKey(), maybeBullets);
					} else {
						holder.getInventory().clear(bullet.getKey());
					}
					foundBullet = true;
					break;
				}
			}
			
			if (foundBullet && !xp) {
				Player player = (Player) entity;
				int xpNeeds = computeTotalXP(player) - xpdraw; 

				if (xpNeeds < 0) {
					for (Map.Entry<Integer,? extends ItemStack> xpbottle : player.getInventory().all(Material.EXP_BOTTLE).entrySet()) {
						ItemStack maybeXp = xpbottle.getValue();
						if (maybeXp.getAmount() > 0) {
							int xpInStack = maybeXp.getAmount() * AddGun.getPlugin().getXpPerBottle();
							int leftOver = xpInStack + xpNeeds; // (in stack + ( negative leftover )) 
							if (leftOver < 0) { // not fully satisfied.
								xpNeeds += xpInStack; // becomes a smaller negative number
								player.getInventory().clear(xpbottle.getKey());
								if (xpNeeds == 0) { // !! fully satisfied on the money.
									xp = true;
									break;
								}
							} else { // fully satisfied with leftover
								xpInStack += xpNeeds;
								int bottlesLeft = xpInStack / AddGun.getPlugin().getXpPerBottle();
								
								// we set the remainder as a gift back to the player.
								xpNeeds = xpInStack - bottlesLeft * AddGun.getPlugin().getXpPerBottle(); 
								
								if (bottlesLeft > 0) {
									maybeXp.setAmount(bottlesLeft);
									player.getInventory().setItem(xpbottle.getKey(), maybeXp);
								} else {
									player.getInventory().clear(xpbottle.getKey());
								}
								
								xp = true;
								break;
							}
						}
					}
					if (!xp) {
						return false; // LOSSY!!! someone is playing mindgames, let's screw em up.
					}
				}
				player.setLevel(0);
				player.setExp(0.0f);
				player.giveExp(xpNeeds);
			}
			
			return foundBullet && xp;
		} else {
			// simple inventory, no xp
			ItemStack maybeBullets = entity.getEquipment().getItemInOffHand(); 
			if(isBullet(maybeBullets)) {
				if (maybeBullets.getAmount() < 1) {
					return false;
				} else {
					maybeBullets.setAmount(maybeBullets.getAmount() - 1);
					if (maybeBullets.getAmount() > 0) {
						entity.getEquipment().setItemInOffHand(maybeBullets);
					} else {
						entity.getEquipment().setItemInOffHand(null);
					}
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Returns a value from 1.0 to 0.0. Zero is best, least jitter. One is worst, most jitter.
	 * @param entity the entities UUID as used by tracking.
	 * @return value from 0 to 1
	 */
	private double computeAccuracyFor(UUID entity) {
		long now = System.currentTimeMillis();
		Long sneak = this.sneakingSince.get(entity);
		Long still = this.stillSince.get(entity);
		
		double base = 1.0d;
		if (sneak == null && still == null) { // not sneaking, not still.
			return 1.0d;
		}
		if (sneak != null) { // sneaking
			base -= timeSigmoid((now - sneak) / 1000.0d);
		}
		if (still != null) { // still
			base -= timeSigmoid((now - still) / 1000.0d);
		}
		
		return base > 0.0d ? base : 0.0d;
	}
	
	/**
	 * private function to compute a soft sigmoid based on
	 * 0 to 15000 milliseconds elapsed. 
	 * Inflection is at 7500 milliseconds. Asymptotically approaches
	 * .5 as time increases
	 * @param elapsed in fractions of a second
	 * @return
	 */
	private double timeSigmoid(double elapsed) {
		double term = (elapsed - 7.5d) / 2.5d;
		return 0.25d + 0.25 * (term / Math.sqrt(1.0 + term * term));
	}
	
	/**
	 * Checks if this gun has enough health to still fire
	 * 
	 * @param toCheck
	 * @return
	 */
	private boolean isAlive(ItemStack toCheck) {
		if (toCheck.getDurability() > (minUses * damagePerUse))
			return true;
		return false;
	}

	private String gunTag() {
		return tag;
	}

	private String bulletTag() {
		return bulletTag;
	}

	/**
	 * This shoots the gun. Accuracy is based on how long you've been still and if you're crouching.
	 * 
	 * If you aren't crouching and you haven't been still... well, you're unlikely to hit your target.
	 * 
	 * If you are crouching, and have been still for a while, you'll do well.
	 * 
	 * @param event the interact event with this gun.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void gunPlayerInteractEvent(PlayerInteractEvent event) {
		if (!enabled || event.isBlockInHand() || !event.hasItem() || !EquipmentSlot.HAND.equals(event.getHand()))
			return;
		ItemStack item = event.getItem();
		if (!isGun(item))
			return;
		Player player = event.getPlayer();

		if (!isAlive(item)) {
			player.sendMessage(
					ChatColor.AQUA + getName() + ChatColor.RED + " needs repair before it can be used again!");
			return;
		}
		
		int xpNeeds = computeTotalXP(player) - xpdraw; 

		if (xpNeeds < 0 && (xpNeeds + AddGun.getPlugin().getXpPerBottle() * getInvXp(player) < 0)) {
			player.sendMessage(
					ChatColor.AQUA + getName() + ChatColor.RED + " requires more fuel (XP) to fire then you have!");
			return;
		}

		if (!hasBullet(player)) {
			player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " has no useable ammo!");
			return;
		}

		Long nextUse = cooldowns.get(player.getUniqueId());
		if (nextUse == null || nextUse < System.currentTimeMillis()) {
			cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldown);

			// consume bullet and XP now.
			if (!payForShot(player)) {
				player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " jammed! Bullet and fuel wasted.");
			} else {
				// based on their stillness and settledness.
				double accuracy = computeAccuracyFor(player.getUniqueId());
				
				double offset = player.isSneaking() ? 1.1d : 1.25d;
				Vector bbOff = player.getEyeLocation().getDirection().normalize();
				bbOff.multiply(player.getWidth() / 2);
				Location origin = player.getLocation().clone().add(bbOff.getX(), offset, bbOff.getZ());
				SmallFireball bullet = player.getWorld().spawn(origin, SmallFireball.class);
				bullet.setCustomName(this.tag);
				bullet.setBounce(false);
				bullet.setGravity(true);
				bullet.setShooter(player);
				bullet.setIsIncendiary(false);
				bullet.setYield(0);
				bullet.setFireTicks(0);
				bullet.setMetadata("hitsRemain", new MetadataValueAdapter(AddGun.getPlugin()) {
	
					@Override
					public void invalidate() {
						// noop yet
					}
	
					@Override
					public Object value() {
						return new Integer(passthrough - 1);
					}
				});
				
				Location baseLocation = player.getEyeLocation().clone();
				baseLocation.setYaw((float) (baseLocation.getYaw() + (Math.random() - 0.5) * 5.0 * accuracy));
				baseLocation.setPitch((float) (baseLocation.getPitch() + (Math.random() - 0.5) * 5.0 * accuracy));
				if (accuracy > 0.25) {
					player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " is heavy! Crouch and hold still to improve accuracy.");
				} else if (accuracy < 0.01) {
					// TODO: remove
					player.sendMessage(ChatColor.AQUA + getName() + ChatColor.GREEN + " is shooting straight, good work.");
				}
				Vector baseVector = baseLocation.getDirection().normalize().multiply(this.maxSpeed);
				
				bullet.setVelocity(baseVector);
				
				AddGun.getPlugin().debug(" Spawning new bullet at {0} with velocity {1}", bullet.getLocation(),
						bullet.getVelocity());
				Location loc = bullet.getLocation().clone();
				travelPaths.put(bullet.getUniqueId(), loc);
				loc.getWorld().spawnParticle(Particle.FLAME, loc, 25);
				loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_LARGE_BLAST_FAR, 10.0f, 2.0f);
				player.setCooldown(Material.DIAMOND_HOE, (int) (cooldown / 50));
				item.setDurability((short) (item.getDurability() - damagePerUse));
				player.getInventory().setItemInMainHand(item);
				
				// jerk player's view back and reset still
				this.stillSince.remove(player.getUniqueId());
				player.teleport(player.getLocation().setDirection(baseLocation.getDirection()), TeleportCause.PLUGIN);
			}
		} else {
			player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " is in cooldown mode, wait another "
					+ ChatColor.WHITE + ((long) ((nextUse - System.currentTimeMillis()) / 1000)) + "s");

			player.setCooldown(Material.DIAMOND_HOE, (int) ((nextUse - System.currentTimeMillis()) / 50));
		}
		
		// Prevent normal effects of this tool
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setCancelled(true);
	}

	/**
	 * It hit the ground maybe!
	 * 
	 * @param event the impact event, we ignore any that aren't just blockhits
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitGroundEvent(ProjectileHitEvent event) {
		if (!EntityType.SMALL_FIREBALL.equals(event.getEntityType()))
			return;
		if (event.getHitBlock() == null)
			return;

		SmallFireball bullet = (SmallFireball) event.getEntity();
		if (!bullet.getName().equals(this.tag))
			return;

		Location end = event.getHitBlock().getLocation().clone().add(0.5, 0.5, 0.5);
		World world = end.getWorld();

		// make a new sound where it hits.
		world.playSound(end, Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.5f);
		
		// make a splash
		world.spawnParticle(Particle.BLOCK_DUST, end.clone().add( 0.5, 0.0, 0.0), 5);
		world.spawnParticle(Particle.BLOCK_DUST, end.clone().add(-0.5, 0.0, 0.0), 5);
		world.spawnParticle(Particle.BLOCK_DUST, end.clone().add( 0.0, 0.5, 0.0), 5);
		world.spawnParticle(Particle.BLOCK_DUST, end.clone().add( 0.0,-0.5, 0.0), 5);
		world.spawnParticle(Particle.BLOCK_DUST, end.clone().add( 0.0, 0.0, 0.5), 5);
		world.spawnParticle(Particle.BLOCK_DUST, end.clone().add( 0.0, 0.0,-0.5), 5);

		Entity hit = event.getEntity();
		if (hit == null) {
			Location start = travelPaths.remove(bullet.getUniqueId());

			if (start != null) {
				double distance = end.distance(start);

				Vector vector = end.subtract(start).toVector();
				vector = vector.multiply(1.0d / distance);
				for (int i = 0; i < distance; i++) {
					world.spawnParticle(Particle.FLAME, start.add(vector), 5);
				}
			}

			bullet.remove();
			return;
		}
	}

	/**
	 * It hit a toon! or animal
	 * 
	 * @param event the hit event.
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitEvent(EntityDamageByEntityEvent event) {
		if (!EntityType.SMALL_FIREBALL.equals(event.getDamager().getType()))
			return;

		SmallFireball bullet = (SmallFireball) event.getDamager();
		if (!bullet.getName().equals(this.tag))
			return;

		Entity hit = event.getEntity();

		HitDigest whereEnd = detailedHitBoxLocation(bullet.getLocation().clone(), bullet.getVelocity(), hit);
		Location end = whereEnd.hitLocation;
		World world = end.getWorld();

		// make a new sound where it hits.
		world.playSound(end, Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.5f);
		// make a splash
		world.spawnParticle(Particle.BLOCK_DUST, end, 35);

		hit.eject(); // getting hit by railgun gets you out of whatever you are in.
		hit.getPassengers().forEach(e -> e.eject()); // and ejects your passengers
		bullet.remove();

		event.setCancelled(true); // hmmm. TODO: check

		if (hit instanceof Damageable) {
			Damageable dhit = (Damageable) hit;
			if (!dhit.isDead() && !HitPart.MISS.equals(whereEnd.nearestHitPart)) {
				AddGun.getPlugin().debug("Damaging {0} at {1} due to {2} intersection with railgun bullet", dhit,
						dhit.getLocation(), whereEnd.nearestHitPart);
				dhit.damage(damagePerHit, bullet);
			} else if (HitPart.MISS.equals(whereEnd.nearestHitPart)) {
				AddGun.getPlugin().debug(
						"Close shave! Just missed {0} at {1}, doublecheck showed {2} with railgun bullet", dhit,
						dhit.getLocation(), whereEnd.nearestHitPart);
			} else if (HitPart.HEAD.equals(whereEnd.nearestHitPart)) {
				if (bullet.getShooter() instanceof CommandSender) {
					((CommandSender) bullet.getShooter()).sendMessage("HEADSHOT on " + dhit.getCustomName());
				}
			}

			int hitsRemain = (Integer) bullet.getMetadata("hitsRemain").get(0).value();
			if (hitsRemain > 0 || dhit.isDead() || HitPart.MISS.equals(whereEnd.nearestHitPart)) {
				AddGun.getPlugin().debug(" Hit location {0}, spawning continue at {1} with velocity {2}", end,
						end.clone().add(bullet.getVelocity().normalize().multiply(dhit.getWidth() * 2)),
						bullet.getVelocity());
				SmallFireball continueBullet = world.spawn(
						end.clone().add(bullet.getVelocity().normalize().multiply(dhit.getWidth() * 2)),
						SmallFireball.class);
				continueBullet.setCustomName(this.tag);
				continueBullet.setBounce(false);
				continueBullet.setGravity(true);
				continueBullet.setFireTicks(0);
				continueBullet.setIsIncendiary(false);
				continueBullet.setYield(0);
				continueBullet.setFireTicks(0);
				continueBullet.setShooter(bullet.getShooter());
				continueBullet.setMetadata("hitsRemain", new MetadataValueAdapter(AddGun.getPlugin()) {

					@Override
					public void invalidate() {
						// noop yet
					}

					@Override
					public Object value() {
						return new Integer(dhit.isDead() || HitPart.MISS.equals(whereEnd.nearestHitPart) ? hitsRemain
								: hitsRemain - 1);
					}

				});
				continueBullet.setVelocity(bullet.getVelocity());

				// on hit, draw path back to source
				Location start = travelPaths.remove(bullet.getUniqueId());

				double distance = end.distance(start);

				Vector vector = end.subtract(start).toVector();
				vector = vector.multiply(1.0d / distance);
				for (int i = 0; i < distance; i++) {
					world.spawnParticle(Particle.FLAME, start.add(vector), 5);
					// world.spawnParticle(Particle.FIREWORKS_SPARK, start, 2);
				}
				travelPaths.put(continueBullet.getUniqueId(), continueBullet.getLocation());
			}
		} else {
			// kill signs and shit
			hit.remove();
			AddGun.getPlugin().debug("Removing " + hit + " due to intersection with railgun bullet");
		}
	}

	/*
	 * Basic preventions
	 */

	/**
	 * Prevent using the gun-item to damage blocks (cancels even damaging)
	 * @param event the damage event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void preventBlockDamageWithGun(BlockDamageEvent event) {
		if (isGun(event.getItemInHand())) {
			event.setCancelled(true);
		}
	}

	/**
	 * Prevent using the gun-item to break blocks
	 * @param event the break event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void preventBlockBreakWithGun(BlockBreakEvent event) {
		if (event.getPlayer() != null && isGun(event.getPlayer().getInventory().getItemInMainHand())) {
			event.setCancelled(true);
		}
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
			if (isGun(equips.getItemInMainHand())) {
				// modify damage!
				event.setDamage(this.bluntDamage);
			}
		}
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
			sneakingSince.computeIfAbsent(event.getPlayer().getUniqueId(), u -> System.currentTimeMillis());
		} else {
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
			stillSince.computeIfAbsent(event.getPlayer().getUniqueId(), u -> System.currentTimeMillis());
		} else {
			stillSince.remove(event.getPlayer().getUniqueId());
		}
	}

	/**
	 * This tries to keep player from equipping more then one of the Gun.
	 * 
	 * @param event The inventory click event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void equipWeaponEvent(InventoryClickEvent event) {
		if (!enabled) return;
		HumanEntity human = event.getWhoClicked();
		boolean alreadyHasGun = hasGun(human);
		
		Inventory inv = event.getInventory();
		ItemStack current = event.getCurrentItem();
		ItemStack cursor = event.getCursor();
		
		if (!human.getInventory().equals(inv)) {
			return;
		}
		
		if ( (Material.AIR.equals(current.getType()) && isGun(cursor)) ||
				(!Material.AIR.equals(current.getType()) && !Material.AIR.equals(cursor.getType()) && isGun(cursor) && !isGun(current))) {
			if (alreadyHasGun) {
				event.setCancelled(true);
				event.setResult(Result.DENY);
				human.sendMessage(ChatColor.RED + "A " + ChatColor.AQUA + getName() + ChatColor.RED + " is already in inventory, it's too heavy to carry two!");
			} else {
				human.sendMessage(ChatColor.GREEN + "A " + ChatColor.AQUA + getName() + ChatColor.GREEN + " has been equipped!");
				human.setCooldown(Material.DIAMOND_HOE, (int) (cooldown / 50));
				cooldowns.put(human.getUniqueId(), System.currentTimeMillis() + cooldown);
			}
		} else if (!Material.AIR.equals(current.getType()) && !Material.AIR.equals(cursor.getType()) && isGun(cursor) && isGun(current)) {
			human.sendMessage(ChatColor.GREEN + "Swapping out prior " + ChatColor.AQUA + getName() + ChatColor.GREEN + " and equipping a different one!");
			human.setCooldown(Material.DIAMOND_HOE, (int) (cooldown / 50));
			cooldowns.put(human.getUniqueId(), System.currentTimeMillis() + cooldown);
		}
	}

	/**
	 * This prevents from picking up a gun if you already have one
	 * 
	 * @param event The pickup event.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void pickupWeaponEvent(EntityPickupItemEvent event) {
		if (!enabled) return;
		if (hasGun(event.getEntity())) {
			event.setCancelled(true);
			event.getEntity().sendMessage(ChatColor.RED + "A " + ChatColor.AQUA + getName() + ChatColor.RED + " is already in inventory, it's too heavy to carry two!");
		}
	}
}
