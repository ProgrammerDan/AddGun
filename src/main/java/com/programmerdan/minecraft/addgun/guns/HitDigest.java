package com.programmerdan.minecraft.addgun.guns;

import org.bukkit.Location;

public class HitDigest {
	public HitPart nearestHitPart;
	public Location hitLocation;
	
	public HitDigest(HitPart nearestHitPart, Location hitLocation) {
		this.nearestHitPart = nearestHitPart;
		this.hitLocation = hitLocation;
	}
}
