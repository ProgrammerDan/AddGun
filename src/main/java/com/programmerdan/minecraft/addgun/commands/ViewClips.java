package com.programmerdan.minecraft.addgun.commands;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.ammo.Clip;

public class ViewClips extends ViewMenu {

	public ViewClips(Player player) {
		super(player, new ArrayList<>( AddGun.getPlugin().getAmmo().allClipNames() ), "Clip");
	}
	
	@Override
	public ItemStack getRepresentation(String identifier) {
		Clip clip = plugin.getAmmo().getClip(identifier);
		if (clip == null) return null;
		return clip.getClipItem(null, 0);
	}

	@Override
	public String getCommand(String identifier) {
		Clip clip = plugin.getAmmo().getClip(identifier);
		if (clip == null) return null;
		return String.format("gclip %s", clip.getName());
	}

}
