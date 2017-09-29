package com.programmerdan.minecraft.addgun.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Magazine;
import com.programmerdan.minecraft.addgun.guns.BasicGun;

public class CommandHandler implements CommandExecutor, TabCompleter {
	private AddGun plugin;
	
	private PluginCommand giveGun;
	private PluginCommand giveBullet;
	private PluginCommand giveMag;
	private PluginCommand repairGun;
	
	private PluginCommand giveSelfGun;
	private boolean giveSelfGunEnabled = false;
	private int giveSelfGunLimit = 0;
	private Map<UUID, Integer> selfGunLimits = new ConcurrentHashMap<UUID, Integer>();
	private long giveSelfGunTimeout = 0;
	private boolean giveSelfGunReset = false;
	private Map<UUID, Long> selfGunTimeouts = new ConcurrentHashMap<UUID, Long>();
	
	private PluginCommand giveSelfBullet;
	private boolean giveSelfBulletEnabled = false;
	private int giveSelfBulletLimit = 0;
	private Map<UUID, Integer> selfBulletLimits = new ConcurrentHashMap<UUID, Integer>();
	private long giveSelfBulletTimeout = 0;
	private boolean giveSelfBulletReset = false;
	private Map<UUID, Long> selfBulletTimeouts = new ConcurrentHashMap<UUID, Long>();
	
	private PluginCommand giveSelfMag;
	private boolean giveSelfMagEnabled = false;
	private int giveSelfMagLimit = 0;
	private Map<UUID, Integer> selfMagLimits = new ConcurrentHashMap<UUID, Integer>();
	private long giveSelfMagTimeout = 0;
	private boolean giveSelfMagReset = false;
	private Map<UUID, Long> selfMagTimeouts = new ConcurrentHashMap<UUID, Long>();
	
	private PluginCommand repairSelfGun;
	private boolean repairSelfGunEnabled = false;
	private int repairSelfGunLimit = 0;
	private Map<UUID, Integer> selfRepairLimits = new ConcurrentHashMap<UUID, Integer>();
	private long repairSelfGunTimeout = 0;
	private boolean repairSelfGunReset = false;
	private Map<UUID, Long> selfRepairTimeouts = new ConcurrentHashMap<UUID, Long>();
	
	public CommandHandler(FileConfiguration config) {
		plugin = AddGun.getPlugin();
		
		giveGun = plugin.getCommand("givegun");
		giveGun.setExecutor(this);
		giveGun.setTabCompleter(this);

		giveBullet = plugin.getCommand("givebullet");
		giveBullet.setExecutor(this);
		giveBullet.setTabCompleter(this);
		
		giveMag = plugin.getCommand("givemagazine");
		giveMag.setExecutor(this);
		giveMag.setTabCompleter(this);
		
		repairGun = plugin.getCommand("repairgun");
		repairGun.setExecutor(this);
		repairGun.setTabCompleter(this);
		
		giveSelfGun = plugin.getCommand("giveselfgun");
		giveSelfGun.setExecutor(this);
		giveSelfGun.setTabCompleter(this);

		giveSelfBullet = plugin.getCommand("giveselfbullet");
		giveSelfBullet.setExecutor(this);
		giveSelfBullet.setTabCompleter(this);
		
		giveSelfMag = plugin.getCommand("giveselfmagazine");
		giveSelfMag.setExecutor(this);
		giveSelfMag.setTabCompleter(this);
		
		repairSelfGun = plugin.getCommand("repairselfgun");
		repairSelfGun.setExecutor(this);
		repairSelfGun.setTabCompleter(this);
		
		
		if (config.isConfigurationSection("global.self")) {
			ConfigurationSection commandCfg = config.getConfigurationSection("global.self");
			
			if (commandCfg.isConfigurationSection("gun")) {
				if (commandCfg.getBoolean("gun.give", false)) {
					giveSelfGunEnabled = true;
					giveSelfGunLimit = commandCfg.getInt("gun.limit", 0);
					giveSelfGunTimeout = commandCfg.getLong("gun.time", 0);
					giveSelfGunReset = commandCfg.getBoolean("gun.reset", true);
				}
			}
			
			if (commandCfg.isConfigurationSection("magazine")) {
				if (commandCfg.getBoolean("magazine.give", false)) {
					giveSelfMagEnabled = true;
					giveSelfMagLimit = commandCfg.getInt("magazine.limit", 0);
					giveSelfMagTimeout = commandCfg.getLong("magazine.time", 0);
					giveSelfMagReset = commandCfg.getBoolean("magazine.reset", true);
				}
			}
			
			if (commandCfg.isConfigurationSection("bullet")) {
				if (commandCfg.getBoolean("bullet.give", false)) {
					giveSelfBulletEnabled = true;
					giveSelfBulletLimit = commandCfg.getInt("bullet.limit", 0);
					giveSelfBulletTimeout = commandCfg.getLong("bullet.time", 0);
					giveSelfBulletReset = commandCfg.getBoolean("bullet.reset", true);
				}
			}
			
			if (commandCfg.isConfigurationSection("repair")) {
				if (commandCfg.getBoolean("repair.give", false)) {
					repairSelfGunEnabled = true;
					repairSelfGunLimit = commandCfg.getInt("repair.limit", 0);
					repairSelfGunTimeout = commandCfg.getLong("repair.time", 0);
					repairSelfGunReset = commandCfg.getBoolean("repair.reset", true);
				}
			}
		}
		
		if (repairSelfGunTimeout > 0 || giveSelfBulletTimeout > 0 || giveSelfMagTimeout > 0 || giveSelfGunTimeout > 0) {
			plugin.getServer().getPluginManager().registerEvents(new Listener() {
				@EventHandler(ignoreCancelled=true, priority=EventPriority.NORMAL)
				public void deathReset(PlayerDeathEvent event) {
					UUID pid = event.getEntity().getUniqueId();
					// TODO: handle resets.
					if (giveSelfGunReset) {
						selfGunTimeouts.remove(pid);
					}
					if (giveSelfGunLimit > 0) {
						selfGunLimits.remove(pid);
					}
					if (giveSelfBulletReset) {
						selfBulletTimeouts.remove(pid);
					}
					if (giveSelfBulletLimit > 0) {
						selfBulletLimits.remove(pid);
					}
					if (giveSelfMagReset) {
						selfMagTimeouts.remove(pid);
					}
					if (giveSelfMagLimit > 0) {
						selfMagLimits.remove(pid);
					}
					if (repairSelfGunReset) {
						selfRepairTimeouts.remove(pid);
					}
					if (repairSelfGunLimit > 0) {
						selfRepairLimits.remove(pid);
					}
				};
			}, plugin);
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String inv, String[] args) {
		if (cmd.equals(giveGun)) {
			if (args.length < 2) {// <player> <gun>
				return false;
			} else {
				Player target = Bukkit.getPlayer(args[0]); // player!
				if (target == null) {
					sender.sendMessage("Could not find player " + args[0]);
					return true;
				}
				String gunLabel = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
				BasicGun gun = plugin.getGun(gunLabel);
				if (gun == null) {
					sender.sendMessage("Could not find gun " + gunLabel + ". Try using tab-complete to search for valid guns.");
					return true;
				}
				target.getInventory().addItem(gun.getMinimalGun());
				sender.sendMessage("Gave gun " + gunLabel + " to " + args[0]);
				return true;
			}
		} else if (cmd.equals(giveBullet)) {
			if (args.length < 2) {// <player> <bullet> [<amt>]
				return false;
			} else {
				Player target = Bukkit.getPlayer(args[0]); // player!
				if (target == null) {
					sender.sendMessage("Could not find player " + args[0]);
					return true;
				}
				String bulletLabel1 = String.join(" ", Arrays.copyOfRange(args, 1, args.length)); // no amount
				int amt = 1;
				Bullet bullet = plugin.getAmmo().getBullet(bulletLabel1);
				if (bullet == null) {
					String bulletLabel2 = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1)); // with amount 
					
					bullet = plugin.getAmmo().getBullet(bulletLabel2);

					if (bullet == null) {
						sender.sendMessage("Could not find bullet " + bulletLabel1 + " or " + bulletLabel2 + ". Try using tab-complete to search for valid bullets.");
						return true;
					}
					try {
						amt = Integer.parseInt(args[args.length - 1]);
					} catch (NumberFormatException nfe) {
						sender.sendMessage("Invalid amount, defaulting to 1");
						amt = 1;
					}
				}
				ItemStack bulletItem = bullet.getBulletItem();
				if (amt > bulletItem.getMaxStackSize()) {
					amt = bulletItem.getMaxStackSize();
				} else if (amt < 1) {
					amt = 1;
				}
				bulletItem.setAmount(amt);
				target.getInventory().addItem(bulletItem);
				sender.sendMessage("Gave " + amt + " " + bullet.getName() + (amt > 1 ? "s" : "") + " to " + args[0]);
				return true;
			}
		} else if (cmd.equals(giveMag)) {
			if (args.length < 2) {// <player> <magazine>
				return false;
			} else {
				Player target = Bukkit.getPlayer(args[0]); // player!
				if (target == null) {
					sender.sendMessage("Could not find player " + args[0]);
					return true;
				}
				String magazineLabel = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
				Magazine magazine = plugin.getAmmo().getMagazine(magazineLabel);
				if (magazine == null) {
					sender.sendMessage("Could not find magazine " + magazineLabel + ". Try using tab-complete to search for valid magazines.");
					return true;
				}
				ItemStack magazineItem = magazine.getMagazineItem(null, 0);
				target.getInventory().addItem(magazineItem);
				sender.sendMessage("Gave a fresh " + magazineLabel + " to " + args[0]);
				return true;
			}
		} else if (cmd.equals(repairGun)) {
			if (args.length < 1) {// <player>
				return false;
			} else {
				Player target = Bukkit.getPlayer(args[0]); // player!
				if (target == null) {
					sender.sendMessage("Could not find player " + args[0]);
					return true;
				}
				
				ItemStack inHand = target.getInventory().getItemInMainHand();
				
				Set<String> guns = plugin.getGunNames();
				
				for (String gunName : guns) {
					BasicGun gun = plugin.getGun(gunName);
					if (gun.isGun(inHand)) {
						inHand = gun.repairGun(inHand);
						target.getInventory().setItemInMainHand(inHand);
						target.updateInventory();
						
						sender.sendMessage("Triggered repair for " + gunName + " held  by " + args[0]);
						return true;
					}
				}
				sender.sendMessage("Player " + args[0] + " does not appear to be holding a valid gun.");
				return true;
			}
		} else if (sender instanceof Player) {
			Player player = (Player) sender;
			UUID pid = player.getUniqueId();
			if (giveSelfGunEnabled && cmd.equals(giveSelfGun) ) {
				if (giveSelfGunTimeout > 0l && selfGunTimeouts.getOrDefault(pid, System.currentTimeMillis() - 1000l) < System.currentTimeMillis()) {
					selfGunLimits.put(pid, 0); // reset limits.
					selfGunTimeouts.put(pid, System.currentTimeMillis() + giveSelfGunTimeout);
				}
				if (giveSelfGunLimit == 0 || selfGunLimits.getOrDefault(pid, 0) < giveSelfGunLimit) {
					// ready to go
					
					String gunLabel = String.join(" ", args);
					BasicGun gun = plugin.getGun(gunLabel);
					if (gun == null) {
						new ViewGuns(player);
						return true;
					} else {
						player.getInventory().addItem(gun.getMinimalGun());
						sender.sendMessage("Gave you gun " + gunLabel);
						plugin.info("Gave gun " + gunLabel + " to " + player.getName());
					}					
					
					selfGunLimits.compute(pid, (p, i) -> {
						if (i == null) {
							i = 1;
						} else { 
							i = i + 1;
						}
						return i;
					});
					return true;
				} else {
					sender.sendMessage("Gun limit reached, please wait a bit and try again");
					return true;
				}
			} else if (giveSelfBulletEnabled && cmd.equals(giveSelfBullet)) {
				if (giveSelfBulletTimeout > 0l && selfBulletTimeouts.getOrDefault(pid, System.currentTimeMillis() - 1000l) < System.currentTimeMillis()) {
					selfBulletLimits.put(pid, 0); // reset limits.
					selfBulletTimeouts.put(pid, System.currentTimeMillis() + giveSelfBulletTimeout);
				}
				if (giveSelfBulletLimit == 0 || selfBulletLimits.getOrDefault(pid, 0) < giveSelfBulletLimit) {
					// ready to go
					
					String bulletLabel1 = String.join(" ", args); // no amount
					int amt = 1;
					Bullet bullet = plugin.getAmmo().getBullet(bulletLabel1);
					if (bullet == null && args.length > 0) {
						String bulletLabel2 = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 1)); // with amount 
						
						bullet = plugin.getAmmo().getBullet(bulletLabel2);

						if (bullet == null) {
							new ViewBullets(player);
							return true;
						}
						try {
							amt = Integer.parseInt(args[args.length - 1]);
						} catch (NumberFormatException nfe) {
							sender.sendMessage("Invalid amount, defaulting to 1");
							amt = 1;
						}
					} else if (args.length == 0) {
						new ViewBullets(player);
						return true;						
					}
					ItemStack bulletItem = bullet.getBulletItem();
					if (amt > bulletItem.getMaxStackSize()) {
						amt = bulletItem.getMaxStackSize();
					} else if (amt < 1) {
						amt = 1;
					}
					bulletItem.setAmount(amt);
					player.getInventory().addItem(bulletItem);
					sender.sendMessage("Gave " + amt + " " + bullet.getName() + (amt > 1 ? "s" : "") + " to you");
					plugin.info("Gave " + amt + " " + bullet.getName() + (amt > 1 ? "s" : "") + " to " + player.getName());
					
					selfBulletLimits.compute(pid, (p, i) -> {
						if (i == null) {
							i = 1;
						} else { 
							i = i + 1;
						}
						return i;
					});
					return true;
				} else {
					sender.sendMessage("Bullet limit reached, please wait a bit and try again");
					return true;
				}
			} else if (giveSelfMagEnabled && cmd.equals(giveSelfMag)) {
				if (giveSelfMagTimeout > 0l && selfMagTimeouts.getOrDefault(pid, System.currentTimeMillis() - 1000l) < System.currentTimeMillis()) {
					selfMagLimits.put(pid, 0); // reset limits.
					selfMagTimeouts.put(pid, System.currentTimeMillis() + giveSelfMagTimeout);
				}
				if (giveSelfMagLimit == 0 || selfMagLimits.getOrDefault(pid, 0) < giveSelfMagLimit) {
					// ready to go
					
					String magLabel = String.join(" ", args);
					Magazine mag = plugin.getAmmo().getMagazine(magLabel);
					if (mag == null) {
						new ViewMagazines(player);
						return true;
					}
					ItemStack magItem = mag.getMagazineItem(null, 0);
					player.getInventory().addItem(magItem);
					sender.sendMessage("Gave a fresh " + magLabel + " to you");
					plugin.info("Gave a fresh " + magLabel + " to " + player.getName());
					
					selfMagLimits.compute(pid, (p, i) -> {
						if (i == null) {
							i = 1;
						} else { 
							i = i + 1;
						}
						return i;
					});
					return true;
				} else {
					sender.sendMessage("Magazine limit reached, please wait a bit and try again");
					return true;
				}
			} else if (repairSelfGunEnabled && cmd.equals(repairSelfGun)) {
				if (repairSelfGunTimeout > 0l && selfRepairTimeouts.getOrDefault(pid, System.currentTimeMillis() - 1000l) < System.currentTimeMillis()) {
					selfRepairLimits.put(pid, 0); // reset limits.
					selfRepairTimeouts.put(pid, System.currentTimeMillis() + repairSelfGunTimeout);
				}
				if (repairSelfGunLimit == 0 || selfRepairLimits.getOrDefault(pid, 0) < repairSelfGunLimit) {
					// ready to go
					
					ItemStack inHand = player.getInventory().getItemInMainHand();
					
					Set<String> guns = plugin.getGunNames();
					
					for (String gunName : guns) {
						BasicGun gun = plugin.getGun(gunName);
						if (gun.isGun(inHand)) {
							inHand = gun.repairGun(inHand);
							player.getInventory().setItemInMainHand(inHand);
							player.updateInventory();
							
							sender.sendMessage("Triggered repair for your " + gunName);
							plugin.info("Triggered repair for " + gunName + " held by " + player.getName());

							selfRepairLimits.compute(pid, (p, i) -> {
								if (i == null) {
									i = 1;
								} else { 
									i = i + 1;
								}
								return i;
							});
							
							return true;
						}
					}
					sender.sendMessage("You don't appear to be holding a valid gun!");
					
					return true;
				} else {
					sender.sendMessage("Repair limit reached, please wait a bit and try again");
					return true;
				}
			}
			sender.sendMessage("You don't have access to that command, sorry.");
		}
		return false;
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String inv, String[] args) {
		StringBuffer sb = new StringBuffer();
		sb.append(sender.getName()).append(" tab ").append(cmd.getName()).append(" inv ").append(inv);
		sb.append(" args '");
		for (String arg : args) {
			sb.append(" ").append(arg);
		}
		sb.append("'");
		AddGun.getPlugin().debug(sb.toString());
		
		if (cmd.equals(repairGun) || cmd.equals(giveGun) || cmd.equals(giveBullet) || cmd.equals(giveMag)) {
			if (args.length <= 1){
				String almost = (args.length == 1) ? args[0] : null;
				
				List<String> names = new ArrayList<String>();
				for (Player online : Bukkit.getOnlinePlayers()) {
					if (almost == null || almost.equals("") || online.getName().contains(almost)) {
						names.add(online.getName());
					}
				}
				return names;
			}
		}

		if (cmd.equals(giveGun)) {
			if (args.length >= 2) {
				if (args[1] == null || args[1].equals("")) {
					return new ArrayList<String>(plugin.getGunNames());
				}
				String gunLabel = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
				Set<String> guns = plugin.getGunNames();
				List<String> maybeGuns = new ArrayList<String>();
				for (String gun : guns) {
					if (gun.startsWith(gunLabel)) {
						maybeGuns.add(gun);
					}
				}
				return maybeGuns;
			} else {
				return null;
			}
		} else if (cmd.equals(giveBullet)) {
			if (args.length >= 2) {
				if (args[1] == null || args[1].equals("")) {
					return new ArrayList<String>(plugin.getAmmo().allBulletNames());
				}
				String bulletLabel = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
				Set<String> bullets = plugin.getAmmo().allBulletNames();
				List<String> maybeBullets = new ArrayList<String>();
				for (String bullet : bullets) {
					if (bullet.startsWith(bulletLabel)) {
						maybeBullets.add(bullet);
					}
				}
				return maybeBullets;
			} else {
				return null;
			}
		} else if (cmd.equals(giveMag)) {
			if (args.length >= 2) {
				if (args[1] == null || args[1].equals("")) {
					return new ArrayList<String>(plugin.getAmmo().allMagazineNames());
				}
				
				String magLabel = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
				Set<String> mags = plugin.getAmmo().allMagazineNames();
				List<String> maybeMags = new ArrayList<String>();
				for (String mag : mags) {
					if (mag.startsWith(magLabel)) {
						maybeMags.add(mag);
					}
				}
				return maybeMags;
			} else {
				return null;
			}
		}
		
		if (cmd.equals(giveSelfGun)) {
			if (args.length >= 1) {
				if (args[0] == null || args[0].equals("")) {
					return new ArrayList<String>(plugin.getGunNames());
				}
				String gunLabel = String.join(" ", args);
				Set<String> guns = plugin.getGunNames();
				List<String> maybeGuns = new ArrayList<String>();
				for (String gun : guns) {
					if (gun.startsWith(gunLabel)) {
						maybeGuns.add(gun);
					}
				}
				return maybeGuns;
			} else {
				return null;
			}
		} else if (cmd.equals(giveSelfBullet)) {
			if (args.length >= 1) {
				if (args[0] == null || args[0].equals("")) {
					return new ArrayList<String>(plugin.getAmmo().allBulletNames());
				}
				String bulletLabel = String.join(" ", args);
				Set<String> bullets = plugin.getAmmo().allBulletNames();
				List<String> maybeBullets = new ArrayList<String>();
				for (String bullet : bullets) {
					if (bullet.startsWith(bulletLabel)) {
						maybeBullets.add(bullet);
					}
				}
				return maybeBullets;
			} else {
				return null;
			}
		} else if (cmd.equals(giveSelfMag)) {
			if (args.length >= 1) {
				if (args[0] == null || args[0].equals("")) {
					return new ArrayList<String>(plugin.getAmmo().allMagazineNames());
				}
				
				String magLabel = String.join(" ", args);
				Set<String> mags = plugin.getAmmo().allMagazineNames();
				List<String> maybeMags = new ArrayList<String>();
				for (String mag : mags) {
					if (mag.startsWith(magLabel)) {
						maybeMags.add(mag);
					}
				}
				return maybeMags;
			} else {
				return null;
			}
		}

		return null;
	}
}
