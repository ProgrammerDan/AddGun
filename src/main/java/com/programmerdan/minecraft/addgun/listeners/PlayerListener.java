package com.programmerdan.minecraft.addgun.listeners;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;

import com.programmerdan.minecraft.addgun.AddGun;


public class PlayerListener implements Listener {
	
	private AddGun plugin;
	
	public PlayerListener(FileConfiguration config) {
		plugin = AddGun.getPlugin();
		
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	public void shutdown() {
		
	}
}
