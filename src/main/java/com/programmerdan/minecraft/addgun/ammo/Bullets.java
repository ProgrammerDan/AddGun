package com.programmerdan.minecraft.addgun.ammo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;

/**
 * Intent here is this class holds configurations of bullets and provides easy accessors.
 * Each gun will have a list of bullets that it can use.
 * 
 * Bullets are constructed from config.
 * 
 * @author ProgrammerDan
 *
 */
public class Bullets {

	public static Map<String, Bullet> nameMap = new ConcurrentHashMap<>();
	
	public static Map<String, Clip> clipMap = new ConcurrentHashMap<>();
	
	public static Map<Material, Set<Bullet>> map = new ConcurrentHashMap<>();
	
	public static Map<Material, Set<Clip>> clips = new ConcurrentHashMap<>();
	
}
