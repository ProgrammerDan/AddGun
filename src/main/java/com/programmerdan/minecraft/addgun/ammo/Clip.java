package com.programmerdan.minecraft.addgun.ammo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.google.common.collect.Sets;
import com.programmerdan.minecraft.addgun.AddGun;

import static com.programmerdan.minecraft.addgun.guns.Utilities.getClipData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.updateClipData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.updateGunData;

/**
 * Wrapper class for "clips" which are bullet holders. Not all guns need support clips. Some only support clips.
 * 
 * @author ProgrammerDan
 */
public class Clip {
	
	private String name;
	
	private String tag;
	
	private ItemStack example;
	
	private List<String> exampleLore;
	
	private Set<String> allowedBullets = Sets.newConcurrentHashSet();
	
	private Map<String, Integer> allowsRounds = new ConcurrentHashMap<String, Integer>();

	public Clip(ConfigurationSection config) {
		this.name = config.getName();
		
		this.tag = ChatColor.BLACK + "Clip: "
				+ Integer.toHexString(this.getName().hashCode() + this.getName().length());
		
		this.example = config.getItemStack("example");
		if (this.example == null) {
			throw new IllegalArgumentException("No inventory representation (section example) provided for this bullet, it cannot be instanced");
		} else {
			if (this.example.hasItemMeta()) {
				if (this.example.getItemMeta().hasLore()) {
					this.exampleLore = this.example.getItemMeta().getLore();
				}
				ItemMeta meta = this.example.getItemMeta();
				meta.setUnbreakable(true);
				this.example.setItemMeta(meta);
			}
		}
		
		if (config.contains("bullets")) {
			ConfigurationSection bullets = config.getConfigurationSection("bullets");
			for (String bulletName : bullets.getKeys(false)) {
				if (AddGun.getPlugin().getAmmo().getBullet(bulletName) == null) {
					AddGun.getPlugin().warning("Could not find bullet " + bulletName + " for clip " + this.name);
				} else {
					this.allowedBullets.add(bulletName);
					this.allowsRounds.put(bulletName, bullets.getInt(bulletName, 1));
				}
			}
		}
		
		if (allowedBullets.isEmpty()) {
			throw new IllegalArgumentException("No bullets defined for this clip? We cannot proceed");
		}
		
		Map<String, Object> clipData = new HashMap<String, Object>();
		
		clipData.put("rounds", Integer.valueOf(0));
		
		this.example = updateClipData(this.example, clipData);

	}
	
	/**
	 * Gets how many of each bullet is permitted for this clip.
	 * 
	 * @param bulletName the name of the bullet type
	 * @return the # allowed, or 0
	 */
	public int maxRounds(String bulletName) {
		return allowsRounds.containsKey(bulletName) ? allowsRounds.get(bulletName) : 0;
	}
	
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

		if (meta.hasLore() && meta.getLore().contains(tag))
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
		clipData.put("unid", UUID.randomUUID().toString());
		
		clip = updateClipLore(updateClipData(clip, clipData));

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
				int toLoad = Math.min(this.allowsRounds.get(bullet.getName()) - ((Integer) clipData.get("rounds")), i); // max vs. current + new
				clipData.put("rounds", ((Integer) clipData.get("rounds")) + Integer.valueOf(toLoad));
				i = i - toLoad;
				clip = updateClipLore(updateClipData(clip, clipData));
			} else { // different!
				bullets.setAmount(i);
				return new ItemStack[] {clip, bullets}; // cancel
			}
		} else { // not locked, go for it.
			clipData.put("ammo", bullet.getName());
			int toLoad = Math.min(this.allowsRounds.get(bullet.getName()), i);
			clipData.put("rounds", Integer.valueOf(toLoad));
			i = i - toLoad; // remainder
			clip = updateClipLore(updateClipData(clip, clipData));
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
				clip = updateClipLore(updateClipData(clip, clipData));
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
	
	/**
	 * Given a clip object, updates the lore to reflect the NBT
	 * 
	 * @param clip the clip to update
	 * @return the clip, with updated lore.
	 */
	public ItemStack updateClipLore(ItemStack clip) {
		ItemMeta meta = clip.getItemMeta();
		List<String> lore = meta.getLore();
		if (lore == null) {
			lore = new ArrayList<String>();
		} else {
			lore.clear();
		}
		lore.add(this.tag);
		if (this.exampleLore != null && !this.exampleLore.isEmpty()) {
			lore.addAll(this.exampleLore);
		}
		Map<String, Object> clipData = getClipData(clip);
		
		String ammo = clipData.containsKey("ammo") ? (String) clipData.get("ammo") : null;
		Integer rounds = (Integer) clipData.get("rounds");
		
		if (ammo != null) { // locked / has a bullet
			lore.add(ChatColor.GREEN + "Magazine of " + ChatColor.GRAY + ammo);
			if (rounds <= 0) {
				lore.add(ChatColor.RED + "  EMPTY " + ChatColor.DARK_AQUA + "(out of " + ChatColor.WHITE + this.allowsRounds.get(ammo) + ChatColor.DARK_AQUA + " max)");
			} else if (rounds == 1) {
				lore.add(ChatColor.GOLD + "  1 " + ChatColor.BLUE + "Round " + ChatColor.DARK_AQUA + "(out of " + ChatColor.WHITE + this.allowsRounds.get(ammo) + ChatColor.DARK_AQUA + " max)");
			} else {
				lore.add(ChatColor.GREEN + String.format("  %d ", rounds) + ChatColor.BLUE + "Rounds " + ChatColor.DARK_AQUA + "(out of " + ChatColor.WHITE + this.allowsRounds.get(ammo) + ChatColor.DARK_AQUA + " max)");
			}
		} else {
			lore.add(ChatColor.RED + "Magazine Empty");
			lore.add(ChatColor.GREEN + "Clip accepts bullets: ");
			for (String bull : this.allowedBullets) {
				lore.add(ChatColor.GREEN + " - " + ChatColor.GRAY + bull);
			}
		}

		meta.setLore(lore);
		clip.setItemMeta(meta);
		return clip;
	}
}
