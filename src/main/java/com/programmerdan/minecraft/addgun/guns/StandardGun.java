package com.programmerdan.minecraft.addgun.guns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftProjectile;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValueAdapter;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.google.common.collect.Sets;
import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ArmorType;
import com.programmerdan.minecraft.addgun.ammo.AmmoType;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Clip;
import com.programmerdan.minecraft.addgun.events.LoadGunEvent;

import net.minecraft.server.v1_12_R1.EntityProjectile;

import static com.programmerdan.minecraft.addgun.guns.Utilities.getArmorType;
import static com.programmerdan.minecraft.addgun.guns.Utilities.getInvXp;
import static com.programmerdan.minecraft.addgun.guns.Utilities.computeTotalXP;
import static com.programmerdan.minecraft.addgun.guns.Utilities.getGunData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.updateGunData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.sigmoid;

public class StandardGun implements BasicGun {

	private static double[] protectionCurve = new double[] {
		0.0d,
		-0.5 * Math.log(1.0 / (1.0 + 1.0)),
		-0.5 * Math.log(1.0 / (2.0 + 1.0)),
		-0.5 * Math.log(1.0 / (3.0 + 1.0)),
		-0.5 * Math.log(1.0 / (4.0 + 1.0)),
		-0.5 * Math.log(1.0 / (5.0 + 1.0)),
		-0.5 * Math.log(1.0 / (6.0 + 1.0)),
		-0.5 * Math.log(1.0 / (7.0 + 1.0)),
		-0.5 * Math.log(1.0 / (8.0 + 1.0)),
		-0.5 * Math.log(1.0 / (9.0 + 1.0)),
		-0.5 * Math.log(1.0 / (10.0 + 1.0)),
		-0.5 * Math.log(1.0 / (11.0 + 1.0)),
		-0.5 * Math.log(1.0 / (12.0 + 1.0)),
		-0.5 * Math.log(1.0 / (13.0 + 1.0)),
		-0.5 * Math.log(1.0 / (14.0 + 1.0)),
		-0.5 * Math.log(1.0 / (15.0 + 1.0)),
	};
	
	private static double baseProjectileProtection = 1.25d;
	private static double baseEnvironmentProtection = 0.66d;
	private static double baseUnbreakingProtection = 0.75d;
	private static double varianceFactor = 1.2d;
	private static double expansionFactor = 1.2d;
	private static double finalFitFactor = 2.0d;
	
	/**
	 * Is this gun enabled?
	 */
	private boolean enabled = false;

	/**
	 * Simplest example of the gun -- this is used for "matching" 
	 */
	private ItemStack gunExample = null;
	
	/**
	 * Base lore that was on the example gun in config.
	 */
	private List<String> gunLore = null;
	
	/**
	 * All the valid bullets that can be used with this gun, if it is directly loaded with bullets.
	 */
	private List<String> allBullets = null;
	
	/**
	 * All the valid clips that can be used with this gun, if it is loaded via clips.
	 */
	private List<String> allClips = null;
	
	/**
	 * max V in meters (blocks) / t -- this is gun's intrinsic, could be modified by bullets.
	 */
	private double maxSpeed = 200.0;
	
	/**
	 * min V in meters (blocks) / t
	 */
	private double minSpeed = 180.0;
	
	/**
	 * Internal avg V / t
	 */
	private double avgSpeed = (maxSpeed + minSpeed) / 2.0;
	
	/**
	 * Internal variance min compute
	 */
	private double varianceMin = -1.0d;
	
	/**
	 * Internal variance max compute
	 */
	private double varianceMax = 1.0d;
	
	/**
	 * Max times the gun can be fired before repair.
	 */
	private int maxUses = 500;
	
	/**
	 * Inflection point, when this many uses remain the risk of misfire is 50%
	 */
	private int middleRisk = 10;
	
	/**
	 * Risk spread, addresses how steep misfire chance goes from 0 to 50 and from 50 to 100.
	 * A bigger spread means misfire chance
	 */
	private int riskSpread = 20;
	
	/**
	 * Misfire explosion change (percentage) -- could be modified by bullets
	 */
	private double misfireBlowoutChance = 0.05;
	
	/**
	 * Misfire blowout explosion strength -- could be modified by bullets.
	 */
	private float baseBlowoutStrength = 2f;
	
	/**
	 * When firing the gun and still / sneak isn't maximized, potentially angular jitter
	 */
	private double maxMissRadius = 30;
	/**
	 * Some guns are just innacurate .. minimum angular jitter regardless of stillness / sneak
	 */
	private double minMissRadius = 0;

	// dmg is from bullet
	
	/**
	 * or smack nerd over the head?
	 */
	private double bluntDamage = 3.0d;

	
	/**
	 * The unique identifier for this gun.
	 */
	private String name;
	
	/**
	 * Every gun has this tag somewhat hidden, it's used to quickly identify if a managed gun or not
	 */
	private String tag;
	
	/**
	 * Every fired bullet has this tag somewhat hidden, it's used to identify bullets. As is location tracks.
	 */
	private String bulletTag;
	
	/**
	 * Does this gun use clips, bullets, or autofeed from inventory to fire?
	 */
	private AmmoType ammoSource = AmmoType.INVENTORY;
	
	/**
	 * If BULLET, determines the max bullets that can be loaded into the weapon.
	 * CLIP is always 1, and INVENTORY is 0.
	 */
	private int maxAmmo = 0;
	
	/**
	 * Does this gun use XP to fire?
	 */
	private boolean usesXP = false;
	/**
	 * If the gun requires XP (energy) to fire... default no
	 */
	private int xpDraw = 0;
	
	
	/**
	 * Does this gun have a cooldown?
	 */
	private boolean hasCooldown = false;
	/**
	 * Should this gun enter into cooldown on equip (into hotbar)
	 */
	private boolean cooldownOnEquip = false;
	/**
	 * Cooldown between uses in ms
	 */
	private long cooldown = 500;

	/**
	 * This can be used to track any warnings sent to players and not resend
	 */
	private Map<UUID, Set<String>> warned = new ConcurrentHashMap<>();
	/**
	 * If the gun uses a cooldown, tracks which players are in cooldown and since when.
	 */
	private Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
	
	/**
	 * Should this gun be limited to a single gun in inventory?
	 */
	private boolean limitToOne = false;
	
	
	/**
	 * The point at which, after so many seconds of "stillness" -- head motion only -- that any aim impacts are halved.
	 */
	private double stillInflection = 7.5d;
	/**
	 * The point at which, after so many seconds of sneaking, any aim impacts are halved.
	 */
	private double sneakInflection = 7.5d;
	/**
	 * A relative "spread" of speed of motion over inflection, values close to 0 lead to a sharp inflection, values larger make a smoother
	 * transition. Values of 2.5 to 8 are typical. This is for stillness.
	 */
	private double stillSpread = 2.5d;
	/**
	 * A relative "spread" of speed of motion over inflection, values close to 0 lead to a sharp inflection, values larger make a smoother
	 * transition. Values of 2.5 to 8 are typical. This is for sneaking.
	 */
	private double sneakSpread = 2.5d;
	
	public StandardGun(String name) {
		this.name = name;
		tag = ChatColor.BLACK + "Gun: "
				+ Integer.toHexString(this.getName().hashCode() + this.getName().length());
		bulletTag = ChatColor.BLACK + "Bullet: "
				+ Integer.toHexString(this.getName().hashCode() + this.getName().length());
	}
	
	@Override
	public void configure(ConfigurationSection config) {
		if (config != null) {
			enabled = true;
			
			this.gunExample = config.getItemStack("example");
			if (this.gunExample == null) {
				throw new IllegalArgumentException("No inventory representation (section example) provided for this gun, it cannot be instanced");
			} else {
				if (this.gunExample.hasItemMeta() && this.gunExample.getItemMeta().hasLore()) {
					this.gunLore = this.gunExample.getItemMeta().getLore();
				}
			}

			this.maxAmmo = config.getInt("ammo.max", 0);
			
			if (config.contains("ammo.bullets")) {
				this.allBullets = new ArrayList<String>();
				if (this.maxAmmo <= 0) {
					this.ammoSource = AmmoType.INVENTORY;
				} else {
					this.ammoSource = AmmoType.BULLET;
				}
				for (String bullet : config.getStringList("ammo.bullets")) {
					if (AddGun.getPlugin().getAmmo().getBullet(bullet) != null) {
						this.allBullets.add(bullet);
					} else {
						AddGun.getPlugin().warning("Could not find bullet " + bullet + " for gun " + this.name);
					}
				}
				if (this.allBullets.isEmpty()) {
					throw new IllegalArgumentException("Gun is defined to use bullets, but no valid bullets defined!");
				}
			} else if (config.contains("ammo.clips")) {
				this.allClips = new ArrayList<String>();
				this.ammoSource = AmmoType.CLIP;
				for (String clip: config.getStringList("ammo.clips")) {
					if (AddGun.getPlugin().getAmmo().getClip(clip) != null) {
						this.allClips.add(clip);
					} else {
						AddGun.getPlugin().warning("Could not find clip " + clip + " for gun " + this.name);
					}
				}
				if (this.allClips.isEmpty()) {
					throw new IllegalArgumentException("Gun is defined to use clips, but no valid clip defined!");
				}
			} else {
				AddGun.getPlugin().warning("Intentional or otherwise, gun " + this.name + " is either an energy weapon or free to use.");
			}
			
			this.maxUses = config.getInt("health.max", maxUses);
			this.middleRisk = config.getInt("health.misfire.inflection", middleRisk);
			this.riskSpread = config.getInt("health.misfire.spread", riskSpread);
			this.misfireBlowoutChance = config.getDouble("health.misfire.blowout.chance", misfireBlowoutChance);
			this.baseBlowoutStrength = (float) config.getDouble("health.misfire.blowout.strength", baseBlowoutStrength);
			
			this.minSpeed = config.getDouble("speed.min", minSpeed);
			this.maxSpeed = config.getDouble("speed.max", maxSpeed);
			this.avgSpeed = (this.minSpeed + this.maxSpeed) / 2;
			
			this.varianceMin = (this.minSpeed - this.avgSpeed) / ((this.maxSpeed - this.minSpeed + 1) / 4.0d) * StandardGun.varianceFactor;
			this.varianceMax = (this.maxSpeed - this.avgSpeed) / ((this.maxSpeed - this.minSpeed + 1) / 4.0d) * StandardGun.varianceFactor;
			
			this.bluntDamage = config.getDouble("damage.blunt", bluntDamage);
			this.maxMissRadius = config.getDouble("miss.radius.max", maxMissRadius);
			this.minMissRadius = config.getDouble("miss.radius.min", minMissRadius);
			this.stillInflection = config.getDouble("miss.still.inflection", stillInflection);
			this.stillSpread = config.getDouble("miss.still.spread", stillSpread);
			this.sneakInflection = config.getDouble("miss.sneak.inflection", sneakInflection);
			this.sneakSpread = config.getDouble("miss.sneak.spread", sneakSpread);
			
			this.xpDraw = config.getInt("ammo.xp", 0);
			if (this.xpDraw > 0) {
				this.usesXP = true;
			}
			
			this.cooldown = config.getLong("cooldown.milliseconds", 0);
			this.cooldownOnEquip = config.getBoolean("cooldown.equip", false);
			this.hasCooldown = this.cooldown > 0;
			
			this.limitToOne = config.getBoolean("limits.onlyOne", false);
			
			Map<String, Object> gunData = new HashMap<String, Object>();
			
			gunData.put("rounds", Integer.valueOf(0));
			
			gunData.put("type", this.ammoSource);
			
			gunData.put("lifetimeShots", Long.valueOf(0l));
						
			gunData.put("health", this.maxUses);
			
			this.gunExample = updateGunLore(updateGunData(this.gunExample, gunData));
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public double getMaxSpeed() {
		return maxSpeed;
	}

	public void setMaxSpeed(double maxSpeed) {
		this.maxSpeed = maxSpeed;
	}

	public double getMinSpeed() {
		return minSpeed;
	}

	public void setMinSpeed(double minSpeed) {
		this.minSpeed = minSpeed;
	}

	public int getMaxUses() {
		return maxUses;
	}

	public void setMaxUses(int maxUses) {
		this.maxUses = maxUses;
	}

	public int getMiddleRisk() {
		return middleRisk;
	}

	public void setMiddleRisk(int middleRisk) {
		this.middleRisk = middleRisk;
	}
	
	public int getRiskSpread() {
		return riskSpread;
	}
	
	public void setRiskSpread(int riskSpread) {
		this.riskSpread = riskSpread;
	}

	public double getMisfireBlowoutChance() {
		return misfireBlowoutChance;
	}

	public void setMisfireBlowoutChance(double misfireBlowoutChance) {
		this.misfireBlowoutChance = misfireBlowoutChance;
	}


	public int getXpDraw() {
		return xpDraw;
	}

	public void setXpDraw(int xpdraw) {
		this.xpDraw = xpdraw;
	}

	public double getMaxMissRadius() {
		return maxMissRadius;
	}

	public void setMaxMissRadius(double maxMissRadius) {
		this.maxMissRadius = maxMissRadius;
	}

	public double getMinMissRadius() {
		return minMissRadius;
	}

	public void setMinMissRadius(double minMissRadius) {
		this.minMissRadius = minMissRadius;
	}

	public double getBluntDamage() {
		return bluntDamage;
	}

	public void setBluntDamage(double bluntDamage) {
		this.bluntDamage = bluntDamage;
	}

	public boolean isUsesClips() {
		return AmmoType.CLIP.equals(this.ammoSource);
	}

	public boolean isUsesBullets() {
		return AmmoType.BULLET.equals(this.ammoSource);
	}

	public void setAmmoSource(AmmoType ammo) {
		this.ammoSource = ammo;
	}
	
	public AmmoType getAmmoSource() {
		return this.ammoSource;
	}

	public boolean isUsesXP() {
		return usesXP;
	}

	public void setUsesXP(boolean usesXP) {
		this.usesXP = usesXP;
	}

	public boolean isHasCooldown() {
		return hasCooldown;
	}

	public void setHasCooldown(boolean hasCooldown) {
		this.hasCooldown = hasCooldown;
	}

	public boolean isCooldownOnEquip() {
		return cooldownOnEquip;
	}

	public void setCooldownOnEquip(boolean cooldownOnEquip) {
		this.cooldownOnEquip = cooldownOnEquip;
	}

	public long getCooldown() {
		return cooldown;
	}

	public void setCooldown(long cooldown) {
		this.cooldown = cooldown;
	}
	
	public Long getCooldown(UUID entity) {
		return this.cooldowns.get(entity);
	}
	
	public void setCooldown(UUID entity, Long value) {
		this.cooldowns.put(entity, value);
	}

	public boolean isLimitToOne() {
		return limitToOne;
	}

	public void setLimitToOne(boolean limitToOne) {
		this.limitToOne = limitToOne;
	}

	public double getStillInflection() {
		return stillInflection;
	}

	public void setStillInflection(double stillInflection) {
		this.stillInflection = stillInflection;
	}

	public double getSneakInflection() {
		return sneakInflection;
	}

	public void setSneakInflection(double sneakInflection) {
		this.sneakInflection = sneakInflection;
	}

	public double getStillSpread() {
		return stillSpread;
	}

	public void setStillSpread(double stillSpread) {
		this.stillSpread = stillSpread;
	}

	public double getSneakSpread() {
		return sneakSpread;
	}

	public void setSneakSpread(double sneakSpread) {
		this.sneakSpread = sneakSpread;
	}
	
	public String getName() {
		return name;
	}
	
	/// NOW INTO GENERIC HANDLING CODE ///
	
	/**
	 * Subclasses are encouraged to override this.
	 * Just makes a sounds and spawns a particle. Note that if you're OK with this but still want
	 * to do some custom stuff on a nearmiss (perhaps jerk the entity away or something? I dunno)
	 * then consider using {@link #postMiss(HitDigest, Entity, Projectile, Projectile, Bullet)}
	 * instead, which gives you this same detail as well as the projectile spawned to "keep going"
	 * post-miss.
	 * 
	 * @param missData Data matrix showing miss
	 * @param missed What entity was almost hit
	 * @param bullet The bullet data
	 */
	public void nearMiss(HitDigest missData, Entity missed, Projectile bullet, Bullet type) {
		Location end = missData.hitLocation;
		World world = end.getWorld();
		world.playSound(end, Sound.BLOCK_GLASS_HIT, 1.0f, 1.5f);
		world.spawnParticle(Particle.SMOKE_NORMAL, end, 15, 0.1, 0.1, 0.1, 0.1);
	}
	
	/**
	 * Subclasses are encouraged to override this.
	 * Default behavior is to eject off of what you are riding, play a sound and spawn particle.
	 *
	 * 
	 * @param hitData Data matrix showing hit
	 * @param hit What entity was hit
	 * @param bullet The bullet data.
	 */
	public void preHit(HitDigest hitData, Entity hit, Projectile bullet, Bullet type) {
		Location end = hitData.hitLocation;
		World world = end.getWorld();
		hit.eject(); // eject the player
		hit.getPassengers().forEach(e -> e.eject()); // and ejects your passengers
		// make a new sound where it hits.
		world.playSound(end, Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.5f);
		// make a splash
		world.spawnParticle(Particle.SMOKE_NORMAL, end, 35, 0.1, 0.1, 0.1, 0.1);
	}
	
	/**
	 * Override this class, you can use it to provide particle effects along travel path. 
	 * 
	 * It is called after all other handling is done. Keep it lightweight
	 * 
	 * @param start The start location of flight
	 * @param end The end location of impact / miss
	 * @param type the type of bullet in play
	 * @param endOfFlight is the bullet still flying after this or has it impacted?
	 */
	public void flightPath(Location start, Location end, Bullet type, boolean endOfFlight) {
		// no special flight path stuff. maybe a whizzing sound?

		World world = start.getWorld();
		if (endOfFlight) {
			// make a new sound where it hits.
			world.playSound(end, Sound.ENTITY_FIREWORK_BLAST, 1.0f, 1.5f);
		}
		if (type.getFireChance() > 0.0d) {
			if (start != null) {
				double distance = end.distance(start);
	
				Vector vector = end.subtract(start).toVector();
				vector = vector.multiply(0.1d / distance);
				for (int i = 0; i < distance; i++) {
					world.spawnParticle(Particle.SMOKE_NORMAL, start.add(vector), 5, 0.01, 0.01, 0.01, 0.001);
				}
			}
		}
	}
	
	/**
	 * Override this class, you use it to manage what happens when a non-Damageable is hit.
	 * 
	 * @param hitData the Data matrix showing hit information
	 * @param hit the non-damageable entity that was struck
	 * @param bullet The "Projectile" bullet doing the hitting
	 * @param bulletType The "Bullet" type of the projectile
	 */
	public void manageHit(HitDigest hitData, Entity hit, Projectile bullet, Bullet bulletType) {
		// do nothing in standard gun.
		Location end = hitData.hitLocation;
		World world = end.getWorld();
		world.playSound(end, Sound.BLOCK_GLASS_HIT, 1.0f, 1.5f);
		world.spawnParticle(Particle.SMOKE_NORMAL, end, 35, 0.1, 0.1, 0.1, 0.1);
	}

	/**
	 * This is a complex method. It handles the various hitvectors and computations of damage both to
	 * player/entity and armor. I'll write up a full explanation in the documentation of configuration.
	 * 
	 * For now it suffices that depending on _where_ the damage is done, and what is being worn there, depends
	 * on the nature of the damage inflicted.
	 * 
	 * @param hitData the Data matrix showing hit information
	 * @param hit the damageable entity that was struck
	 * @param bullet the "Projectile" bullet doing the hitting
	 * @param bulletType the "Bullet" type of the projectile
	 */
	public void manageDamage(HitDigest hitData, Damageable hit, Projectile bullet, Bullet bulletType) {
		// TODO: manage armor and armor bypass
		double median = bulletType.getAvgHitDamage(hitData.nearestHitPart);
		double twosigma = bulletType.getSpreadHitDamage(hitData.nearestHitPart);
		
		Vector speed = bullet.getVelocity();
		double absVelocity = speed.length();
		// speed is a uniform random variable between min and max, we now approximate it into a gaussian distribution using two shaped sigmoids
		double variance = (absVelocity - this.avgSpeed) / ((this.maxSpeed - this.minSpeed + 1) / 4.0);
		double varMin = variance * StandardGun.expansionFactor + this.varianceMin;
		double varMax = variance * StandardGun.expansionFactor + this.varianceMax;
		double lefttail = (twosigma/2) * (1 + (varMin / Math.sqrt(1 + (varMin * varMin))));
		double righttail = (twosigma/2) * (1 + (varMax / Math.sqrt(1 + (varMax * varMax))));
		
		// compose the two curves, forming an accentuated guassian distribution (more heavily center weighted then a true normal curve)
		double baseRealDamage = median + (lefttail + righttail - twosigma) * StandardGun.finalFitFactor;
		
		double originalBaseDamage = baseRealDamage;

		double finalDamage = baseRealDamage;
		ItemStack armorHit = null;
		ItemStack shield = null;
		double shieldEffectiveness = 0.0d;
		
		LivingEntity living = null;
		EntityEquipment equipment = null;
		
		HumanEntity human = null;
		
		if (hit instanceof LivingEntity) {
			living = (LivingEntity) hit;
			equipment = living.getEquipment();
			// now reductions?	
			switch(hitData.nearestHitPart) {
			case BODY:
			case CHEST_PLATE:
			case LEFT_ARM:
			case LEFT_FOOT:
			case LEFT_HAND:
			case RIGHT_ARM:
			case RIGHT_FOOT:
			case RIGHT_HAND:// all variants on body atm
				armorHit = equipment.getChestplate();
				shield = equipment.getItemInOffHand();
				break;
			case BOOTS:
			case FEET: // just feet
				armorHit = equipment.getBoots();
				break;
			case HEAD:
			case HELMET: // just head
				armorHit = equipment.getHelmet();
				shield = equipment.getItemInOffHand();
				break;
			case LEGGINGS:
			case LEFT_LEG:
			case RIGHT_LEG: 
			case LEGS: // just legs
				armorHit = equipment.getLeggings();
				break;
			case MISS: // no hit?
				nearMiss(hitData, hit, bullet, bulletType);
				return;
			default:
				break;
			}
			
			if (shield != null && Material.SHIELD.equals(shield.getType())) {
				if (hit instanceof HumanEntity) {
					human = (HumanEntity) hit;
					if (human.isBlocking()) {
						shieldEffectiveness = 1.0d;
					} else if (human.isHandRaised()) {
						shieldEffectiveness = 0.5d;
					}
				}
			} else {
				shield = null;
			}
		}

		if (shield != null) {
			// check bypass?
			double angle = Math.toDegrees(hit.getLocation().getDirection().angle(speed));
			// TODO?!?! check this
			if (angle >= -135 && angle < 135) {
				// no deflection!
				shieldEffectiveness = 0.0d;
			}
			
			finalDamage *= (1.0 - shieldEffectiveness);
			
			AddGun.getPlugin().debug(String.format("Base damage of %.2f, Has shield %s, angle of %.2f, effectiveness %.2f, final Damage %.2f",
					baseRealDamage, shield, angle, shieldEffectiveness, finalDamage));

			int unbLevel = shield.getEnchantmentLevel(Enchantment.DURABILITY);
			
			// Basically, unbreaking reduces the amount of durability damage that the armor will sustain 
			double unbReduction = (1.0 - StandardGun.baseUnbreakingProtection * StandardGun.protectionCurve[unbLevel]);
			double finalDuraDamage = baseRealDamage * (1.0 - (1.0 - bulletType.getArmorDamage(ArmorType.SHIELD)) * unbReduction);
			if (shield.getDurability() < finalDuraDamage) {
				// broken.
				double directDamage = finalDuraDamage - shield.getDurability();
				
				finalDamage += directDamage; // this is the bit of damage the shield was meant to absorb, but didn't.
				
				shield = null;
				
				AddGun.getPlugin().debug(String.format("Shield broken by dura damage %.2f, adding the remaining %.2f to the player damage", 
						finalDuraDamage, directDamage));
			} else {
				shield.setDurability((short) (shield.getDurability() - Math.round(finalDuraDamage)));
				
				AddGun.getPlugin().debug(String.format("Shield damaged by %.2f", finalDuraDamage));
			}
		}

		// reset base damage
		baseRealDamage = finalDamage;
		
		if (armorHit != null && baseRealDamage > 0.0d) {
			// check bypass?
			
			// check armor type
			ArmorType grade = getArmorType(armorHit.getType()); 
			int protLevel = armorHit.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
			int projLevel = armorHit.getEnchantmentLevel(Enchantment.PROTECTION_PROJECTILE);
			int unbLevel = armorHit.getEnchantmentLevel(Enchantment.DURABILITY);
			
			if (projLevel > 15) projLevel = 15;
			if (protLevel > 15) protLevel = 15;
			if (unbLevel > 15) unbLevel = 15;
		
			// get base modifier from Bullet in terms of how much a type of armor protects you from this type of bullet
			// then adjust based on prot and proj levels.
			double reduction = bulletType.getArmorReduction(grade);
			double protReduction = (1.0 + StandardGun.baseEnvironmentProtection * StandardGun.protectionCurve[protLevel]);
			double projReduction = (1.0 + StandardGun.baseProjectileProtection * StandardGun.protectionCurve[projLevel]);
			
			double finalReduce = reduction * protReduction * projReduction;
			
			if (finalReduce > 1.0) finalReduce = 1.0;
			
			finalDamage *= (1.0 - finalReduce);
			
			AddGun.getPlugin().debug(String.format("Has armor %s, baseDamage of %.2f, base reduction %.2f, prot %.2f, proj %.2f, final Damage %.2f",
					armorHit, baseRealDamage, reduction, protReduction, projReduction, finalDamage));
			
			// Basically, unbreaking reduces the amount of durability damage that the armor will sustain 
			double unbReduction = (1.0 + StandardGun.baseUnbreakingProtection * StandardGun.protectionCurve[unbLevel]);
			double finalDuraDamage = baseRealDamage * (1.0 - (1.0 - bulletType.getArmorDamage(grade)) * unbReduction);
			if (armorHit.getDurability() + finalDuraDamage > armorHit.getType().getMaxDurability()) {
				// broken.
				double directDamage = finalDuraDamage - (armorHit.getType().getMaxDurability() - armorHit.getDurability());
				
				finalDamage += directDamage; // this is the bit of damage the armor was meant to absorb, but didn't.
				
				armorHit = null;
				
				AddGun.getPlugin().debug(String.format("Armor broken by dura damage %.2f, adding the remaining %.2f to the player damage", 
						finalDuraDamage, directDamage));
			} else {
				armorHit.setDurability((short) (armorHit.getDurability() + Math.round(finalDuraDamage)));
				
				AddGun.getPlugin().debug(String.format("Armor damaged by %.2f", finalDuraDamage));
			}
		}
		
		if (equipment != null) {
			ItemStack tShield = equipment.getItemInOffHand();

			switch(hitData.nearestHitPart) {
			case BODY:
			case CHEST_PLATE:
			case LEFT_ARM:
			case LEFT_FOOT:
			case LEFT_HAND:
			case RIGHT_ARM:
			case RIGHT_FOOT:
			case RIGHT_HAND:// all variants on body atm
				equipment.setChestplate(armorHit);
				if (tShield != null && Material.SHIELD.equals(tShield.getType())) {
					equipment.setItemInOffHand(shield);
				}
				break;
			case BOOTS:
			case FEET: // just feet
				equipment.setBoots(armorHit);
				break;
			case HEAD:
			case HELMET: // just head
				equipment.setHelmet(armorHit);
				if (tShield != null && Material.SHIELD.equals(tShield.getType())) {
					equipment.setItemInOffHand(shield);
				}
				break;
			case LEGGINGS:
			case LEFT_LEG:
			case RIGHT_LEG: 
			case LEGS: // just legs
				equipment.setLeggings(armorHit);
				break;
			case MISS: // no hit?
				nearMiss(hitData, hit, bullet, bulletType);
				break;
			default:
				break;
			}
		}

		AddGun.getPlugin().debug(String.format("Orig Base damage %.2f, Base damage of %.2f, Final damage of %.2f",
				originalBaseDamage, baseRealDamage, finalDamage));
		
		final double trueFinalDamage = finalDamage;
		//TODO: player states? custom shit? event?
		Bukkit.getScheduler().runTask(AddGun.getPlugin(), new Runnable() {
			@SuppressWarnings("deprecation")
			@Override
			public void run() {
				hit.setLastDamageCause(new EntityDamageByEntityEvent(bullet, hit, DamageCause.PROJECTILE, trueFinalDamage));
				try {
					AddGun.getPlugin().getTagUtility().tag((LivingEntity) bullet.getShooter(), (LivingEntity) hit);
				} catch(Exception e) {}
				hit.damage(trueFinalDamage, bullet);
			}
		});
	}
	


	/**
	 * Any post-hit cleanup can be handled here. This would be stuff not associated with manageHit or manageDamage.
	 * 
	 *  Currently handles explosions and fire.
	 *  
	 * @param hitData the Data matrix describing hit information
	 * @param hit the Entity that was hit, after handling manageHit -- or if ground impact, will be null.
	 * @param bullet the "Projectile" bullet doing the hitting
	 * @param bulletType the "Bullet" type of the projectile.
	 */
	public void postHit(HitDigest hitData, Entity hit, Projectile bullet, Bullet bulletType) {
		// this does any incendary effects / explosions for 
		Location loc = hitData.hitLocation.clone();
		World world = loc.getWorld();
		StringBuffer dbg = new StringBuffer();
		
		double random = Math.random();
		if (random < bulletType.getExplosionChance()) {
			// boom.
			random = Math.random();
			if (random < bulletType.getFireChance()) { // incendary!
				// fire
				Bukkit.getScheduler().runTaskLater(AddGun.getPlugin(), new Runnable() {
					@Override
					public void run() {
						world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), bulletType.getExplosionLevel(), true, true);
						world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), bulletType.getExplosionLevel() * 3, false, false);
					}
				} , 1l);
				dbg.append("Created explosion ").append(bulletType.getExplosionLevel()).append(" on hit, with fire ");
			} else {
				Bukkit.getScheduler().runTaskLater(AddGun.getPlugin(), new Runnable() {
					@Override
					public void run() {
						world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), bulletType.getExplosionLevel(), false, true);
						world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), bulletType.getExplosionLevel() * 3, false, false);
					}
				}, 1l);
				dbg.append("Created explosion ").append(bulletType.getExplosionLevel()).append(" on hit, without fire ");
			}
		}

		random = Math.random();
		if (hit != null && random < bulletType.getFireChance()) { // incendary!
			// fire
			hit.setFireTicks(bulletType.getFireTicks());
			dbg.append("Lit hit on fire");
		}
		
		AddGun.getPlugin().debug(dbg.toString());
		
		if (hit == null) {
			world.spawnParticle(Particle.EXPLOSION_NORMAL, loc, 50, 1.0, 1.0, 1.0, 0.1);
		}
	}

	/**
	 * Any post-miss cleanup can be handled here. Misses will automatically run this function, and
	 * it includes pre- and post-miss data.
	 * 
	 * @param hitData the Data matrix describing miss information
	 * @param hit the Entity that was missed, after miss management and continue projectile spawn.
	 * @param bullet the "Projectile" bullet doing the original missing
	 * @param continueBullet Since the original projectile is removed, this is a "continue" bullet spawned _after_ the miss entity.
	 * @param bulletType the "Bullet" type of the projectiles.
	 */
	public void postMiss(HitDigest hitData, Entity hit, Projectile bullet, Projectile continueBullet,
			Bullet bulletType) {}
	
	
	/**
	 * A basic shoot method, it _can_ be overridden but take care.
	 * Handles passing the bullet to its BulletType for configuration, sets shooter, velocity, etc.
	 * 
	 * @param begin The location to shoot from
	 * @param bulletType the Bullet type of this bullet
	 * @param shooter the entity shooting
	 * @param velocity the velocity to use as base for this shooting, if any
	 * @param overrideVelocity if true, use the passed velocity and override anything set up by the bullet type.
	 * @return the new Projectile that has been unleashed.
	 */
	public Projectile shoot(Location begin, Bullet bulletType, ProjectileSource shooter, Vector velocity, boolean overrideVelocity) {
		World world = begin.getWorld();
		begin = begin.clone();
		begin.setDirection(velocity);
		Projectile newBullet = world.spawn(begin, bulletType.getProjectileType() );
				
		newBullet.setCustomName(this.bulletTag);
		newBullet.setBounce(false);
		newBullet.setGravity(true);
		newBullet.setShooter(shooter);
		
		bulletType.configureBullet(newBullet, world, shooter, velocity);
		
		if (overrideVelocity) {
			newBullet.setVelocity(velocity);
		}

		return newBullet;
	}

	/* Shooting helpers */
	
	/**
	 * This computes if enough XP is present to fire the gun and bullet that's chambered
	 * 
	 * @param entity the shooter
	 * @param bullet the Bullet type that's chambered
	 * @return true if enough fuel, false otherwise
	 */
	boolean hasFuel(LivingEntity entity, Bullet bullet) {
		if (!this.usesXP && !bullet.getUsesXP()) return true; // no xp needs.
		
		int totalDraw = this.xpDraw + bullet.getXPDraw();
		
		int xpNeeds = computeTotalXP(entity) - totalDraw; 

		if (xpNeeds < 0 && (xpNeeds + AddGun.getPlugin().getXpPerBottle() * getInvXp(entity) < 0)) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Checks if the proferred item is a gun.
	 * 
	 * @param toCheck the item to check if this gun
	 * @return true if gun, false otherwise
	 */
	@Override	
	public boolean isGun(ItemStack toCheck) {
		if (!enabled || toCheck == null)
			return false;

		if (!gunExample.getType().equals(toCheck.getType()))
			return false;

		if (!toCheck.hasItemMeta())
			return false;

		ItemMeta meta = toCheck.getItemMeta();

		if (meta.hasLore() && meta.getLore().contains(tag))
			return true;

		return false;
	}
	
	/**
	 * Checks if this gun has enough health to still fire
	 * 
	 * @param toCheck Confirms that object has gun nbt, otherwise doesn't check if gun, just checks health
	 * @return true if alive, false otherwise.
	 */
	public boolean isAlive(ItemStack toCheck) {
		if (toCheck == null) return false;
		return isAlive(getGunData(toCheck));
	}
	
	/**
	 * Checks if this gun is alive using a converted NBT data package.
	 * 
	 * @param data the data map to check, must have a "health" object
	 * @return true if health is above 0, false otherwise.
	 */
	public boolean isAlive(Map<String, Object> data) {
		if (data == null || data.isEmpty()) return false;
		
		Object health = data.get("health");
		if (health != null && health instanceof Integer) {
			return ((Integer) health) > 0;
		}
		return false;
	}
	
	/**
	 * Checks if this gun has bullets / clips with bullets
	 * 
	 * @param toCheck Confirms that object has gun nbt, otherwise Does _not_ confirm if gun, just checks for ammo signals.
	 * @return true if ammo found, false otherwise. Does not check if ammo works or not.
	 */
	public boolean isLoaded(ItemStack toCheck) {
		if (toCheck == null ) return false;
		return isLoaded(getGunData(toCheck));
	}
	
	/**
	 * Checks if this gun is alive using a converted NBT data package
	 * 
	 * @param data the data map to check, must have a "rounds" object
	 * @return true if "rounds" is above 0, false otherwise.
	 */
	public boolean isLoaded(Map<String, Object> data) {
		if (data == null || data.isEmpty()) return false;
		
		Object rounds = data.get("rounds");
		if (rounds != null && rounds instanceof Integer) {
			return ((Integer) rounds) > 0;
		}
		
		return false;
	}

	/**
	 * Returns the unique imprint put on every bullet by this gun.
	 * 
	 * @return the imprint
	 */
	public String getBulletTag() {
		return this.bulletTag;
	}
	
	/**
	 * Returns the unique imprint put on every gun.
	 * 
	 * @return the imprint
	 */
	public String getGunTag() {
		return this.tag;
	}
	
	/**
	 * Gets the currently loaded Bullet type, regardless of clip or no clip
	 * 
	 * @param toCheck the gun to check
	 * @return the ammo type, or null if unloaded
	 */
	public Bullet getAmmo(ItemStack toCheck) {
		if (toCheck == null) return null;
		
		return getAmmo(getGunData(toCheck));
	}

	public Bullet getAmmo(Map<String, Object> data) {
		if (data == null || data.isEmpty()) return null;
		
		Object ammo = data.get("ammo");
		if (ammo != null && ammo instanceof String) {
			return AddGun.getPlugin().getAmmo().getBullet((String) ammo);
		}

		return null;
	}
	
	/**
	 * Returns a 2 element array -- element 0 is the gun, possibly modified, element 1 is the ammo/clip if any.
	 * 
	 * @param gun the gun to remove ammo from
	 * @return the gun and ammo if any, as a 2 element array
	 */
	public ItemStack[] unloadAmmo(ItemStack gun) {
		if (gun == null) return new ItemStack[] {gun, null};
		
		Map<String, Object> gunData = getGunData(gun);
		if (gunData == null || gunData.isEmpty()) return new ItemStack[] {gun, null};
		
		AmmoType type = (AmmoType) gunData.get("type");
		if (type == null) {
			return new ItemStack[] {gun, null};
		}
		ItemStack ammo = null;
		Bullet bullet = null;
		switch(type) {
		case BULLET:
			if (!gunData.containsKey("ammo")) return new ItemStack[] {gun, null};
			bullet = AddGun.getPlugin().getAmmo().getBullet((String) gunData.get("ammo"));

			if ((Integer) gunData.get("rounds") > 0) {
				ammo = bullet.getBulletItem();
				ammo.setAmount((Integer) gunData.get("rounds"));
			}
			
			gunData.put("ammo", null);
			break;
		case CLIP:
			if (!gunData.containsKey("clip")) return new ItemStack[] {gun, null};
			
			Clip clip = AddGun.getPlugin().getAmmo().getClip((String) gunData.get("clip"));
			
			if (gunData.containsKey("ammo")) { 
				bullet = AddGun.getPlugin().getAmmo().getBullet((String) gunData.get("ammo"));
				if ((Integer) gunData.get("rounds") > 0) {
					ammo = clip.getClipItem(bullet, (Integer) gunData.get("rounds"));
				} else {
					ammo = clip.getClipItem(bullet, 0);
				}
			} else {
				ammo = clip.getClipItem(null, 0);
			}
			break;
		case INVENTORY:
		default:
			break;
		}
		gunData.clear();
		gunData.put("ammo", null);
		gunData.put("clip", null);
		gunData.put("rounds", Integer.valueOf(0));
		
		gun = updateGunLore(updateGunData(gun, gunData));
		
		return new ItemStack[] {gun, ammo};
	}
	
	/**
	 * Attempts to load the gun with ammo, based on ItemStacks. If the gun is already loaded, what happens depends
	 * a lot on what's in the gun already. If the ammo being presented is the same type, it'll "top off" the load.
	 * If the gun is loaded with a clip, and a clip is presented and valid, it'll swap out the clip.
	 * 
	 * @param gun the gun to load
	 * @param ammo the ammo to use (bullets or clips)
	 * @return a 2 element array; element 0 is the gun, potentially modified. element 1 is the remaining ammo or null.
	 */
	public ItemStack[] loadAmmo(ItemStack gun, ItemStack ammo, HumanEntity player) {
		if (gun == null || ammo == null) return new ItemStack[] {gun, ammo};
		
		Map<String, Object> gunData = getGunData(gun);
		if (gunData == null || gunData.isEmpty()) return new ItemStack[] {gun, ammo};
		
		AmmoType type = (AmmoType) gunData.get("type");
		switch(type) {
		case BULLET:
			Bullet bullet = AddGun.getPlugin().getAmmo().findBullet(ammo);
			if (bullet != null) { // else, takes bullets, but something else was offered.
				if (this.allBullets.contains(bullet.getName())) {
					if (!gunData.containsKey("ammo")) { // unloaded! easy path
						int load = Math.min(ammo.getAmount(), this.maxAmmo);
						
						LoadGunEvent event = new LoadGunEvent(this, null, bullet, ammo.getAmount() - load, player);
						Bukkit.getServer().getPluginManager().callEvent(event);
						if (event.isCancelled()) return new ItemStack[] {gun, ammo};
						
						gunData.clear();
						gunData.put("ammo", bullet.getName());
						gunData.put("rounds", load);
						ammo.setAmount(ammo.getAmount() - load);
						if (ammo.getAmount() <= 0) {
							ammo = null;
						}
						gun = updateGunLore(updateGunData(gun, gunData));
					} else { //loaded!
						Bullet loadedBullet = AddGun.getPlugin().getAmmo().getBullet((String) gunData.get("ammo"));
						if (loadedBullet.equals(bullet)) { // same kind of bullet!
							int currentLoad = (Integer) gunData.get("rounds");
							int load = Math.min( currentLoad + ammo.getAmount(), this.maxAmmo);

							LoadGunEvent event = new LoadGunEvent(this, null, bullet, currentLoad + ammo.getAmount() - this.maxAmmo, player);
							Bukkit.getServer().getPluginManager().callEvent(event);
							if (event.isCancelled()) return new ItemStack[] {gun, ammo};

							gunData.clear();
							gunData.put("rounds", load);
							ammo.setAmount(currentLoad + ammo.getAmount() - this.maxAmmo);
							if (ammo.getAmount() <= 0) {
								ammo = null;
							}
							gun = updateGunLore(updateGunData(gun, gunData));
						} // either can't figure out the bullet type or can't safely "swap" bullets, do a clean unload first
					}
				}
			}
			break;
		case CLIP:
			Clip clip = AddGun.getPlugin().getAmmo().findClip(ammo);
			if (clip != null) { // else, takes bullets, but something else was offered.
				if (this.allClips.contains(clip.getName())) {
					if (!gunData.containsKey("clip")) { // unloaded! easy path
						gunData.clear();
						Bullet bullt = clip.getBulletType(ammo);
						
						LoadGunEvent event = new LoadGunEvent(this, clip, bullt, clip.getRounds(ammo), player);
						Bukkit.getServer().getPluginManager().callEvent(event);
						if (event.isCancelled()) return new ItemStack[] {gun, ammo};

						
						gunData.put("ammo", bullt == null ? null : bullt.getName());
						gunData.put("clip", clip.getName());
						gunData.put("rounds", clip.getRounds(ammo));
						ammo.setAmount(ammo.getAmount() - 1);
						if (ammo.getAmount() <= 0) {
							ammo = null;
						}
						gun = updateGunLore(updateGunData(gun, gunData));
					} else { //loaded!
						if (ammo.getAmount() == 1) { // we can swap!
							Bullet oldBullet = AddGun.getPlugin().getAmmo().getBullet((String) gunData.get("ammo"));
							Clip oldClip = AddGun.getPlugin().getAmmo().getClip((String) gunData.get("clip"));
							Integer oldRounds = (Integer) gunData.get("rounds");
							if (oldRounds <= 0) {
								oldRounds = 0;
							}

							Bullet bullt = clip.getBulletType(ammo);
							LoadGunEvent event = new LoadGunEvent(this, clip, bullt, clip.getRounds(ammo), player);
							Bukkit.getServer().getPluginManager().callEvent(event);
							if (event.isCancelled()) return new ItemStack[] {gun, ammo};
							
							gunData.clear();
							gunData.put("ammo", bullt.getName());
							gunData.put("clip", clip.getName());
							gunData.put("rounds", clip.getRounds(ammo));
							gun = updateGunLore(updateGunData(gun, gunData));

							ammo = oldClip.getClipItem(oldBullet, oldRounds);
						} // else we do nothing, we can't clean swap.
					}
				}
			}
			break;
		case INVENTORY:
		default:
			// do nothing
			break;
		}
		return new ItemStack[] {gun, ammo};
	}

	/**
	 * This figures out how to pay for the firing, based on all the needs of the gun, bullet, etc.
	 * 
	 * It handles any inventory removals.
	 * 
	 * @param entity the entity shooting
	 * @param bulletType the type of bullet
	 * @param gun the gun item
	 * @param gunData the embedded gun data
	 * @param hand the hand that held the gun
	 * @return true if the shot is paid for, false otherwise. At this point all deducations are made, whether true or false (no refunds)
	 */
	public boolean payForShot(LivingEntity entity, Bullet bulletType, ItemStack gun, Map<String, Object> gunData, EquipmentSlot hand) {
		if (entity == null || !enabled) {
			return false;
		}

		if (entity instanceof InventoryHolder) {
			// complex inventory
			InventoryHolder holder = (InventoryHolder) entity;
			boolean foundBullet = false;
			boolean xp = !(entity instanceof Player);
			if (AmmoType.INVENTORY.equals((AmmoType) gunData.get("type"))) {
				for (Map.Entry<Integer,? extends ItemStack> bullet : holder.getInventory().all(bulletType.getMaterialType()).entrySet()) {
					ItemStack maybeBullets = bullet.getValue();
					if (bulletType.isBullet(maybeBullets) && maybeBullets.getAmount() > 0) {
						maybeBullets.setAmount(maybeBullets.getAmount() - 1);
						if (maybeBullets.getAmount() > 0) {
							holder.getInventory().setItem(bullet.getKey(), maybeBullets);
						} else {
							holder.getInventory().clear(bullet.getKey());
						}
						foundBullet = true;
						int health = (Integer) gunData.get("health");
						long shots = (Long) gunData.get("lifetimeShots");						
						gunData.clear();
						gunData.put("health", health - 1);
						gunData.put("lifetimeShots", shots + 1);
						gun = updateGunLore(updateGunData(gun, gunData));
						switch(hand) {
						case HAND:
							entity.getEquipment().setItemInMainHand(gun);
							break;
						case OFF_HAND:
							entity.getEquipment().setItemInOffHand(gun);
							break;
						default:
							return false;
						}

						break;
					}
				}
			} else {
				// withdraw from the gun, or try to
				Object rounds = gunData.get("rounds");
				if (rounds != null && rounds instanceof Integer && ((Integer) rounds) > 0) {
					foundBullet = true;
					int newRounds = ((Integer) rounds) - 1;
					int health = (Integer) gunData.get("health");
					long shots = (Long) gunData.get("lifetimeShots");
					gunData.clear();
					if (newRounds > 0) {
						gunData.put("rounds", ((Integer) rounds) - 1);
					} else { // empty.
						if (AmmoType.BULLET.equals((AmmoType) gunData.get("type"))) {
							gunData.put("ammo", null);
						} // if it's a clip we just zero out rounds but leave the clip "loaded".
						gunData.put("rounds", Integer.valueOf(0));
					}
					gunData.put("health", health - 1);
					gunData.put("lifetimeShots", shots + 1);
					gun = updateGunLore(updateGunData(gun, gunData));
					switch(hand) {
					case HAND:
						entity.getEquipment().setItemInMainHand(gun);
						break;
					case OFF_HAND:
						entity.getEquipment().setItemInOffHand(gun);
						break;
					default:
						return false;
					}
				} else {
					return false; // no ammo in gun!
				}
			}
			
			if (foundBullet && !xp && (this.usesXP || bulletType.getUsesXP())) {
				Player player = (Player) entity;
				int xpNeeds = computeTotalXP(player) - this.xpDraw - bulletType.getXPDraw(); 

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
				} else {
					xp = true;
				}
				player.setLevel(0);
				player.setExp(0.0f);
				player.giveExp(xpNeeds);
			}
			
			return foundBullet && xp;
		} else {
			if (AmmoType.INVENTORY.equals((AmmoType) gunData.get("type"))) {
				// simple inventory, no xp
				ItemStack maybeBullets = EquipmentSlot.HAND.equals(hand) ? entity.getEquipment().getItemInOffHand() : entity.getEquipment().getItemInMainHand(); 
				if(bulletType.isBullet(maybeBullets)) {
					if (maybeBullets.getAmount() < 1) {
						return false;
					} else {
						maybeBullets.setAmount(maybeBullets.getAmount() - 1);
						if (maybeBullets.getAmount() > 0) {
							if (EquipmentSlot.HAND.equals(hand)) {
								entity.getEquipment().setItemInOffHand(maybeBullets);
							} else {
								entity.getEquipment().setItemInMainHand(maybeBullets);
							}
						} else {
							if (EquipmentSlot.HAND.equals(hand)) {
								entity.getEquipment().setItemInOffHand(null);
							} else {
								entity.getEquipment().setItemInMainHand(null);
							}
						}
						
						int health = (Integer) gunData.get("health");
						long shots = (Long) gunData.get("lifetimeShots");
						// deduct health from gun.
						gunData.clear();
						gunData.put("health", health - 1);
						gunData.put("lifetimeShots", shots + 1);
						gun = updateGunLore(updateGunData(gun, gunData));
						switch(hand) {
						case HAND:
							entity.getEquipment().setItemInMainHand(gun);
							break;
						case OFF_HAND:
							entity.getEquipment().setItemInOffHand(gun);
							break;
						default:
							return false;
						}

						return true;
					}
				}
			} else {
				// withdraw from the gun, or try to
				Object rounds = gunData.get("rounds");
				if (rounds != null && rounds instanceof Integer && ((Integer) rounds) > 0) {
					int newRounds = ((Integer) rounds) - 1;
					int health = (Integer) gunData.get("health");
					long shots = (Long) gunData.get("lifetimeShots");
					gunData.clear();
					if (newRounds > 0) {
						gunData.put("rounds", ((Integer) rounds) - 1);
					} else { // empty.
						if (AmmoType.BULLET.equals((AmmoType) gunData.get("type"))) {
							gunData.put("ammo", null);
						} // if it's a clip we just zero out rounds but leave the clip "loaded".
						gunData.put("rounds", Integer.valueOf(0));
					}
					gunData.put("health", health - 1);
					gunData.put("lifetimeShots", shots + 1);
					gun = updateGunLore(updateGunData(gun, gunData));
					switch(hand) {
					case HAND:
						entity.getEquipment().setItemInMainHand(gun);
						break;
					case OFF_HAND:
						entity.getEquipment().setItemInOffHand(gun);
						break;
					default:
						return false;
					}
				} else {
					return false; // no ammo in gun!
				}				
			}
		}
		return false;

	}

	/**
	 * This figures out if the gun's ammo is in the entity's inventory. 
	 * 
	 * @param entity the entity to check
	 * @return the Bullet type found that is valid for this gun, or null.
	 */
	public Bullet getAmmo(LivingEntity entity) {
		if (entity == null || !enabled)
			return null;

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
				Bullet bullet = AddGun.getPlugin().getAmmo().findBullet(item);
				if (bullet != null && this.allBullets.contains(bullet.getName())) {
					return bullet;
				}
			}
		}
		return null;
	}

	/**
	 * Computes chance that the gun misfires! Yikes.
	 * 
	 * Misfire is based on when you last repaired the gun. A misfire has a chance of causing a gun to explode (handled in another function)
	 * 
	 * 
	 * 
	 * @param entity the entity shooting the gun
	 * @param bulletType the type of bullet
	 * @param item the gunItem, could be modified by this.
	 * @param gunData the gunData
	 * @param hand the hand holding the gun.
	 * 
	 * @return true if misfired, false otherwise.
	 */
	public boolean misfire(LivingEntity entity, Bullet bulletType, ItemStack item, Map<String, Object> gunData,
			EquipmentSlot hand) {
		if (entity == null || !enabled) 
			return true;
		
		Integer health = (Integer) gunData.get("health"); // gunhealth!
		double misfireChance = 1.0d - sigmoid((double) health, (double) this.middleRisk,0.5d, (double) this.riskSpread);
		
		double random = Math.random();
		AddGun.getPlugin().debug("Misfire computation: {0} health {1} misfireChance {2} random", health, misfireChance, random);
		if (random < misfireChance) {
			return true;
		}
		return false;
	}

	/**
	 * Gets chance that the misfiring gun also explodes! Yikes.
	 * 
	 * @param entity the entity shooting the gun
	 * @param bulletType the type of bullet
	 * @param item the gunItem, could be modified by this.
	 * @param gunData the gunData
	 * @param hand the hand holding the gun.
	 * @return true if blowout, false otherwise
	 */
	public boolean blowout(LivingEntity entity, Bullet bulletType, ItemStack gun, Map<String, Object> gunData,
			EquipmentSlot hand) {
		if (entity == null || !enabled)
			return true;
		
		double random = Math.random();
		if (random < this.misfireBlowoutChance) {
			Location explosion = entity.getLocation().clone().add(0.0d, 1.3d, 0.0d);
			World world = explosion.getWorld();
			random = Math.random();
			world.createExplosion(explosion.getX(), explosion.getY(), explosion.getZ(), this.baseBlowoutStrength + bulletType.getExplosionLevel(), (random < bulletType.getFireChance()) ? true : false, true);

			gunData.clear();
			gunData.put("health", Integer.valueOf(0));
			gun = updateGunLore(updateGunData(gun, gunData));
			switch(hand) {
			case HAND:
				entity.getEquipment().setItemInMainHand(gun);
				break;
			case OFF_HAND:
				entity.getEquipment().setItemInOffHand(gun);
				break;
			default:
			}
			
			return true;
		}
		return false;
	}

	/**
	 * This just fires off a sound. Successor types could do more, or maybe I'll think of something later.
	 * 
	 * @param loc the location of firing
	 * @param entity the entity shooting
	 * @param bullet the new projectile
	 * @param bulletType the type of the projectile (as a bullet)
	 */
	public void postShoot(Location loc, LivingEntity entity, Projectile bullet, Bullet bulletType) {
		Object particleData = Particle.FLAME.getDataType();
		loc.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, loc, 5, 0.1, 0.1, 0.1, 0.1);

		loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_LARGE_BLAST_FAR, 10.0f, 1.0f);
	}

	/**
	 * Based on shooting the gun, jerks the player's head to the direction of firing
	 * 
	 * @param entity the entity shooting
	 * @param direction the direction of shot
	 */
	public void knockback(LivingEntity entity, Vector direction) {
		if (entity instanceof Player) {
			Player p = (Player) entity;
			Vector begin = p.getEyeLocation().getDirection().clone();
			Vector end = begin.clone().getMidpoint(direction).getMidpoint(begin); // 25% of "direction" between start and where fire occurred.
			long kickback = Math.max(Math.min(this.cooldown / 3, (long) Animation.FRAME_DELAY * 3l), (long) Animation.FRAME_DELAY * 2l);
			long kickdown = Math.max((long) Animation.FRAME_DELAY * 2l, this.cooldown - kickback);
			AddGun.getPlugin().getPlayerListener().playAnimation(p, new Animation(p, begin, kickback, direction, kickdown, end));
		} else {
			entity.teleport(entity.getLocation().setDirection(direction), TeleportCause.PLUGIN);
		}
	}
	
	/**
	 * Given a gun object, updates the lore to reflect the NBT
	 * 
	 * @param gun the gun to update
	 * @return the gun, with updated lore.
	 */
	public ItemStack updateGunLore(ItemStack gun) {
		ItemMeta meta = gun.getItemMeta();
		meta.setUnbreakable(true);
		List<String> lore = meta.getLore();
		if (lore == null) {
			lore = new ArrayList<String>();
		} else {
			lore.clear();
		}
		lore.add(this.tag);
		if (this.gunLore != null && !this.gunLore.isEmpty()) {
			lore.addAll(this.gunLore);
		}
		Map<String, Object> gunData = getGunData(gun);
		 
		AmmoType type = (AmmoType) gunData.get("type");
		Integer rounds = (Integer) gunData.get("rounds");
		String bullet = null;
		switch(type) {
		case BULLET:
			if (gunData.containsKey("ammo")) {
				bullet = (String) gunData.get("ammo");
				lore.add(ChatColor.GREEN + "Bullet " + ChatColor.GRAY + bullet + ChatColor.GREEN + " loaded");
	
				if (rounds <= 0) {
					lore.add(ChatColor.RED + "  CHAMBER EMPTY");
				} else if (rounds == 1) {
					lore.add(ChatColor.GOLD + "  1 " + ChatColor.BLUE + "Round");
				} else {
					lore.add(ChatColor.GREEN + String.format("  %d ", rounds) + ChatColor.BLUE + "Rounds");
				}
			} else {
				lore.add(ChatColor.GREEN + "Gun accepts bullets: ");
				for (String bull : this.allBullets) {
					lore.add(ChatColor.GREEN + " - " + ChatColor.GRAY + bull);
				}
			}
			break;
		case CLIP:
			if (gunData.containsKey("clip")) {
				String clip = (String) gunData.get("clip");
				if (gunData.containsKey("ammo")) { // locked clip
					bullet = (String) gunData.get("ammo");
					lore.add(ChatColor.GREEN + "Clip " + ChatColor.WHITE + clip + ChatColor.GREEN + " of " + ChatColor.GRAY + bullet + ChatColor.GREEN + " loaded");
				} else {
					lore.add(ChatColor.GREEN + "Clip " + ChatColor.WHITE + clip + ChatColor.GREEN + " loaded");
				}
				if (rounds <= 0) {
					lore.add(ChatColor.RED + "  MAGAZINE EMPTY");
				} else if (rounds == 1) {
					lore.add(ChatColor.GOLD + "  1 " + ChatColor.BLUE + "Round");
				} else {
					lore.add(ChatColor.GREEN + String.format("  %d ", rounds) + ChatColor.BLUE + "Rounds");
				}
			} else {
				lore.add(ChatColor.GREEN + "Gun accepts clips: ");
				for (String clip : this.allClips) {
					lore.add(ChatColor.GREEN + " - " + ChatColor.WHITE + clip);
				}
			}
			break;
		case INVENTORY:
			lore.add(ChatColor.GREEN + "Gun auto-loads using: ");
			for (String bull : this.allBullets) {
				lore.add(ChatColor.GREEN + " - " + ChatColor.GRAY + bull);
			}
			break;
		}
		 
		int health = (Integer) gunData.get("health");
		//int maxHealth = this.maxUses;
		 
		StringBuffer display = new StringBuffer();
		 
		if (health <= 0) { // dead
			display.append(ChatColor.DARK_RED).append("Gun is too damaged to use");
		} else if (health <= middleRisk) { // misfire likely
			display.append(ChatColor.RED).append("Gun is badly damaged");
		} else if (health <= (middleRisk * (riskSpread / 2) )) { // probably about to experience increased risk of misfire
			display.append(ChatColor.LIGHT_PURPLE).append("Gun has signs of wear");
		} else {
			display.append(ChatColor.GREEN).append("Gun is in good repair");
		}
		lore.add(display.toString());
		
		meta.setLore(lore);
		gun.setItemMeta(meta);
		return gun;
	}
	
	/**
	 * Check if this living entity has a gun of this type already in possession
	 * @param entity the entity to check
	 * @return true if already in inventory, false otherwise.
	 */
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

	/**
	 * Given an entity and a message, sends them said message if not previously received.
	 * 
	 * @param entity the entity to alert
	 * @param string the message to send.
	 */
	public void optionallyWarn(LivingEntity entity, String string) {
		UUID person = entity.getUniqueId();
		this.warned.compute(person, (u, s) -> {
			if (s == null) {
				s = Sets.newConcurrentHashSet();
			}
			if (!s.contains(string)) {
				entity.sendMessage(string);
				s.add(string);
			}
		
			return s;
		} );
	}

	/**
	 * Returns a clone of the basic gun.
	 * 
	 * @return the basic gun.
	 */
	@Override
	public ItemStack getMinimalGun() {
		Map<String, Object> gunData = new HashMap<String, Object>();
		
		gunData.put("unid", UUID.randomUUID().toString());
		
		return updateGunData(this.gunExample.clone(), gunData);
	}

	/**
	 * Simple repair method, just sets health back to maximum.
	 * 
	 * @return the gun, repaired.
	 */
	@Override
	public ItemStack repairGun(ItemStack toRepair) {
		if (isGun(toRepair)) {
			Map<String, Object> gunUpdate = new HashMap<String, Object>();
			gunUpdate.put("health", Integer.valueOf(this.maxUses));
			return updateGunLore(updateGunData(toRepair, gunUpdate));
		}
		return toRepair;
	}

	/**
	 * Just checks that the gun isn't doing something funky like have too much health
	 * or too much ammo, or invalid ammo.
	 * 
	 * @return true if ok, false otherwise
	 */
	public boolean validGun(ItemStack gun) {
		if (gun == null) return false;
		if (!isGun(gun)) return false;
		
		Map<String, Object> data = getGunData(gun);
		if (data == null || data.isEmpty()) return false;
		
		Object health = data.get("health");
		if (health != null && health instanceof Integer) {
			if ((((Integer) health) < 0) || ((Integer) health) > this.maxUses) return false;
		}

		AmmoType type = (AmmoType) data.get("type");
		if (type == null) {
			return false;
		}
		Bullet bullet = null;
		switch(type) {
		case BULLET:
			if (!data.containsKey("ammo")) {
				if (data.containsKey("rounds") && ((Integer) data.get("rounds") > 0)) return false; // no bullet but has ammo?
			} else {
				bullet = AddGun.getPlugin().getAmmo().getBullet((String) data.get("ammo"));
	
				if (bullet == null) return false;
				
				if (!data.containsKey("rounds") || (Integer) data.get("rounds") > this.maxAmmo || (Integer) data.get("rounds") < 0) return false;
			}
			break;
		case CLIP:
			if (!data.containsKey("clip")) {
				if (data.containsKey("rounds") && ((Integer) data.get("rounds") > 0)) return false; // no clip but has ammo?
			} else {
				Clip clip = AddGun.getPlugin().getAmmo().getClip((String) data.get("clip"));
			
				if (data.containsKey("ammo")) { 
					bullet = AddGun.getPlugin().getAmmo().getBullet((String) data.get("ammo"));
					
					if (bullet == null) return false;
					
					if (!data.containsKey("rounds") || (Integer) data.get("rounds") > clip.maxRounds(bullet.getName()) || (Integer) data.get("rounds") < 0) return false;
				} else {
					if (!data.containsKey("rounds") || (Integer) data.get("rounds") > this.maxAmmo || (Integer) data.get("rounds") < 0) return false;
				}
			}
			break;
		case INVENTORY:
		}
		return true;
	}
}
