package com.programmerdan.minecraft.addgun.listeners;

import java.util.concurrent.Executors;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.biggestnerd.devotedpvp.ItemSafeEvent;
import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Magazine;
import com.programmerdan.minecraft.addgun.guns.StandardGun;

import org.bukkit.inventory.ItemStack;

public class CompatListener implements Listener {

	AddGun plugin;
	
	public CompatListener() {
		plugin = AddGun.getPlugin();
	}
	
	@EventHandler
	public void safeEventListener(ItemSafeEvent safe) {
		ItemStack item = safe.getItem();
		
		if (item == null) return;
		StandardGun gun = plugin.getGuns().findGun(item);
		
		if (gun != null && gun.validGun(item)) {
			safe.setValid();
			return;
		}
		
		Magazine mag = plugin.getAmmo().findMagazine(item);
		
		// TODO: validmag check
		if (mag != null && item.getAmount() == 1) {
			safe.setValid();
			return;
		}
		
		Bullet bullet = plugin.getAmmo().findBullet(item);
		
		if (bullet != null) {
			if (item.getAmount() <= item.getMaxStackSize() && item.getAmount() > 0) {
				safe.setValid();
			}
		}
	}
}
