package com.programmerdan.minecraft.addgun.commands;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.programmerdan.minecraft.addgun.AddGun;

import vg.civcraft.mc.civmodcore.inventorygui.Clickable;
import vg.civcraft.mc.civmodcore.inventorygui.ClickableInventory;


/**
 * Portions adapted from EasyHelpOp
 * 
 * @author ProgrammerDan
 *
 */
public abstract class ViewMenu {

	private static final int pageSize = 27;
	protected AddGun plugin = AddGun.getPlugin();
	private Player player;
	private int pageNum = 1;
	private int pageNumMax;
	private List<String> identifiers;
	private String titlePrefix;

	public ViewMenu(Player player, List<String> identifiers, String titlePrefix) {
		this.player = player;
		this.identifiers = identifiers;
		this.titlePrefix = titlePrefix;
		
		this.pageNumMax = Math.max( (int) Math.ceil(identifiers.size() / pageSize), 1);
		
		openMenu();
	}
	
	public abstract ItemStack getRepresentation(String identifier);
	public abstract String getCommand(String identifier);

	public void openMenu() {
		ClickableInventory.forceCloseInventory(player);
		
		ClickableInventory gunMenu = new ClickableInventory(pageSize+9,String.format("%s menu - Page #%d", titlePrefix, pageNum));
		
		for (int i = (pageNum * pageSize) - pageSize; i < pageNum*pageSize && i < identifiers.size(); i++) {
			String gunName = identifiers.get(i);
			if (gunName == null) continue;

			ItemStack rep = getRepresentation(gunName);
			if (rep == null) continue;
			
			Clickable clickGun = new Clickable(rep) {
	
				@Override
				public void clicked(Player p) {
					p.performCommand(getCommand(gunName));
					
					ClickableInventory.forceCloseInventory(p);
				}
			};
			
			gunMenu.addSlot(clickGun);
		}
		
		ItemStack backItem = createItem(Material.ARROW, "Back Page", null);
		ItemStack forwardItem = createItem(Material.ARROW, "Forward Page", null);
		ItemStack closeItem = createItem(Material.BARRIER, "Close", null);
		
		Clickable backClick = new Clickable(backItem){
			@Override
			public void clicked(Player p){
				setPageNum(pageNum-1);
				openMenu();
			}
		};
		
		Clickable forwardClick = new Clickable(forwardItem){
			@Override
			public void clicked(Player p){
				setPageNum(pageNum+1);
				openMenu();
			}
		};
		
		Clickable closeClick = new Clickable(closeItem) {
			@Override
			public void clicked(Player p) {
				ClickableInventory.forceCloseInventory(player);
			}
		};
		
		if (pageNum > 1)
			gunMenu.setSlot(backClick, pageSize);
		gunMenu.setSlot(closeClick, pageSize+4);
		if (pageNum < pageNumMax)
			gunMenu.setSlot(forwardClick, pageSize+8);
		
		gunMenu.showInventory(player);
	}

	private ItemStack createItem(Material material, String title, List<String> lore) {
		ItemStack item = new ItemStack(material);
		
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(title);
		
		if(lore != null)
			meta.setLore(lore);
			
		item.setItemMeta(meta);
		
		return item;
	}

	public void setPageNum(int num) {
		pageNum = Math.max(1, Math.min(pageNumMax, num));
	}

}