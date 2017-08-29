package com.programmerdan.minecraft.addgun;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.minelink.ctplus.CombatTagPlus;

public class CTPTagUtility implements TagUtility {

	private CombatTagPlus ctp = (CombatTagPlus) Bukkit.getPluginManager().getPlugin("CombatTagPlus");
	@Override
	public void tag(LivingEntity tagger, LivingEntity taggee) {
		if (tagger instanceof Player) {
			if (taggee instanceof Player) {
				ctp.getTagManager().tag((Player) taggee, (Player) tagger);
			}
		} else if (taggee instanceof Player) {
			ctp.getTagManager().tag((Player) taggee, null);
		}
	}

}
