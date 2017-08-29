package com.programmerdan.minecraft.addgun.events;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.HandlerList;

import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.ammo.Clip;

public class LoadClipEvent extends GunEvent {
	private static final HandlerList handlers = new HandlerList();

	private final Clip clip;
	private final Bullet bullet;
	private final int rounds;
	private final HumanEntity player;
	
	public LoadClipEvent(Clip clip, Bullet bullet, int amount, HumanEntity player) {
		super(false);
		
		this.clip = clip;
		this.bullet = bullet;
		this.rounds = amount;
		this.player = player;
	}
	
	public Clip getClip() {
		return this.clip;
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
		return LoadClipEvent.handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}

}
