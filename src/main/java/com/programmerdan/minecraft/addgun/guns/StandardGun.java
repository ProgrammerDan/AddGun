package com.programmerdan.minecraft.addgun.guns;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import com.google.common.collect.Sets;

public abstract class StandardGun implements Listener {

	/**
	 * This can be used to track any warnings sent to players and not resend
	 */
	private Map<UUID, String> warned = new ConcurrentHashMap<>();

	/**
	 * this keeps track of travel paths for bullets (TODO: refactor)
	 */
	private Map<UUID, Location> travelPaths = new ConcurrentHashMap<>();
	
	/**
	 * Is this gun enabled?
	 */
	private boolean enabled = false;

	/**
	 * Simplest example of the gun -- this is used for "matching" 
	 */
	private ItemStack gunExample = null;
	
	/**
	 * All the valid bullets that can be used with this gun, if it is directly loaded with bullets.
	 */
	private List<String> allBullets = null;
	
	/**
	 * All the valid clips that can be used with this gun, if it is loaded via clips.
	 */
	private List<String> allClips = null;
	
	/**
	 * max V in meters (blocks) / s -- this is gun's intrinsic, could be modified by bullets.
	 */
	private double maxSpeed = 200.0;
	
	/**
	 * min V in meters (blocks) / s
	 */
	private double minSpeed = 180.0;
	
	/**
	 * Max times the gun can be fired before repair.
	 */
	private int maxUses = 500;
	
	/**
	 * Inflection point, when this many uses remain the risk of misfire is 50%
	 */
	private int middleRisk = 20;
	
	/**
	 * Misfire explosion change (percentage) -- could be modified by bullets
	 */
	private double misfireBlowoutChance = 0.05;
	
	/**
	 * Internal, computed. Will be gun item's max health / uses.
	 */
	private int damagePerUse = (int) (1562 / maxUses);
	
	/**
	 * If the gun requires XP (energy) to fire... default no
	 */
	private int xpDraw = 0;
	
	
	
	/**
	 * When firing the gun and still / sneak isn't maximized, potentially angular jitter
	 */
	private double maxMissRadius = 30;
	/**
	 * Some guns are just innacurate .. minimum angular jitter regardless of stillness / sneak
	 */
	private double minMissRadius = 0;
	/**
	 * Typical damage per hit
	 */
	private double avgHitDamage = 20.0d;
	/**
	 * Damage variation
	 */
	private double spreadHitDamage = 5.0d;
	/**
	 * or smack nerd over the head?
	 */
	private double bluntDamage = 3.0d;

	
	/**
	 * The unique identifier for this gun.
	 */
	private String name;
	
	/**
	 * Does this gun support clips of bullets to fire?
	 */
	private boolean usesClips = false;
	
	/**
	 * Does this gun use bullets to fire?
	 */
	private boolean usesBullets = true;
	
	/**
	 * Does this gun use XP to fire?
	 */
	private boolean usesXP = false;
	
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
	
	protected StandardGun(String name) {
		this.gunExample = this.generateGun();
		this.name = name;
	}
	
	public abstract ItemStack generateGun();

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

	public double getMisfireBlowoutChance() {
		return misfireBlowoutChance;
	}

	public void setMisfireBlowoutChance(double misfireBlowoutChance) {
		this.misfireBlowoutChance = misfireBlowoutChance;
	}

	public int getDamagePerUse() {
		return damagePerUse;
	}

	public void setDamagePerUse(int damagePerUse) {
		this.damagePerUse = damagePerUse;
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

	public double getAvgHitDamage() {
		return avgHitDamage;
	}

	public void setAvgHitDamage(double avgHitDamage) {
		this.avgHitDamage = avgHitDamage;
	}

	public double getSpreadHitDamage() {
		return spreadHitDamage;
	}

	public void setSpreadHitDamage(double spreadHitDamage) {
		this.spreadHitDamage = spreadHitDamage;
	}

	public double getBluntDamage() {
		return bluntDamage;
	}

	public void setBluntDamage(double bluntDamage) {
		this.bluntDamage = bluntDamage;
	}

	public boolean isUsesClips() {
		return usesClips;
	}

	public void setUsesClips(boolean usesClips) {
		this.usesClips = usesClips;
	}

	public boolean isUsesBullets() {
		return usesBullets;
	}

	public void setUsesBullets(boolean usesBullets) {
		this.usesBullets = usesBullets;
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

	public String getName() {
		return name;
	}
	
	
}
