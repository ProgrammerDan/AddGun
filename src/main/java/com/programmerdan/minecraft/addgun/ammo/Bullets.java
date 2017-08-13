package com.programmerdan.minecraft.addgun.ammo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import com.programmerdan.minecraft.addgun.guns.StandardGun;

/**
 * Intent here is this class holds configurations of bullets and provides easy accessors.
 * Each gun will have a list of bullets that it can use.
 * 
 * Bullets and clips are constructed from config.
 * 
 * @author ProgrammerDan
 *
 */
public class Bullets implements Listener {

	private Map<String, Bullet> tagMap = new ConcurrentHashMap<>();
	
	private Map<String, Clip> clipTagMap = new ConcurrentHashMap<>();
	
	private Map<String, Bullet> nameMap = new ConcurrentHashMap<>();
	
	private Map<String, Clip> clipMap = new ConcurrentHashMap<>();
	
	private Map<Material, Set<Bullet>> map = new ConcurrentHashMap<>();
	
	private Map<Material, Set<Clip>> clips = new ConcurrentHashMap<>();
	
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
	 * Registers a clip. Handles all index mapping.
	 * 
	 * @param clip the clip to regsiter
	 */
	public void registerClip(Clip clip) {
		if (clip == null) return;
		
		clipMap.put(clip.getName(), clip);
		
		clips.compute(clip.getMaterialType(), (k, s) -> {
			if (s == null) {
				s = Sets.newConcurrentHashSet();
			}
			s.add(clip);
			return s;
		});
		
		clipTagMap.put(clip.getTag(), clip);
	}
	
	/**
	 * Using a supplied item, identifies which bullet, or if none, null.
	 * 
	 * @param bullet the ItemStack to check
	 * @return a Bullet or null.
	 */
	public Bullet findBullet(ItemStack bullet) {
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
	 * Using a supplied item, identifies which clip, or if none, null.
	 * 
	 * @param clip the ItemStack to check
	 * @return a Clip or null.
	 */
	public Clip findClip(ItemStack clip) {
		Set<Clip> set = clips.get(clip.getType());
		if (set == null) return null;
		// TODO: Can we do better?
		for (Clip sclip : set) {
			if (sclip.isClip(clip)) {
				return sclip;
			}
		}
		return null;
	}

	/**
	 * Using the provided name, gets the clip, or null.
	 * @param name the name of the clip
	 * @return the Clip or null if no match
	 */
	public Clip getClip(String name) {
		return clipMap.get(name);
	}
	
	/**
	 * Using the provided loretag, gets the clip, or null.
	 * 
	 * @param tag the loretag of the clip
	 * @return the Clip or null if no match
	 */
	public Clip getClipByTag(String tag) {
		return clipTagMap.get(tag);
	}
	
	/**
	 * Returns a representation of all the clips configured by names
	 * 
	 * @return a set of clip names
	 */
	public Set<String> allClipNames() {
		return clipMap.keySet();
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
	public void interactClipEvent(InventoryClickEvent event) {
		if (!InventoryAction.SWAP_WITH_CURSOR.equals(event.getAction()) | !event.isRightClick()) {
			return;
		}
		
		//HumanEntity human = event.getWhoClicked();

		ItemStack current = event.getCurrentItem();
		ItemStack cursor = event.getCursor();
		
		Clip currentClip = findClip(current);
		if (currentClip == null) return;
		
		Bullet cursorBullet = null;
		
		if (cursor != null) {
			cursorBullet = findBullet(cursor);
			if (cursorBullet != null) {
				// load / swap event.
				ItemStack[] outcome = currentClip.loadClip(current, cursorBullet, cursor.getAmount());
				event.setCurrentItem(outcome[0]);
				event.setCursor(outcome[1]); // why tf is this deprecated?!
				event.setResult(Result.DENY);
			}
		} else {
			// unload event.
			ItemStack[] outcome = currentClip.unloadClip(current);
			event.setCurrentItem(outcome[0]);
			event.setCursor(outcome[1]); // why tf is this deprecated?!
			event.setResult(Result.DENY);
		}
	}
	
	/**
	 * Quick check if there are any clips registered
	 * @return true if yes, otherwise false
	 */
	public boolean hasClips() {
		return !this.clipMap.isEmpty();
	}
}
