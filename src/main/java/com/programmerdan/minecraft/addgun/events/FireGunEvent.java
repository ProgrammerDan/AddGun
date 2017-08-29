package com.programmerdan.minecraft.addgun.events;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.HandlerList;

import com.programmerdan.minecraft.addgun.ammo.Bullet;
import com.programmerdan.minecraft.addgun.guns.StandardGun;

public class FireGunEvent extends GunEvent {
	private static final HandlerList handlers = new HandlerList();
	
	private final StandardGun gun;
	private final Bullet bullet;
	private final HumanEntity player;
	private double accuracy;
	
	public FireGunEvent(StandardGun gun, Bullet bullet, HumanEntity player, double accuracy) {
		super(false);
		this.gun = gun;
		this.bullet = bullet;
		this.player = player;
		this.accuracy = accuracy;
	}
	
	public StandardGun getGun() {
		return this.gun;
	}
	
	public Bullet getBullet() {
		return this.bullet;
	}
	
	public HumanEntity getHumanEntity() {
		return this.player;
	}
	
	public double getAccuracy() {
		return this.accuracy;
	}
	
	public void setAccuracy(double accuracy) {
		this.accuracy = (accuracy >= 0.0 ? accuracy <= 1.0 ? accuracy: 1.0 : 0.0);
	}
	
	@Override
	public HandlerList getHandlers() {
		return FireGunEvent.handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
