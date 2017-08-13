package com.programmerdan.minecraft.addgun.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Clip;
import com.programmerdan.minecraft.addgun.guns.BasicGun;

public class CommandHandler implements CommandExecutor, TabCompleter {
	private AddGun plugin;
	
	private PluginCommand giveGun;
	private PluginCommand giveBullet;
	private PluginCommand giveClip;
	private PluginCommand repairGun;
	
	public CommandHandler(FileConfiguration config) {
		plugin = AddGun.getPlugin();
		
		giveGun = plugin.getCommand("givegun");
		giveGun.setExecutor(this);
		giveGun.setTabCompleter(this);

		giveBullet = plugin.getCommand("givebullet");
		giveBullet.setExecutor(this);
		giveBullet.setTabCompleter(this);
		
		giveClip = plugin.getCommand("giveclip");
		giveClip.setExecutor(this);
		giveClip.setTabCompleter(this);
		
		repairGun = plugin.getCommand("repairGun");
		repairGun.setExecutor(this);
		repairGun.setTabCompleter(this);
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
				BasicGun gun = plugin.getGun(args[1]);
				if (gun == null) {
					sender.sendMessage("Could not find gun " + args[1] + ". Try using tab-complete to search for valid guns.");
					return true;
				}
				target.getInventory().addItem(gun.getMinimalGun());
				sender.sendMessage("Gave gun " + args[1] + " to " + args[0]);
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
				Bullet bullet = plugin.getAmmo().getBullet(args[1]);
				if (bullet == null) {
					sender.sendMessage("Could not find bullet " + args[1] + ". Try using tab-complete to search for valid bullets.");
					return true;
				}
				ItemStack bulletItem = bullet.getBulletItem();
				int amt = 1;
				if (args.length > 2) { // <amount>
					try {
						amt = Integer.parseInt(args[2]);
						if (amt > bulletItem.getMaxStackSize()) {
							amt = bulletItem.getMaxStackSize();
						} else if (amt < 1) {
							amt = 1;
						}
					} catch (NumberFormatException nfe) {
						sender.sendMessage("Invalid amount, defaulting to 1");
						amt = 1;
					}
				}
				bulletItem.setAmount(amt);
				target.getInventory().addItem(bulletItem);
				sender.sendMessage("Gave " + amt + " " + args[1] + (amt > 1 ? "s" : "") + " to " + args[0]);
				return true;
			}
		} else if (cmd.equals(giveClip)) {
			if (args.length < 2) {// <player> <clip>
				return false;
			} else {
				Player target = Bukkit.getPlayer(args[0]); // player!
				if (target == null) {
					sender.sendMessage("Could not find player " + args[0]);
					return true;
				}
				Clip clip = plugin.getAmmo().getClip(args[1]);
				if (clip == null) {
					sender.sendMessage("Could not find clip " + args[1] + ". Try using tab-complete to search for valid clips.");
					return true;
				}
				ItemStack clipItem = clip.getClipItem(null, 0);
				target.getInventory().addItem(clipItem);
				sender.sendMessage("Gave a fresh " + args[1] + " to " + args[0]);
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
		}
		return false;
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String inv, String[] args) {
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

		if (cmd.equals(giveGun)) {
			if (args.length == 2) {
				if (args[1] == null || args[1].equals("")) {
					return new ArrayList<String>(plugin.getGunNames());
				}
				Set<String> guns = plugin.getGunNames();
				List<String> maybeGuns = new ArrayList<String>();
				for (String gun : guns) {
					if (gun.contains(args[1])) {
						maybeGuns.add(gun);
					}
				}
				return maybeGuns;
			} else {
				return null;
			}
		} else if (cmd.equals(giveBullet)) {
			if (args.length == 2) {
				if (args[1] == null || args[1].equals("")) {
					return new ArrayList<String>(plugin.getAmmo().allBulletNames());
				}
				Set<String> bullets = plugin.getAmmo().allBulletNames();
				List<String> maybeBullets = new ArrayList<String>();
				for (String bullet : bullets) {
					if (bullet.contains(args[1])) {
						maybeBullets.add(bullet);
					}
				}
				return maybeBullets;
			} else {
				return null;
			}
		} else if (cmd.equals(giveClip)) {
			if (args.length == 2) {
				if (args[1] == null || args[1].equals("")) {
					return new ArrayList<String>(plugin.getAmmo().allClipNames());
				}
				Set<String> clips = plugin.getAmmo().allClipNames();
				List<String> maybeClips = new ArrayList<String>();
				for (String clip : clips) {
					if (clip.contains(args[1])) {
						maybeClips.add(clip);
					}
				}
				return maybeClips;
			} else {
				return null;
			}
		}
		return null;
	}
}
