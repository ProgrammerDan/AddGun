package com.programmerdan.minecraft.addgun.ammo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.TippedArrow;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ArmorType;
import com.programmerdan.minecraft.addgun.guns.HitPart;

/**
 * Represents a single bullet type that can match against standard ammo, be aggregated into clips or directly loaded into a gun.
 * 
 * @author ProgrammerDan
 *
 */
public class Bullet implements Comparable<Bullet>, Serializable {
	// TODO: modifiers to gun properties.
	
	/**
	 * A serialID for Bullets
	 */
	private static final long serialVersionUID = -2487869326126717625L;

	/**
	 * The name of this bullet, used in various settings.
	 */
	private String name;
	
	/**
	 * The item tag of this bullet, used when as an inventory item (name is used when "loaded" into a gun, tag when in inventory)
	 */
	private String tag;
	
	/**
	 * The entity nature of this bullet.
	 */
	private EntityType bulletType;
	
	/**
	 * The ItemStack that represents this bullet.
	 */
	private ItemStack example;
	
	/**
	 * Misfire explosion chance modifier (additive) (percentage)
	 */
	private double misfireBlowoutChance = 0.00;
	
	/**
	 * If the bullet requires XP (energy) to fire... default no
	 */
	private int xpDraw = 0;
	
	/**
	 * When firing the gun and still / sneak isn't maximized, potentially angular jitter modified (additive)
	 */
	private double maxMissRadius = 0;
	/**
	 * Some guns are just innacurate .. minimum angular jitter regardless of stillness / sneak (additive)
	 */
	private double minMissRadius = 0;

	/**
	 * Some bullets are clouds of smaller bullets, like buckshot or birdshot; use a higher scatter to have a bunch
	 * of smaller pullets in a spread. Avg is the midpoint used; spread is the +- on the avg, and radius is degrees
	 * around line of fire that scatter could impact. 
	 */
	private int avgScatter = 0;
	private int spreadScatter = 0;
	private double scatterRadius = 0;
	
	/**
	 * Damage to use if hitpart has no match
	 */
	private double baseAvgHitDamage = 0.0d;
	
	/**
	 * Spread to use if hitpart has no match
	 */
	private double baseSpreadHitDamage = 0.0d;
	
	
	/**
	 * Typical damage modifier per hit
	 */
	private Map<HitPart, Double> avgHitDamage = new ConcurrentHashMap<HitPart, Double>();
	//20.0d;
	/**
	 * Damage variation
	 */
	private Map<HitPart, Double> spreadHitDamage = new ConcurrentHashMap<HitPart, Double>();
	//private double spreadHitDamage = 5.0d;
	/**
	 * Explosion?
	 */
	private float explosionLevel = 0;
	/**
	 * Chance of an explosion on impact.
	 */
	private double explosionChance = 0.0d;
	
	public float getExplosionLevel() {
		return this.explosionLevel;
	}
	
	public double getExplosionChance() {
		return this.explosionChance;
	}
	/**
	 * Incendiary?
	 */
	private int fireTicks = 0;
	/**
	 * Chance of starting a fire on impact.
	 */
	private double fireChance = 0.0d;
	
	public int getFireTicks() {
		return this.fireTicks;
	}
	
	public double getFireChance() {
		return this.fireChance;
	}
	
	/**
	 * Knockback on hit
	 */
	private int knockback = 0;
	/**
	 * Chance of knockback.
	 */
	private double knockbackChance = 0.0d;

	/**
	 * The point at which, after so many seconds of "stillness" -- head motion only -- that any aim impacts are halved.
	 * Additive to gun base. Some bullets are harder to fire.
	 */
	private double stillInflection = 0.0d;
	/**
	 * The point at which, after so many seconds of sneaking, any aim impacts are halved.
	 */
	private double sneakInflection = 0.0d;
	/**
	 * A relative "spread" of speed of motion over inflection, values close to 0 lead to a sharp inflection, values larger make a smoother
	 * transition. This is for stillness. Adds to gun spread, so keep this reasonable.
	 */
	private double stillSpread = 0.0d;
	/**
	 * A relative "spread" of speed of motion over inflection, values close to 0 lead to a sharp inflection, values larger make a smoother
	 * transition. This is for sneaking.
	 */
	private double sneakSpread = 0.0d;

	
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
	
	// bypass stuff is TODO
	
	/**
	 * Base armor reduction % across all types
	 */
	private double baseArmorReduction = 0.0d;
	
	/**
	 * Configure armor damage reduction % for each type.
	 * Note this is used to compute increasing reduction via enchantments
	 * Note also that unlike normal damage, bullet damage is against a single piece of armor.
	 */
	private Map<ArmorType, Double> armorReduction = new ConcurrentHashMap<ArmorType, Double>();
	
	/**
	 * Base armor damage % across all types
	 */
	private double baseArmorDamage = 0.0d;
	
	/**
	 * Configure armor damage for each type.
	 * Based on the type of armor, indicates what % of damage is passed on to the armor.
	 * Some enchantments may reduce this in the final accounting.
	 * But generally, a good metric to follow is the more damage reduction it offers, the
	 * more damage it takes.
	 */
	private Map<ArmorType, Double> armorDamage = new ConcurrentHashMap<ArmorType, Double>();


	public Bullet(ConfigurationSection config) {
		this.name = config.getName();
		
		this.tag = ChatColor.BLACK + "Bullet: "
				+ Integer.toHexString(this.getName().hashCode() + this.getName().length());

		try {
			this.bulletType = EntityType.valueOf(config.getString("type", EntityType.SMALL_FIREBALL.toString()));
		} catch (IllegalArgumentException | NullPointerException e) {
			this.bulletType = EntityType.SMALL_FIREBALL;
		}
		
		this.example = config.getItemStack("example");
		if (this.example != null) {
			 ItemMeta meta = this.example.getItemMeta();
			 List<String> lore = meta.getLore();
			 if (lore == null) {
				 lore = new ArrayList<String>();
			 }
			 lore.add(this.tag);
			 meta.setLore(lore);
			 this.example.setItemMeta(meta);
		} else {
			throw new IllegalArgumentException("No inventory representation (section example) provided for this bullet, it cannot be instanced");
		}
		
		this.misfireBlowoutChance = config.getDouble("misfireBlowoutChance", misfireBlowoutChance);
		this.xpDraw = config.getInt("xpPerShot", xpDraw);
		this.maxMissRadius = config.getDouble("missRadius.max", maxMissRadius);
		this.minMissRadius = config.getDouble("missRadius.min", minMissRadius);
		
		this.sneakInflection = config.getDouble("sneak.inflection", sneakInflection);
		this.sneakSpread = config.getDouble("sneak.spread", sneakSpread);
		this.stillInflection = config.getDouble("still.inflection", stillInflection);
		this.stillSpread = config.getDouble("still.spread", stillSpread);
		
		this.avgScatter = config.getInt("scatter.avg", avgScatter);
		this.spreadScatter = config.getInt("scatter.spread", spreadScatter);
		this.scatterRadius = config.getDouble("scatter.radius", scatterRadius);
		
		this.explosionChance = config.getDouble("explosion.chance", explosionChance);
		this.explosionLevel = (float) config.getDouble("explosion.level", explosionLevel);
		
		this.fireChance = config.getDouble("incendiary.chance", fireChance);
		this.fireTicks = config.getInt("incendiary.ticks", fireTicks);
		
		this.knockbackChance = config.getDouble("knockback.chance", knockbackChance);
		this.knockback = config.getInt("knockback.level", knockback);
		
		// TODO: damage bypass.
		this.baseArmorReduction = config.getDouble("reduction.base", baseArmorReduction);
		for (ArmorType armor : ArmorType.values()) {
			if (config.contains("reduction." + armor.toString())) {
				double armorReduce = config.getDouble("reduction." + armor.toString());
				this.armorReduction.put(armor, armorReduce);
			}
		}
		
		this.baseArmorDamage = config.getDouble("durability.base", baseArmorDamage);
		for (ArmorType armor : ArmorType.values()) {
			if (config.contains("durability." + armor.toString())) {
				double armorDamage = config.getDouble("durability." + armor.toString());
				this.armorDamage.put(armor,  armorDamage);
			}
		}
		
		this.baseAvgHitDamage = config.getDouble("damage.avg", baseAvgHitDamage);
		this.baseSpreadHitDamage = config.getDouble("damage.spread", baseSpreadHitDamage);
		
		for (HitPart hit : HitPart.values()) {
			ConfigurationSection hitConfig = config.getConfigurationSection("damage." + hit.toString());
			if (hitConfig == null) continue;
			double avgHitDamage = hitConfig.getDouble("avg");
			double spreadHitDamage = hitConfig.getDouble("spread");
			
			this.avgHitDamage.put(hit, avgHitDamage);
			this.spreadHitDamage.put(hit, spreadHitDamage);
		}
	}
	
	/**
	 * Gets the avg hit damage for this projectile
	 * @param hit the part hit
	 * @return the base + specific damage for part hit
	 */
	public double getAvgHitDamage(HitPart hit) {
		return this.baseAvgHitDamage + this.avgHitDamage.getOrDefault(hit, 0.0d);
	}
	
	/**
	 * Gets the "spread" -- typically will be used as a 2*sigma on a normal distribution.
	 * 
	 * @param hit the part hit
	 * @return the base + specific spread for the part hit. Note specific spreads can be negative, to indicate a "tighter" spread, just that combined they should not be less then 0.
	 */
	public double getSpreadHitDamage(HitPart hit) {
		return this.baseSpreadHitDamage + this.spreadHitDamage.getOrDefault(hit,  0.0d);
	}
	
	/**
	 * Gets the % reduction of damage that armor confers as its base impact.
	 * 
	 * @param armor the armor type being tested
	 * @return the % reduction (0 to 1)
	 */
	public double getArmorReduction(ArmorType armor) {
		return this.armorReduction.getOrDefault(armor, this.baseArmorReduction);
	}
	
	/**
	 * Gets the % of damage to apply to the armor.
	 * 
	 * @param armor the armor type absorbing damage
	 * @return the % damage conferred to the armor (0 to 1)
	 */
	public double getArmorDamage(ArmorType armor) {
		return this.armorReduction.getOrDefault(armor, this.baseArmorDamage);
	}

	/**
	 * Gets the configured unique name for this Bullet instance.
	 * 
	 * @return the name
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * Returns the inventory representation of this bullet.
	 * 
	 * @return a Clone of the backing inventory item object.
	 */
	public ItemStack getBulletItem() {
		return this.example.clone();
	}
	
	/**
	 * Returns the material type of the inventory representation of this bullet.
	 * 
	 * @return the Material enum type
	 */
	public Material getMaterialType() {
		return this.example.getType();
	}
	
	/**
	 * The hidden bullet tag that identifies it uniquely when in inventories
	 * @return the string tag
	 */
	public String getTag() {
		return this.tag;
	}
	
	/**
	 * Returns the configured EntityType for this Bullet instance, corrects it if necessary.
	 * 
	 * @return the entity type
	 */
	public EntityType getEntityType() {
		if (!this.bulletType.isSpawnable()) {
			AddGun.getPlugin().debug("Bullet {0} was configured to use {1}, but that is invalid, using SmallFireball instead.", this.name, this.bulletType);
			this.bulletType = EntityType.SMALL_FIREBALL;
		}
		return this.bulletType;
	}

	/**
	 * Returns if this bullet requires XP to launch.
	 * 
	 * @return true if it does, false otherwise.
	 */
	public boolean getUsesXP() {
		return this.xpDraw <= 0;
	}
	
	/**
	 * Returns the amount of XP used by this bullet.
	 * 
	 * @return XP used
	 */
	public int getXPDraw() {
		return (this.xpDraw <= 0 ? 0 : this.xpDraw);
	}
	
	/**
	 * The degrees to augment the gun's min miss radius with.
	 * @return miss radius modifier
	 */
	public double getMinMissRadius() {
		return this.minMissRadius;
	}
	
	/**
	 * The degrees to augment the gun's max miss radius with.
	 * 
	 * @return miss radius modifier
	 */
	public double getMaxMissRadius() {
		return this.maxMissRadius;
	}

	
	/**
	 * Returns the Class type that corresponds to the EntityType, or corrects if necessary.
	 * 
	 * @return the Class that is a Projectile
	 */
	public Class<? extends Projectile> getProjectileType() {
		switch (this.bulletType) {
		case ARROW:
			return Arrow.class;
		case DRAGON_FIREBALL:
			return DragonFireball.class;
		case EGG:
			return Egg.class;
		case ENDER_PEARL:
			return EnderPearl.class;
		case FIREBALL:
			return Fireball.class;
		case FISHING_HOOK:
			return FishHook.class;
		case LINGERING_POTION:
			return LingeringPotion.class;
		case LLAMA_SPIT:
			return LlamaSpit.class;
		case SHULKER_BULLET:
			return ShulkerBullet.class;
		case SMALL_FIREBALL:
			return SmallFireball.class;
		case SNOWBALL:
			return Snowball.class;
		case SPECTRAL_ARROW:
			return SpectralArrow.class;
		case SPLASH_POTION:
			return SplashPotion.class;
		case THROWN_EXP_BOTTLE:
			return ThrownExpBottle.class;
		case TIPPED_ARROW:
			return TippedArrow.class;
		case WITHER_SKULL:
			return WitherSkull.class;
		case EXPERIENCE_ORB:
		case FIREWORK:
		case PRIMED_TNT:
		case LIGHTNING:
		case AREA_EFFECT_CLOUD:
		case ARMOR_STAND:
		case BAT:
		case BLAZE:
		case BOAT:
		case CAVE_SPIDER:
		case CHICKEN:
		case COMPLEX_PART:
		case COW:
		case CREEPER:
		case DONKEY:
		case DROPPED_ITEM:
		case ELDER_GUARDIAN:
		case ENDERMAN:
		case ENDERMITE:
		case ENDER_CRYSTAL:
		case ENDER_DRAGON:
		case ENDER_SIGNAL:
		case EVOKER:
		case EVOKER_FANGS:
		case FALLING_BLOCK:
		case GHAST:
		case GIANT:
		case GUARDIAN:
		case HORSE:
		case HUSK:
		case ILLUSIONER:
		case IRON_GOLEM:
		case ITEM_FRAME:
		case LEASH_HITCH:
		case LLAMA:
		case MAGMA_CUBE:
		case MINECART:
		case MINECART_CHEST:
		case MINECART_COMMAND:
		case MINECART_FURNACE:
		case MINECART_HOPPER:
		case MINECART_MOB_SPAWNER:
		case MINECART_TNT:
		case MULE:
		case MUSHROOM_COW:
		case OCELOT:
		case PAINTING:
		case PARROT:
		case PIG:
		case PIG_ZOMBIE:
		case PLAYER:
		case POLAR_BEAR:
		case RABBIT:
		case SHEEP:
		case SHULKER:
		case SILVERFISH:
		case SKELETON:
		case SKELETON_HORSE:
		case SLIME:
		case SNOWMAN:
		case SPIDER:
		case SQUID:
		case STRAY:
		case UNKNOWN:
		case VEX:
		case VILLAGER:
		case VINDICATOR:
		case WEATHER:
		case WITCH:
		case WITHER:
		case WITHER_SKELETON:
		case WOLF:
		case ZOMBIE:
		case ZOMBIE_HORSE:
		case ZOMBIE_VILLAGER:
		default:
			AddGun.getPlugin().debug("Bullet {0} was configured to use {1}, but that is invalid, using SmallFireball instead.", this.name, this.bulletType);
			this.bulletType = EntityType.SMALL_FIREBALL;
			return SmallFireball.class;
		}
	}

	/**
	 * Based on the parameters in this bullet, configure some stuff.
	 * 
	 * @param bullet the newly minted projectile 
	 * @param world the world it inhabits
	 * @param shooter who shot it
	 * @param velocity The gun's intrinsic velocity. Might be ignored. 
	 */
	public void configureBullet(Projectile bullet, World world, ProjectileSource shooter, Vector velocity) {
		
		bullet.setVelocity(velocity);
		bullet.setFireTicks(this.fireTicks);
	}
	
	/* Standard Overrides */
	/* TODO: Serialization ones, add bukkit serialization? */
	
	@Override
	public boolean equals(Object e) {
		if (e instanceof Bullet) {
			return this.getName().equals(((Bullet) e).getName());
		}
		
		return false;
	}
	
	@Override
	public int compareTo(Bullet b) {
		return this.getName().compareTo(b.getName());
	}
	
	@Override
	public int hashCode() {
		return this.getName().hashCode();
	}

	/**
	 * Lightweight check to see if the given itemstack is a bullet
	 * @param toCheck
	 * @return
	 */
	public boolean isBullet(ItemStack toCheck) {
		if (toCheck == null)
			return false;

		if (!example.getType().equals(toCheck.getType()))
			return false;

		if (!toCheck.hasItemMeta())
			return false;

		ItemMeta meta = toCheck.getItemMeta();

		if (meta.hasLore() && meta.getLore().contains(tag))
			return true;

		return false;

	}

}
