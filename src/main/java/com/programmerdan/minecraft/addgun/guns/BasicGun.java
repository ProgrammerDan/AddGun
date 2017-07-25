package com.programmerdan.minecraft.addgun.guns;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public interface BasicGun {

	public void configure(ConfigurationSection config);
	
	public String getName();
	
	public ItemStack getMinimalGun();
	
	public boolean isGun(ItemStack toCheck);
	
	public boolean isListener();
}
