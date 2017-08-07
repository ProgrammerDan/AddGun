package com.programmerdan.minecraft.addgun.ammo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.programmerdan.minecraft.addgun.guns.HitPart;

/**
 * Represents a single bullet type that can match against standard ammo, be aggregated into clips or directly loaded into a gun.
 * 
 * @author ProgrammerDan
 *
 */
public class Bullet {
	// TODO: modifiers to gun properties.
	
	private String name;
	
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
	private int explosionLevel = 0;
	/**
	 * Chance of an explosion on impact.
	 */
	private double explosionChance = 0.0d;
	/**
	 * Incendiary?
	 */
	private int fireTicks = 0;
	/**
	 * Chance of starting a fire on impact.
	 */
	private double fireChance = 0.0d;
	/**
	 * Knockback on hit
	 */
	private int knockback = 0;
	/**
	 * Chance of knockback.
	 */
	private double knockbackChance = 0.0d;

	// bypass stuff is TODO
	
	/**
	 * % to which this bullet bypasses armor reduction, if defense points are at or below defensePointsBypassBegins
	 */
	private double armorBypass = 0.1d;
	/**
	 * % of bypass occurring, assuming defensePointsBypassBegins is satisfied.
	 */
	private double armorBypassChance = 0.5d;
	/**
	 * Based on the "defense points" calculus, determines the armor point at which bypass can occur. This is _on a per piece_ basis. See also
	 * how it's modified by the hitlocation modifiers  
	 */
	private double defensePointsBypassBegins = 5.0; 

	public Bullet(ConfigurationSection config) {
		this.name = config.getName();
		
		this.misfireBlowoutChance = config.getDouble("misfireBlowoutChance", misfireBlowoutChance);
		this.xpDraw = config.getInt("xpPerShot", xpDraw);
		this.maxMissRadius = config.getDouble("missRadius.max", maxMissRadius);
		this.minMissRadius = config.getDouble("missRadius.min", minMissRadius);
		this.avgScatter = config.getInt("scatter.avg", avgScatter);
		this.spreadScatter = config.getInt("scatter.spread", spreadScatter);
		this.scatterRadius = config.getDouble("scatter.radius", scatterRadius);
		
		this.explosionChance = config.getDouble("explosion.chance", explosionChance);
		this.explosionLevel = config.getInt("explosion.level", explosionLevel);
		
		this.fireChance = config.getDouble("incendiary.chance", fireChance);
		this.fireTicks = config.getInt("incendiary.ticks", fireTicks);	
	}
	
}
