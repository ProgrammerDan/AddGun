package com.programmerdan.minecraft.addgun.commands;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.Bullet;

public class ViewBullets extends ViewMenu {

	public ViewBullets(Player player) {
		super(player, new ArrayList<>(AddGun.getPlugin().getAmmo().allBulletNames()), "Bullet");
	}
	
	@Override
	public ItemStack getRepresentation(String identifier) {
		Bullet bullet = plugin.getAmmo().getBullet(identifier);
		if (bullet == null) return null;
		return bullet.getBulletItem();
	}

	@Override
	public String getCommand(String identifier) {
		Bullet bullet = plugin.getAmmo().getBullet(identifier);
		if (bullet == null) return null;
		return String.format("bullet %s 64", bullet.getName());
	}

}
