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
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValueAdapter;
import org.bukkit.util.Vector;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.guns.BasicGun;

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
		if (!enabled) return false;
		
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
		
		Long lastUse = cooldowns.get(player.getUniqueId());
		if (lastUse == null || lastUse - cooldown < System.currentTimeMillis()) {
			SmallFireball bullet = player.launchProjectile(SmallFireball.class, player.getLocation().getDirection().normalize().multiply(this.maxSpeed));
			bullet.setCustomName(this.tag);
			bullet.setBounce(false);
			bullet.setGravity(true);
			bullet.setShooter(player);
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
			Location loc = bullet.getLocation().clone();
			travelPaths.put(bullet.getUniqueId(), loc);
			loc.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, 1);
			loc.getWorld().spawnParticle(Particle.FLAME, loc, 1);
			cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
			player.setCooldown(Material.DIAMOND_HOE, (int) (cooldown / 50));
		} else {
			player.sendMessage(ChatColor.AQUA + getName() + ChatColor.RED + " is in cooldown mode, wait another " + ChatColor.WHITE + 
					((long) ((cooldown - (System.currentTimeMillis() - lastUse)) / 1000)) + "s");
			
			player.setCooldown(Material.DIAMOND_HOE, (int) ((cooldown - (System.currentTimeMillis() - lastUse)) / 50));
		}
	}
	
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gunBulletHitEvent(ProjectileHitEvent event) {
		if (!EntityType.SMALL_FIREBALL.equals(event.getEntityType())) return;
		
		SmallFireball bullet = (SmallFireball) event.getEntity();
		if (!bullet.getName().equals(this.tag)) return;
		
		Entity hit = event.getHitEntity();
		if (hit == null) {
			bullet.remove();
		}
		
		hit.eject(); // getting hit by railgun gets you out of whatever you are in.
		
		if (hit instanceof Damageable) {
			Damageable dhit = (Damageable) hit;
			AddGun.getPlugin().debug("Damaging " + dhit + " due to intersection with railgun bullet");
			
			dhit.damage(damagePerHit, bullet);
			dhit.setInvulnerable(true);
			int hitsRemain = (Integer) bullet.getMetadata("hitsRemain").get(0).value();
			if (hitsRemain > 0) {
				Location end = bullet.getLocation().clone();
				World world = end.getWorld();
				
				AddGun.getPlugin().debug(" Hit location {0}, spawning continue at {1} with velocity {2}", bullet.getLocation(), bullet.getLocation().clone().add(bullet.getVelocity().normalize().multiply(2)), bullet.getVelocity());
				SmallFireball continueBullet = world.spawn(end.clone().add(bullet.getVelocity().normalize().multiply(2)), SmallFireball.class);
				continueBullet.setVelocity(bullet.getVelocity());
				continueBullet.setCustomName(this.tag);
				continueBullet.setBounce(false);
				continueBullet.setGravity(true);
				continueBullet.setFireTicks(0);
				continueBullet.setShooter(bullet.getShooter());
				continueBullet.setMetadata("hitsRemain", new MetadataValueAdapter(AddGun.getPlugin()) {

					@Override
					public void invalidate() {
						// noop yet
					}

					@Override
					public Object value() {
						return new Integer(hitsRemain - 1);
					}
					
				});
				
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
			bullet.remove();
			dhit.setInvulnerable(false);
		} else {
			hit.remove();
			AddGun.getPlugin().debug("Removing " + hit + " due to intersection with railgun bullet");
		}
	}
}
