package com.programmerdan.minecraft.addgun.commands;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.guns.BasicGun;

public class ViewGuns extends ViewMenu {
	
	public ViewGuns(Player player) {
		super(player, new ArrayList<String>(AddGun.getPlugin().getGunNames()), "Gun");
	}

	@Override
	public ItemStack getRepresentation(String identifier) {
		BasicGun gun = plugin.getGun(identifier);
		if (gun == null) return null;
		return gun.getMinimalGun();
	}

	@Override
	public String getCommand(String identifier) {
		BasicGun gun = plugin.getGun(identifier);
		if (gun == null) return null;
		return String.format("gun %s", gun.getName());
	}
}
