package com.programmerdan.minecraft.addgun.commands;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.Magazine;

public class ViewMagazines extends ViewMenu {

	public ViewMagazines(Player player) {
		super(player, new ArrayList<>( AddGun.getPlugin().getAmmo().allMagazineNames() ), "Magazine");
	}
	
	@Override
	public ItemStack getRepresentation(String identifier) {
		Magazine magazine = plugin.getAmmo().getMagazine(identifier);
		if (magazine == null) return null;
		return magazine.getMagazineItem(null, 0);
	}

	@Override
	public String getCommand(String identifier) {
		Magazine magazine = plugin.getAmmo().getMagazine(identifier);
		if (magazine == null) return null;
		return String.format("gmagazine %s", magazine.getName());
	}

}
