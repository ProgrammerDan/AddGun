package com.programmerdan.minecraft.addgun.events;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.HandlerList;

import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Magazine;

public class LoadMagazineEvent extends GunEvent {
	private static final HandlerList handlers = new HandlerList();

	private final Magazine magazine;
	private final Bullet bullet;
	private final int rounds;
	private final HumanEntity player;
	
	public LoadMagazineEvent(Magazine magazine, Bullet bullet, int amount, HumanEntity player) {
		super(false);
		
		this.magazine = magazine;
		this.bullet = bullet;
		this.rounds = amount;
		this.player = player;
	}
	
	public Magazine getMagazine() {
		return this.magazine;
	}
	
	public Bullet getBullet() {
		return this.bullet;
	}
	
	public int getRounds() {
		return this.rounds;
	}
	
	public HumanEntity getHumanEntity() {
		return this.player;
	}
	
	@Override
	public HandlerList getHandlers() {
		return LoadMagazineEvent.handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
