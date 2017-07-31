package com.programmerdan.minecraft.addgun.guns.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValueAdapter;
import org.bukkit.util.Vector;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.guns.BasicGun;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.Vec3D;

public class RailGun implements BasicGun, Listener {

	private Map<UUID, Long> cooldowns = new ConcurrentHashMap<>(); 
	
	private Map<UUID, Location> travelPaths = new ConcurrentHashMap<>();
	private boolean enabled = false;
	
	private ItemStack example = null;
	
	private double maxSpeed = 100.0;
	private int maxUses = 120;
	private int minUses = 20;
	private long cooldown = 500;
	private double damagePerHit = 100.0d;
	private int passthrough = 3;
	private double bluntDamage = 3.0d;
	
	private String tag = ChatColor.BLACK + "Gun: " + Integer.toHexString(this.getName().hashCode() + this.getName().length());
	
	public RailGun() {}

	@Override
	public void configure(ConfigurationSection config) {
		if (config != null) {
			enabled = true;
			
			maxSpeed = config.getDouble("maxVelocity", maxSpeed);
			cooldown = config.getLong("cooldown", cooldown);
			minUses = config.getInt("minUses", minUses);
			maxUses = config.getInt("maxUses", maxUses);
			damagePerHit = config.getDouble("damage", damagePerHit);
			bluntDamage = config.getDouble("blunt", bluntDamage);
			passthrough = config.getInt("passthrough", passthrough);
			
			ItemStack preExample = new ItemStack(Material.DIAMOND_HOE, 1, (short) maxUses);
			ItemMeta meta = preExample.getItemMeta();
			meta.setDisplayName("Rail Gun Mark1");
			List<String> lores = meta.getLore();
			if (lores == null) {
				lores = new ArrayList<String>();
			}
			lores.add(gunTag());
			meta.setUnbreakable(true);
			meta.setLore(lores);
			preExample.setItemMeta(meta);
			
			this.example = preExample;
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
		return example;
	}

	@Override
	public boolean isGun(ItemStack toCheck) {
		if (!enabled || toCheck == null) return false;
		
		if (!Material.DIAMOND_HOE.equals(toCheck.getType())) return false;
		
		if (!toCheck.hasItemMeta()) return false;
		
		ItemMeta meta = toCheck.getItemMeta();
		
		if (meta.getLore().contains(gunTag())) return true;
		
		return false;
	}
	
	private boolean isAlive(ItemStack toCheck) {
		if (toCheck.getDurability() > minUses) return true;
		return false;
	}
	
	private String gunTag() {
		return tag;
	}

	@Override
	public boolean isListener() {
		return true;
	}
	
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void gunPlayerInteractEvent(PlayerInteractEvent event) {
		if (!enabled || event.isBlockInHand() || !event.hasItem() || !EquipmentSlot.HAND.equals(event.getHand())) return;
		ItemStack item = event.getItem();
		if (!isGun(item)) return;
		Player player = event.getPlayer();
		
		if (!isAlive(item)) {
			player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " needs repair before it can be used again!");
			return;
		}
		
		Long nextUse = cooldowns.get(player.getUniqueId());
		if (nextUse == null || nextUse < System.currentTimeMillis()) {
			cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldown);
			SmallFireball bullet = player.getWorld().spawn(player.getLocation().add(0.0d, 1.25d, 0.0d), SmallFireball.class);
			//SmallFireball bullet = player.launchProjectile(SmallFireball.class, player.getLocation().getDirection().normalize().multiply(this.maxSpeed));
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
			bullet.setVelocity(player.getLocation().getDirection().normalize().multiply(this.maxSpeed));
			AddGun.getPlugin().debug(" Spawning new bullet at {0} with velocity {1}", bullet.getLocation(), bullet.getVelocity());
			Location loc = bullet.getLocation().clone();
			travelPaths.put(bullet.getUniqueId(), loc);
			//loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, 1);
			loc.getWorld().spawnParticle(Particle.FLAME, loc, 25);
			loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_LARGE_BLAST_FAR, 10.0f, 2.0f);
			player.setCooldown(Material.DIAMOND_HOE, (int) (cooldown / 50));
			item.setDurability((short) (item.getDurability() - 1));
			player.getInventory().setItemInMainHand(item);
		} else {
			player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " is in cooldown mode, wait another " + ChatColor.WHITE + 
					((long) ((nextUse - System.currentTimeMillis()) / 1000)) + "s");
			
			player.setCooldown(Material.DIAMOND_HOE, (int) ((nextUse - System.currentTimeMillis()) / 50));
		}
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitGroundEvent(ProjectileHitEvent event) {
		if (!EntityType.SMALL_FIREBALL.equals(event.getEntityType())) return;
		if (event.getHitBlock() == null) return;
		
		SmallFireball bullet = (SmallFireball) event.getEntity();
		if (!bullet.getName().equals(this.tag)) return;

		Location end = event.getHitBlock().getLocation().clone();
		World world = end.getWorld();

		// make a new sound where it hits.
		world.playSound(end, Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.5f);
		// make a splash
		world.spawnParticle(Particle.BLOCK_DUST, end, 35);
		
		Entity hit = event.getEntity();
		if (hit == null) {
			Location start = travelPaths.remove(bullet.getUniqueId());

			if (start != null) {
				double distance = end.distance(start);
				
				Vector vector = end.subtract(start).toVector();
				vector = vector.multiply(1.0d/distance);
				for (int i = 0; i < distance; i++) {
					world.spawnParticle(Particle.FLAME, start.add(vector), 5);
				}
			}

			bullet.remove();
			return;
		}
	}
	
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitEvent(EntityDamageByEntityEvent event) {
		if (!EntityType.SMALL_FIREBALL.equals(event.getDamager().getType())) return;
		
		SmallFireball bullet = (SmallFireball) event.getDamager();
		if (!bullet.getName().equals(this.tag)) return;

		Entity hit = event.getEntity();

		Location end = approximateHitBoxLocation(bullet.getLocation().clone(), bullet.getVelocity(), hit);
		World world = end.getWorld();

		// make a new sound where it hits.
		world.playSound(end, Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.5f);
		// make a splash
		world.spawnParticle(Particle.BLOCK_DUST, end, 35);
		
		hit.eject(); // getting hit by railgun gets you out of whatever you are in.
		bullet.remove();
		
		if (hit instanceof Damageable) {
			Damageable dhit = (Damageable) hit;
			if (!dhit.isDead()) {
				AddGun.getPlugin().debug("Damaging {0} at {1} due to intersection with railgun bullet", dhit, dhit.getLocation());
				
				dhit.damage(damagePerHit, bullet);
			}
			/*dhit.setInvulnerable(true);
			Bukkit.getScheduler().runTaskLater(AddGun.getPlugin(), new Runnable() {
					@Override
					public void run() {
						if (dhit != null) dhit.setInvulnerable(false);
					}
				}, 1);*/
			int hitsRemain = (Integer) bullet.getMetadata("hitsRemain").get(0).value();
			if (hitsRemain > 0 || dhit.isDead()) {
				AddGun.getPlugin().debug(" Hit location {0}, spawning continue at {1} with velocity {2}", bullet.getLocation(), end.clone().add(bullet.getVelocity().normalize().multiply(dhit.getWidth()*2)), bullet.getVelocity());
				SmallFireball continueBullet = world.spawn(end.clone().add(bullet.getVelocity().normalize().multiply(dhit.getWidth()*2)), SmallFireball.class);
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
						return new Integer(dhit.isDead() ? hitsRemain : hitsRemain - 1);
					}
					
				});
				continueBullet.setVelocity(bullet.getVelocity());
				
				// on hit, draw path back to source
				Location start = travelPaths.remove(bullet.getUniqueId());

				double distance = end.distance(start);
				
				Vector vector = end.subtract(start).toVector();
				vector = vector.multiply(1.0d/distance);
				for (int i = 0; i < distance; i++) {
					world.spawnParticle(Particle.FLAME, start.add(vector), 5);
					//world.spawnParticle(Particle.FIREWORKS_SPARK, start, 2);
				}
				travelPaths.put(continueBullet.getUniqueId(), continueBullet.getLocation());
			}
		} else {
			hit.remove();
			AddGun.getPlugin().debug("Removing " + hit + " due to intersection with railgun bullet");
		}
	}
	
	private Location approximateHitBoxLocation(Location clone, Vector velocity, Entity entity) {
		
		// we have the bullet's last tick location, its velocity, and the barycenter of the object it hit, and that
		// object's hitbox. We also know for sure that the object was intersected with.
		AxisAlignedBB boundingBox = ((CraftEntity) entity).getHandle().getBoundingBox();
		Vec3D origLocation = new Vec3D(clone.getX(), clone.getY(), clone.getZ());
		Vec3D origVector = new Vec3D(clone.getX() + velocity.getX(), clone.getY() + velocity.getY(), clone.getZ() + velocity.getZ());
		
		MovingObjectPosition hit = boundingBox.b(origLocation, origVector);
		if (hit == null) {
			return clone;
		} else {
			return new Location(clone.getWorld(), hit.pos.x, hit.pos.y, hit.pos.z, clone.getYaw(), clone.getPitch());
		}
	}

	/**
	 * Basic preventions
	 */
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void preventBlockDamageWithGun(BlockDamageEvent event) {
		if (isGun(event.getItemInHand())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void preventBlockBreakWithGun(BlockBreakEvent event) {
		if (event.getPlayer() != null && isGun(event.getPlayer().getInventory().getItemInMainHand())) {
			event.setCancelled(true);
		}
	}
	
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
	
	
}
