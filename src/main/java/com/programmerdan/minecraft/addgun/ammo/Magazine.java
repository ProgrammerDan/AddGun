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

import static com.programmerdan.minecraft.addgun.guns.Utilities.getMagazineData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.updateMagazineData;
import static com.programmerdan.minecraft.addgun.guns.Utilities.updateGunData;

/**
 * Wrapper class for "magazines" which are bullet holders. Not all guns need support magazines. Some only support magazines.
 * 
 * @author ProgrammerDan
 */
public class Magazine {
	
	private String name;
	
	private String tag;
	
	private ItemStack example;
	
	private List<String> exampleLore;
	
	private Set<String> allowedBullets = Sets.newConcurrentHashSet();
	
	private Map<String, Integer> allowsRounds = new ConcurrentHashMap<String, Integer>();

	public Magazine(ConfigurationSection config) {
		this.name = config.getName();
		
		this.tag = ChatColor.BLACK + "Magazine: "
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
					AddGun.getPlugin().warning("Could not find bullet " + bulletName + " for magazine " + this.name);
				} else {
					this.allowedBullets.add(bulletName);
					this.allowsRounds.put(bulletName, bullets.getInt(bulletName, 1));
				}
			}
		}
		
		if (allowedBullets.isEmpty()) {
			throw new IllegalArgumentException("No bullets defined for this magazine? We cannot proceed");
		}
		
		Map<String, Object> magazineData = new HashMap<String, Object>();
		
		magazineData.put("rounds", Integer.valueOf(0));
		
		this.example = updateMagazineData(this.example, magazineData);

	}
	
	/**
	 * Gets how many of each bullet is permitted for this magazine.
	 * 
	 * @param bulletName the name of the bullet type
	 * @return the # allowed, or 0
	 */
	public int maxRounds(String bulletName) {
		return allowsRounds.containsKey(bulletName) ? allowsRounds.get(bulletName) : 0;
	}
	
	/**
	 * Lightweight check to see if the given itemstack is a magazine
	 * @param toCheck
	 * @return
	 */
	public boolean isMagazine(ItemStack toCheck) {
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
	 * Creates a "fresh" magazine, using the example itemstack, locks it to the "bullet" type if any, and fills it with rounds.
	 * @param bullet The bullet type, or null
	 * @param i number of rounds. If bullet type is null, this is ignored.
	 * @return a new Magazine object for inventories.
	 */
	public ItemStack getMagazineItem(Bullet bullet, int i) {
		ItemStack magazine = example.clone();
		Map<String, Object> magazineData = new HashMap<String, Object>();
		if (bullet != null) {
			magazineData.put("ammo", bullet.getName());
			magazineData.put("rounds", Integer.valueOf(i));
		} else {
			magazineData.put("ammo", null);
			magazineData.put("rounds", Integer.valueOf(0));
		}
		magazineData.put("unid", UUID.randomUUID().toString());
		
		magazine = updateMagazineLore(updateMagazineData(magazine, magazineData));

		return magazine;
	}
	
	/**
	 * Tries to put more bullets into this magazine. 
	 * 
	 * Fails if the magazine has bullets of a different type or is locked to bullets of a different type.
	 * 
	 * @param magazine
	 * @param bullet
	 * @param i
	 * @return
	 */
	public ItemStack[] loadMagazine(ItemStack magazine, Bullet bullet, int i) {
		ItemStack bullets = bullet.getBulletItem();
		if (!this.allowedBullets.contains(bullet.getName())) {
			bullets.setAmount(i);
			return new ItemStack[] {magazine, bullets};
		}
		
		Map<String, Object> magazineData = getMagazineData(magazine);
		if (magazineData.containsKey("ammo")) { // locked to an ammo
			if (bullet.getName().equals((String) magazineData.get("ammo"))) { // same!
				int toLoad = Math.min(this.allowsRounds.get(bullet.getName()) - ((Integer) magazineData.get("rounds")), i); // max vs. current + new
				magazineData.put("rounds", ((Integer) magazineData.get("rounds")) + Integer.valueOf(toLoad));
				i = i - toLoad;
				magazine = updateMagazineLore(updateMagazineData(magazine, magazineData));
			} else { // different!
				bullets.setAmount(i);
				return new ItemStack[] {magazine, bullets}; // cancel
			}
		} else { // not locked, go for it.
			magazineData.put("ammo", bullet.getName());
			int toLoad = Math.min(this.allowsRounds.get(bullet.getName()), i);
			magazineData.put("rounds", Integer.valueOf(toLoad));
			i = i - toLoad; // remainder
			magazine = updateMagazineLore(updateMagazineData(magazine, magazineData));
		}
		if (i > 0)  {
			bullets.setAmount(i);
		} else {
			bullets = null;
		}
		
		return new ItemStack[] {magazine, bullets}; // ok
	}
	
	/**
	 * Removes the bullets from this magazine (if possible)
	 * 
	 * Fails if this magazine has no bullets.
	 * @param magazine The magazine to unload
	 * @return a 2 element array; element 0 is the magazine, modified, element 1 is the bullets retrieved if any
	 */
	public ItemStack[] unloadMagazine(ItemStack magazine) {
		ItemStack bullets = null;
		Map<String, Object> magazineData = getMagazineData(magazine);
		if (magazineData.containsKey("ammo")) { // locked to an ammo, might have ammo
			Bullet bullet = AddGun.getPlugin().getAmmo().getBullet((String) magazineData.get("ammo"));
			if (bullet != null) { // ok.
				bullets = bullet.getBulletItem();
				int unload = (Integer) magazineData.get("rounds");
				if (unload > 0) {
					bullets.setAmount(unload);
				} else {
					bullets = null;
				}
				magazineData.put("ammo", null);
				magazineData.put("rounds", 0);
				magazine = updateMagazineLore(updateMagazineData(magazine, magazineData));
			} else { // otherwise it's... invalid ammo
				// unable to unload! bad magazine?!
				// TODO messaging
			}
		} // otherwise its already empty
		
		return new ItemStack[] {magazine, bullets}; // ok
	}

	/**
	 * Returns the bullet type of this magazine, or null if can't figure it out.
	 * 
	 * @param magazine the Magazine itemStack
	 * @return the Bullet type held by this magazine.
	 */
	public Bullet getBulletType(ItemStack magazine) {
		Map<String, Object> magazineData = getMagazineData(magazine);
		if (magazineData.containsKey("ammo")) { // locked to an ammo, might have ammo
			return AddGun.getPlugin().getAmmo().getBullet((String) magazineData.get("ammo"));
		}
		
		return null;
	}
	
	/**
	 * Returns the rounds in this magazine, or null if can't figure it out.
	 * 
	 * @param magazine the Magazine itemStack
	 * @return the number of rounds in the magazine
	 */
	public Integer getRounds(ItemStack magazine) {
		Map<String, Object> magazineData = getMagazineData(magazine);
		if (magazineData.containsKey("rounds")) { 
			return (Integer) magazineData.get("rounds");
		}
		
		return null;
	}
	
	/**
	 * Given a magazine object, updates the lore to reflect the NBT
	 * 
	 * @param magazine the magazine to update
	 * @return the magazine, with updated lore.
	 */
	public ItemStack updateMagazineLore(ItemStack magazine) {
		ItemMeta meta = magazine.getItemMeta();
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
		Map<String, Object> magazineData = getMagazineData(magazine);
		
		String ammo = magazineData.containsKey("ammo") ? (String) magazineData.get("ammo") : null;
		Integer rounds = (Integer) magazineData.get("rounds");
		
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
			lore.add(ChatColor.GREEN + "Accepts bullets: ");
			for (String bull : this.allowedBullets) {
				lore.add(ChatColor.GREEN + " - " + ChatColor.GRAY + bull);
			}
		}

		meta.setLore(lore);
		magazine.setItemMeta(meta);
		return magazine;
	}
}
