package com.programmerdan.minecraft.addgun.guns;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import net.minecraft.server.v1_12_R1.PacketPlayOutPosition;
import net.minecraft.server.v1_12_R1.PacketPlayOutPosition.EnumPlayerTeleportFlags;

/**
 * Instance this to interpolate knockback / returns for gun use.
 * 
 * @author ProgrammerDan
 *
 */
public class Animation implements Runnable{
	
	public static double FRAME_DELAY = 34.0d;
	
	private static Set<EnumPlayerTeleportFlags> flags = new HashSet<>(Arrays.asList(EnumPlayerTeleportFlags.X, EnumPlayerTeleportFlags.Y, EnumPlayerTeleportFlags.Z,
			EnumPlayerTeleportFlags.X_ROT, EnumPlayerTeleportFlags.Y_ROT));
	
	private Player player; // against pro-forma to store this ... doing it at my own risk.
	
	private Vector begin;
	private Vector peak;
	private Vector end;
	
	private Vector[] steps;
	
	private long beginToPeak;
	private long peakToEnd;
	
	private long start;
	private long done;
	
	public Animation(Player target, Vector begin, long beginToPeak, Vector peak, long peakToEnd, Vector end) {
		this.player = target;
		this.begin = begin;
		this.beginToPeak = beginToPeak;
		this.peak = peak;
		this.peakToEnd = peakToEnd;
		this.end = end;
		computeSteps();
	}
	
	private void computeSteps() {
		int beginIndices = (int) Math.ceil( beginToPeak / FRAME_DELAY);
		Vector perStepUp = peak.clone().subtract(begin).multiply(1d / beginIndices);
		int endIndices = (int) Math.ceil( peakToEnd / FRAME_DELAY);
		Vector perStepDown = end.clone().subtract(peak).multiply(1d / endIndices);
		int indices = beginIndices + endIndices -1;
		//AddGun.getPlugin().debug("begin {0}, indices {1}, up step {2}\npeak {3}, down step {4}, down indices {5}\n end {6}",
		//		begin, beginIndices, perStepUp, peak, perStepDown, endIndices, end);
		steps = new Vector[indices];
		for (int i = 0; i < indices; i++) {
			if (i == 0) {
				steps[i] = perStepUp; //begin;
			} else if (i < beginIndices) { // first half
				steps[i] = perStepUp; //steps[i - 1].clone().add(perStepUp);
			} else if (i == beginIndices) {
				steps[i] = new Vector(); // peak;
			} else if (i == indices - 1) {
				steps[i] = perStepDown; // end;
			} else if (i > beginIndices) {// second half
				steps[i] = perStepDown; //steps[i - 1].clone().add(perStepDown);
			}
			//AddGun.getPlugin().debug("frame {0} vector {1}", i, steps[i]);
		}
	}
	
	public Vector start() {
		this.start = System.currentTimeMillis();
		this.done = this.start + this.beginToPeak + this.peakToEnd;
		return steps[0]; //begin;
	}
	
	public Vector step() {
		long now = System.currentTimeMillis();
		if (now >= done) return steps[steps.length - 1];//this.end;
		int step = (int) Math.round((now - start) / FRAME_DELAY);
		if (step > steps.length) return steps[steps.length - 1];//this.end;
		return steps[step];
	}
	
	public boolean isDone() {
		long now = System.currentTimeMillis();
		if (now >= done) return true;
		return false;
	}
	
	public void run() {
		Vector headMove = null;
		if (this.start == 0l) {
			// start
			headMove = start();
		} else if (!isDone()) {
			// continue;
			headMove = step();
		} else { // done
			throw new RuntimeException("Done this animation");
		}
		
		if (player != null) {
			Location bloc = player.getEyeLocation().clone();
			Location loc = player.getEyeLocation().clone().setDirection(bloc.getDirection().clone().add(headMove));
			((CraftPlayer)player).getHandle().playerConnection.sendPacket(new PacketPlayOutPosition(0.0, 0.0, 0.0, loc.getYaw() - bloc.getYaw(), loc.getPitch() - bloc.getPitch(), flags, 99));
		}
	}
}
