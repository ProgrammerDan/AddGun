package com.programmerdan.minecraft.addgun;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.reflect.ClassPath;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Bullets;
import com.programmerdan.minecraft.addgun.ammo.Magazine;
import com.programmerdan.minecraft.addgun.commands.CommandHandler;
import com.programmerdan.minecraft.addgun.guns.BasicGun;
import com.programmerdan.minecraft.addgun.guns.Guns;
import com.programmerdan.minecraft.addgun.guns.StandardGun;
import com.programmerdan.minecraft.addgun.listeners.CompatListener;
import com.programmerdan.minecraft.addgun.listeners.PlayerListener;

public class AddGun  extends JavaPlugin {
	private static AddGun instance;
	private CommandHandler commandHandler;
	private PlayerListener playerListener;
	private TagUtility tagUtility;

	private Map<String, BasicGun> customGuns;
	
	private Guns guns;
	
	private Bullets ammo;
	
	private int xpPerBottle;
	
	private int kickbackExpand;

	private double accuracySneaking;
	private double accuracyStill;
	private double walkingBase;
	private double walkingReduce;
	private double walkingIncrease;
	private double runningBase;
	private double runningReduce;
	private double runningIncrease;
	private double glidingBase;
	private double glidingReduce;
	private double glidingIncrease;
	private double crouchReduce;
	
	
	private ConcurrentHashMap<Material, Long> breakables;
	
	@Override
	public void onEnable() {
		super.onEnable();

		saveDefaultConfig();
		reloadConfig();
		
		AddGun.instance = this;

		config(getConfig());
		addBullets(getConfig());
		addGuns(getConfig());

		registerPlayerListener();
		registerCommandHandler();
		registerCompatListener();
		
		createTagUtility();
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		if (this.playerListener != null) this.playerListener.shutdown();
	}
	
	public int getXpPerBottle() {
		return xpPerBottle;
	}
	
	public int getKickbackExpand() {
		return kickbackExpand;
	}
	
	public double getAccuracySneaking() {
		return this.accuracySneaking;
	}
	
	public double getAccuracyStill() {
		return this.accuracyStill;
	}
	
	public double getCrouchReduce() {
		return this.crouchReduce;
	}
	
	public double getWalkingBase() {
		return this.walkingBase;
	}
	public double getWalkingReduce() {
		return this.walkingReduce;
	}
	public double getWalkingIncrease() {
		return this.walkingIncrease;
	}
	
	public double getRunningBase() {
		return this.runningBase;
	}
	public double getRunningReduce() {
		return this.runningReduce;
	}
	public double getRunningIncrease() {
		return this.runningIncrease;
	}
	
	public double getGlidingBase() {
		return this.glidingBase;
	}
	public double getGlidingReduce() {
		return this.glidingReduce;
	}
	public double getGlidingIncrease() {
		return this.glidingIncrease;
	}
	
	public Long getBreakableCooldown(Material type) {
		return breakables.get(type);
	}
	
	public PlayerListener getPlayerListener() {
		return this.playerListener;
	}
	
	public CommandHandler getCommandHandler() {
		return this.commandHandler;
	}
	
	public Guns getGuns() {
		return guns;
	}
	
	public Set<String> getGunNames() {
		Set<String> tGuns = new HashSet<String>();
		tGuns.addAll(guns.getGunNames());
		if (!this.customGuns.isEmpty()) {
			tGuns.addAll(this.customGuns.keySet());
		}
		return tGuns;
	}
	
	public BasicGun getGun(String name) {
		BasicGun gun = guns.getGun(name);
		if (gun == null) {
			return this.customGuns.get(name);
		}
		return gun;
	}
	
	public Bullets getAmmo() {
		return ammo;
	}
	
	
	private void registerCommandHandler() {
		if (!this.isEnabled()) return;
		try {
			this.commandHandler = new CommandHandler(getConfig());
		} catch (Exception e) {
			this.severe("Failed to set up command handling", e);
			this.setEnabled(false);
		}
	}

	private void registerPlayerListener() {
		if (!this.isEnabled()) return;
		try {
			this.playerListener = new PlayerListener(getConfig());
		} catch (Exception e) {
			this.severe("Failed to set up player event capture / handling", e);
			this.setEnabled(false);
		}	
	}
	
	private void registerCompatListener() {
		if (!this.isEnabled()) return;
		try {
			if (this.getServer().getPluginManager().isPluginEnabled("DevotedPvP")) {
				this.getServer().getPluginManager().registerEvents( new CompatListener(), this);
			}
		} catch (Exception e) {
			this.warning("Unable to start Compat listener, DevotedPvP not installed.");
		}
	}
	
	private void createTagUtility() {
		if (!this.isEnabled()) return;
		try {
			if (this.getServer().getPluginManager().isPluginEnabled("CombatTagPlus")) {
				this.tagUtility = new CTPTagUtility();
			} else {
				this.tagUtility = new NullTagUtility();
			}
		} catch (Exception e) {
			this.warning("Unable to start Compat listener, DevotedPvP not installed.");
		}
	}
	
	public TagUtility getTagUtility() {
		return this.tagUtility;
	}

	private void config(FileConfiguration config) {
		ConfigurationSection global = config.getConfigurationSection("global");
		this.breakables = new ConcurrentHashMap<Material, Long>();
		if (global != null) {
			this.xpPerBottle = global.getInt("xpPerBottle", 10);
			this.kickbackExpand = global.getInt("kickback.expand", 4);

			if (global.isConfigurationSection("breakables")) {
				ConfigurationSection breaks = global.getConfigurationSection("breakables");
				for (String key : breaks.getKeys(false)) {
					try {
						Material mkey = Material.valueOf(key);
						
						this.breakables.put(mkey, breaks.getLong(key));
						warning("Adding breakable {0} with cooldown of {1}", key, breaks.getLong(key));
					} catch (Exception e) {
						warning("Skipping breakable {0} due to no matching Material found.", key);
					}
				}
			}
			this.accuracySneaking = global.getDouble("accuracy.sneaking", 0.025d);
			this.accuracyStill = global.getDouble("accuracy.still", 0.05d);
			
			this.walkingBase = global.getDouble("accuracy.walking.base", 0.1d);
			this.runningBase = global.getDouble("accuracy.running.base", 0.4d);
			this.glidingBase = global.getDouble("accuracy.gliding.base", 0.7d);
			
			this.walkingReduce = global.getDouble("accuracy.walking.reduce", 0.00005d);
			this.walkingIncrease = global.getDouble("accuracy.walking.increase", 0.0001d);
			
			this.runningReduce = global.getDouble("accuracy.running.reduce", 0.0001d);
			this.runningIncrease = global.getDouble("accuracy.running.increase", 0.001d);

			this.glidingReduce = global.getDouble("accuracy.gliding.reduce", 0.0001d);
			this.glidingIncrease = global.getDouble("accuracy.gliding.increase", 0.001d);

			this.crouchReduce = global.getDouble("accuracy.crouch-reduce", 0.5d);
		} else {
			this.xpPerBottle = 10;
			this.kickbackExpand = 4;
			this.accuracySneaking = 0.025d;
			this.accuracyStill = 0.05d;
			this.walkingBase = 0.1d;
			this.walkingReduce = 0.00005d;
			this.walkingIncrease = 0.0001d;
			this.runningBase = 0.4d;
			this.runningReduce = 0.0001d;
			this.runningIncrease = 0.001d;
			this.glidingBase = 0.7d;
			this.glidingReduce = 0.0001d;
			this.glidingIncrease = 0.001d;
			this.crouchReduce = 0.5d;
		}

	}
	
	private void addBullets(FileConfiguration config) {
		
		ConfigurationSection bullets = config.getConfigurationSection("bullets");
		if (bullets == null || bullets.getKeys(false) == null) {
			this.warning("No bullets enabled!");
			return;
		}
		
		this.ammo = new Bullets();
		
		for (String bulletName : bullets.getKeys(false)) {
			try {
				Bullet bullet = new Bullet(bullets.getConfigurationSection(bulletName));
				
				this.ammo.registerBullet(bullet);
			} catch (Exception e) {
				warning("Failed to register bullet {0} due to error {1}", bulletName, e);
			}
		}
		
		ConfigurationSection magazines = config.getConfigurationSection("magazines");
		if (magazines == null || magazines.getKeys(false) == null) {
			this.warning("No magazines enabled.");
		}

		for (String magName : magazines.getKeys(false)) {
			try {
				Magazine magazine = new Magazine(magazines.getConfigurationSection(magName));
				
				this.ammo.registerMagazine(magazine);
			} catch (Exception e) {
				warning("Failed to register magazine {0} due to error {1}", magName, e);
			}
		}

		if (this.ammo.hasMagazines()) {
			this.getServer().getPluginManager().registerEvents(this.ammo, this);
		}
	}
	
	private void addGuns(FileConfiguration config) {
		
		ConfigurationSection guns = config.getConfigurationSection("guns");
		if (guns == null || guns.getKeys(false) == null) {
			this.warning("No guns enabled!");
			return;
		}
		
		this.guns = new Guns();
		this.customGuns = new ConcurrentHashMap<>();
		
		// load all possible guns
		Map<String, BasicGun> possibleGuns = new HashMap<String, BasicGun>();
		
		try {
			ClassPath getSamplersPath = ClassPath.from(this.getClassLoader());

			for (ClassPath.ClassInfo clsInfo : getSamplersPath.getTopLevelClasses("com.programmerdan.minecraft.addgun.guns.impl")) {
				Class<?> clazz = clsInfo.load();
				info("Found gun {0}, attempting to find a suitable constructor", clazz.getName());
				if (clazz != null && BasicGun.class.isAssignableFrom(clazz)) {
					BasicGun basicGun = null;
					try {
						Constructor<?> constructBasic = clazz.getConstructor();
						basicGun = (BasicGun) constructBasic.newInstance();
						possibleGuns.put(basicGun.getName(), basicGun);
						info("Created a new Gun Manager for custom gun of type {0}", clazz.getName());
					} catch (Exception e) {}
				}
			}
		} catch (IOException ioe) {
			warning("Failed to load any guns, due to IO error", ioe);
		}
		
		// configure guns desired (which enables them)
		for (String gun : guns.getKeys(false)) {
			try {
				BasicGun possibleGun = possibleGuns.get(gun);
				if (possibleGun == null) {
					StandardGun newGun = new StandardGun(gun);
					newGun.configure(guns.getConfigurationSection(gun));
					this.guns.registerGun(newGun);
					
					info("Configured standard gun {0} for use", gun);
				} else {
					possibleGun.configure(guns.getConfigurationSection(gun));
					
					this.getServer().getPluginManager().registerEvents((Listener) possibleGun, this);
					
					this.customGuns.put(possibleGun.getName(), possibleGun);
					info("Configured fancy gun {0} for use", gun);
				}
			} catch (Exception e) {
				warning("Gun {0} failed during setup", gun);
				warning("Exception trapped: ", e);
			}
		}
		
		/*
		 * Register general gun handler
		 */
		if (this.guns.hasGuns()) {
			this.getServer().getPluginManager().registerEvents(this.guns, this);
			info("Registered Gun Manager's events");
		}
	}

	/**
	 * 
	 * @return the static global instance. Not my fav pattern, but whatever.
	 */
	public static AddGun getPlugin() {
		return AddGun.instance;
	}

	/**
	 * Simple SEVERE level logging.
	 */
	public void severe(String message) {
		getLogger().log(Level.SEVERE, message);
	}

	/**
	 * Simple SEVERE level logging with Throwable record.
	 */
	public void severe(String message, Throwable error) {
		getLogger().log(Level.SEVERE, message, error);
	}

	/**
	 * Simple WARNING level logging.
	 */
	public void warning(String message) {
		getLogger().log(Level.WARNING, message);
	}

	/**
	 * Simple WARNING level logging with Throwable record.
	 */
	public void warning(String message, Throwable error) {
		getLogger().log(Level.WARNING, message, error);
	}

	/**
	 * Simple WARNING level logging with ellipsis notation shortcut for deferred injection argument array.
	 */
	public void warning(String message, Object... vars) {
		getLogger().log(Level.WARNING, message, vars);
	}

	/**
	 * Simple INFO level logging
	 */
	public void info(String message) {
		getLogger().log(Level.INFO, message);
	}

	/**
	 * Simple INFO level logging with ellipsis notation shortcut for deferred injection argument array.
	 */
	public void info(String message, Object... vars) {
		getLogger().log(Level.INFO, message, vars);
	}
	
	/**
	 * Toggle debug live
	 * 
	 * @param state true for on, false for off.
	 */
	public void setDebug(boolean state) {
		if (state) {
			getConfig().set("debug", true);
		} else {
			getConfig().set("debug", false);
		}
	}

	/**
	 * Live on/off debug message at INFO level.
	 *
	 * Skipped if `debug` in root config is false.
	 */
	public void debug(String message) {
		if (getConfig() != null && getConfig().getBoolean("debug", false)) {
			getLogger().log(Level.INFO, message);
		}
	}

	/**
	 * Live on/off debug message  at INFO level with ellipsis notation
	 * shortcut for deferred injection argument array.
	 *
	 * Skipped if `debug` in root config is false.
	 */
	public void debug(String message, Object... vars) {
		if (getConfig() != null && getConfig().getBoolean("debug", false)) {
			getLogger().log(Level.INFO, message, vars);
		}
	}

}
