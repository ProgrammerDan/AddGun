package com.programmerdan.minecraft.addgun;

import org.bukkit.entity.LivingEntity;

public class NullTagUtility implements TagUtility {

	@Override
	public void tag(LivingEntity tagger, LivingEntity taggee) {
		return;
	}

}
