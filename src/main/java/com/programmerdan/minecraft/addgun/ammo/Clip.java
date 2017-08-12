package com.programmerdan.minecraft.addgun.ammo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.common.collect.Sets;
import com.programmerdan.minecraft.addgun.AddGun;

import static com.programmerdan.minecraft.addgun.guns.Utilities.getClipData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.updateClipData;

/**
 * Wrapper class for "clips" which are bullet holders. Not all guns need support clips. Some only support clips.
 * 
 * @author ProgrammerDan
 */
public class Clip {
	
	private String name;
	
	private String tag;
	
	private ItemStack example;
	
	private Set<String> allowedBullets = Sets.newConcurrentHashSet();
	
	private Map<String, Integer> allowsRounds = new ConcurrentHashMap<String, Integer>();
	
	/**
	 * Lightweight check to see if the given itemstack is a clip
	 * @param toCheck
	 * @return
	 */
	public boolean isClip(ItemStack toCheck) {
		if (toCheck == null)
			return false;

		if (!example.getType().equals(toCheck.getType()))
			return false;

		if (!toCheck.hasItemMeta())
			return false;

		ItemMeta meta = toCheck.getItemMeta();

		if (meta.getLore().contains(tag))
			return true;

		return false;

	}
	
	public String getName() {
		return name;
	}
	
	public String getTag() {
		return tag;
	}
	
	public Material getMaterialType() {
		return example.getType();
	}

	/**
	 * Creates a "fresh" clip, using the example itemstack, locks it to the "bullet" type if any, and fills it with rounds.
	 * @param bullet The bullet type, or null
	 * @param i number of rounds. If bullet type is null, this is ignored.
	 * @return a new Clip object for inventories.
	 */
	public ItemStack getClipItem(Bullet bullet, int i) {
		ItemStack clip = example.clone();
		Map<String, Object> clipData = new HashMap<String, Object>();
		if (bullet != null) {
			clipData.put("ammo", bullet.getName());
			clipData.put("rounds", Integer.valueOf(i));
		} else {
			clipData.put("ammo", null);
			clipData.put("rounds", Integer.valueOf(0));
		}
		
		clip = updateClipData(clip, clipData);

		return clip;
	}
	
	/**
	 * Tries to put more bullets into this clip. 
	 * 
	 * Fails if the clip has bullets of a different type or is locked to bullets of a different type.
	 * 
	 * @param clip
	 * @param bullet
	 * @param i
	 * @return
	 */
	public ItemStack[] loadClip(ItemStack clip, Bullet bullet, int i) {
		ItemStack bullets = bullet.getBulletItem();
		if (!this.allowedBullets.contains(bullet.getName())) {
			bullets.setAmount(i);
			return new ItemStack[] {clip, bullets};
		}
		
		Map<String, Object> clipData = getClipData(clip);
		if (clipData.containsKey("ammo")) { // locked to an ammo
			if (bullet.getName().equals((String) clipData.get("ammo"))) { // same!
				int toLoad = Math.min(this.allowsRounds.get(bullet.getName()), i + ((Integer) clipData.get("rounds"))); // max vs. current + new
				clipData.put("rounds", Integer.valueOf(toLoad));
				i = i - toLoad;
				clip = updateClipData(clip, clipData);
			} else { // different!
				bullets.setAmount(i);
				return new ItemStack[] {clip, bullets}; // cancel
			}
		} else { // not locked, go for it.
			clipData.put("ammo", bullet.getName());
			int toLoad = Math.min(this.allowsRounds.get(bullet.getName()), i);
			clipData.put("rounds", Integer.valueOf(toLoad));
			i = i - toLoad; // remainder
			clip = updateClipData(clip, clipData);
		}
		if (i > 0)  {
			bullets.setAmount(i);
		} else {
			bullets = null;
		}
		
		return new ItemStack[] {clip, bullets}; // ok
	}
	
	/**
	 * Removes the bullets from this clip (if possible)
	 * 
	 * Fails if this clip has no bullets.
	 * @param clip The clip to unload
	 * @return a 2 element array; element 0 is the clip, modified, element 1 is the bullets retrieved if any
	 */
	public ItemStack[] unloadClip(ItemStack clip) {
		ItemStack bullets = null;
		Map<String, Object> clipData = getClipData(clip);
		if (clipData.containsKey("ammo")) { // locked to an ammo, might have ammo
			Bullet bullet = AddGun.getPlugin().getAmmo().getBullet((String) clipData.get("ammo"));
			if (bullet != null) { // ok.
				bullets = bullet.getBulletItem();
				int unload = (Integer) clipData.get("rounds");
				if (unload > 0) {
					bullets.setAmount(unload);
				} else {
					bullets = null;
				}
				clipData.put("ammo", null);
				clipData.put("rounds", 0);
				clip = updateClipData(clip, clipData);
			} else { // otherwise it's... invalid ammo
				// unable to unload! bad clip?!
				// TODO messaging
			}
		} // otherwise its already empty
		
		return new ItemStack[] {clip, bullets}; // ok
	}

	/**
	 * Returns the bullet type of this clip, or null if can't figure it out.
	 * 
	 * @param clip the Clip itemStack
	 * @return the Bullet type held by this clip.
	 */
	public Bullet getBulletType(ItemStack clip) {
		Map<String, Object> clipData = getClipData(clip);
		if (clipData.containsKey("ammo")) { // locked to an ammo, might have ammo
			return AddGun.getPlugin().getAmmo().getBullet((String) clipData.get("ammo"));
		}
		
		return null;
	}
	
	/**
	 * Returns the rounds in this clip, or null if can't figure it out.
	 * 
	 * @param clip the Clip itemStack
	 * @return the number of rounds in the clip
	 */
	public Integer getRounds(ItemStack clip) {
		Map<String, Object> clipData = getClipData(clip);
		if (clipData.containsKey("rounds")) { 
			return (Integer) clipData.get("rounds");
		}
		
		return null;
	}
}
