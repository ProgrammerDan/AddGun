package com.programmerdan.minecraft.addgun.ammo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.google.common.collect.Sets;
import com.programmerdan.minecraft.addgun.AddGun;
import com.programmerdan.minecraft.addgun.events.LoadMagazineEvent;
import com.programmerdan.minecraft.addgun.events.LoadGunEvent;
import com.programmerdan.minecraft.addgun.guns.StandardGun;

/**
 * Intent here is this class holds configurations of bullets and provides easy accessors.
 * Each gun will have a list of bullets that it can use.
 * 
 * Bullets and magazines are constructed from config.
 * 
 * @author ProgrammerDan
 *
 */
public class Bullets implements Listener {

	private Map<String, Bullet> tagMap = new ConcurrentHashMap<>();
	
	private Map<String, Magazine> magazineTagMap = new ConcurrentHashMap<>();
	
	private Map<String, Bullet> nameMap = new ConcurrentHashMap<>();
	
	private Map<String, Magazine> magazineMap = new ConcurrentHashMap<>();
	
	private Map<Material, Set<Bullet>> map = new ConcurrentHashMap<>();
	
	private Map<Material, Set<Magazine>> magazines = new ConcurrentHashMap<>();
	
	/**
	 * Register a bullet. Handles all index mapping.
	 * 
	 * @param bullet the bullet to register
	 */
	public void registerBullet(Bullet bullet) {
		if (bullet == null) return;
		
		nameMap.put(bullet.getName(), bullet);
		
		map.compute(bullet.getMaterialType(), (k, s) -> {
			if (s == null) {
				s = Sets.newConcurrentHashSet();
			}
			s.add(bullet);
			return s;
		});
		
		tagMap.put(bullet.getTag(), bullet);
	}
	
	/**
	 * Registers a magazine. Handles all index mapping.
	 * 
	 * @param magazine the magazine to regsiter
	 */
	public void registerMagazine(Magazine magazine) {
		if (magazine == null) return;
		
		magazineMap.put(magazine.getName(), magazine);
		
		magazines.compute(magazine.getMaterialType(), (k, s) -> {
			if (s == null) {
				s = Sets.newConcurrentHashSet();
			}
			s.add(magazine);
			return s;
		});
		
		magazineTagMap.put(magazine.getTag(), magazine);
	}
	
	/**
	 * Using a supplied item, identifies which bullet, or if none, null.
	 * 
	 * @param bullet the ItemStack to check
	 * @return a Bullet or null.
	 */
	public Bullet findBullet(ItemStack bullet) {
		if (bullet == null) return null;
		Set<Bullet> set = map.get(bullet.getType());
		if (set == null) return null;
		// TODO: Can we do better?
		for (Bullet sbullet : set) {
			if (sbullet.isBullet(bullet)) {
				return sbullet;
			}
		}
		return null;
	}

	/**
	 * Using the provided name, gets the bullet, or null.
	 * @param name the name of the bullet
	 * @return the Bullet or null if no match
	 */
	public Bullet getBullet(String name) {
		return nameMap.get(name);
	}
	
	/**
	 * Using the provided loretag, gets the bullet, or null.
	 * 
	 * @param tag the loretag of the bullet
	 * @return the Bullet or null if no match
	 */
	public Bullet getBulletByTag(String tag) {
		return tagMap.get(tag);
	}
	
	/**
	 * Using a supplied item, identifies which magazine, or if none, null.
	 * 
	 * @param magazine the ItemStack to check
	 * @return a Magazine or null.
	 */
	public Magazine findMagazine(ItemStack magazine) {
		if (magazine == null) return null;
		Set<Magazine> set = magazines.get(magazine.getType());
		if (set == null) return null;
		// TODO: Can we do better?
		for (Magazine smag : set) {
			if (smag.isMagazine(magazine)) {
				return smag;
			}
		}
		return null;
	}

	/**
	 * Using the provided name, gets the magazine, or null.
	 * @param name the name of the magazine
	 * @return the Magazine or null if no match
	 */
	public Magazine getMagazine(String name) {
		return magazineMap.get(name);
	}
	
	/**
	 * Using the provided loretag, gets the magazine, or null.
	 * 
	 * @param tag the loretag of the magazine
	 * @return the Magazine or null if no match
	 */
	public Magazine getMagazineByTag(String tag) {
		return magazineTagMap.get(tag);
	}
	
	/**
	 * Returns a representation of all the magazines configured by names
	 * 
	 * @return a set of magazine names
	 */
	public Set<String> allMagazineNames() {
		return magazineMap.keySet();
	}
	
	/**
	 * Returns a representation of all the bullets configured by names
	 * 
	 * @return a set of bullet names
	 */
	public Set<String> allBulletNames() {
		return nameMap.keySet();
	}
	
	/**
	 * This handles load / unload events.
	 * 
	 * @param event The inventory click event
	 * 
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void interactMagazineEvent(InventoryClickEvent event) {
		if (!(InventoryAction.SWAP_WITH_CURSOR.equals(event.getAction()) || 
				InventoryAction.PICKUP_ALL.equals(event.getAction()) || 
				InventoryAction.PICKUP_HALF.equals(event.getAction()) || 
				InventoryAction.PICKUP_SOME.equals(event.getAction()) || 
				InventoryAction.PICKUP_ONE.equals(event.getAction())) | !event.isRightClick()) {
			return;
		}
		
		//HumanEntity human = event.getWhoClicked();

		ItemStack current = event.getCurrentItem();
		ItemStack cursor = event.getCursor();
		
		Magazine currentMag = findMagazine(current);
		if (currentMag == null) return;
		
		Bullet cursorBullet = null;
		
		if (cursor != null && !Material.AIR.equals(cursor.getType())) {
			cursorBullet = findBullet(cursor);
			if (cursorBullet != null) {
				// load / swap event.
				ItemStack[] outcome = currentMag.loadMagazine(current, cursorBullet, cursor.getAmount());
				
				LoadMagazineEvent magEvent = new LoadMagazineEvent(currentMag, cursorBullet, cursor.getAmount(), event.getWhoClicked());
				Bukkit.getServer().getPluginManager().callEvent(magEvent);
				if (magEvent.isCancelled()) return;
				
				event.setCurrentItem(outcome[0]);
				event.setCursor(outcome[1]); // why tf is this deprecated?!
				event.setResult(Result.DENY);
			}
		} else {
			// unload event.
			ItemStack[] outcome = currentMag.unloadMagazine(current);
			event.setCurrentItem(outcome[0]);
			event.setCursor(outcome[1]); // why tf is this deprecated?!
			event.setResult(Result.DENY);
		}
	}
	
	/**
	 * Quick check if there are any magazines registered
	 * @return true if yes, otherwise false
	 */
	public boolean hasMagazines() {
		return !this.magazineMap.isEmpty();
	}
}
