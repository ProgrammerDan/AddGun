package com.programmerdan.minecraft.addgun.guns;

import java.util.Queue;
import java.util.concurrent.atomic.DoubleAccumulator;

/**
 * Tracks the shots that have been fired and accumulates their aim disturbing impact.
 * 
 * @author ProgrammerDan
 */
public class ShotTracker implements Runnable {
	private Queue<Shot> shotQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
	
	private DoubleAccumulator offset = new DoubleAccumulator((a,b) -> {return a + b;}, 0.0);
	
	public void addShot(double aimOffset, double decay, int lifetime) {
		shotQueue.offer(new Shot(aimOffset, decay, lifetime));
	}
	
	public void run() {
		DoubleAccumulator offset = new DoubleAccumulator((a,b) -> {return a + b;}, 0.0);

		shotQueue.removeIf(s -> {
			offset.accumulate(s.decay());
			return s.ticksLeft == 0;
		});
		
		this.offset = offset;
		
		if (shotQueue.isEmpty()) {
			throw new RuntimeException("All done for now for this player");
		}
	}
	
	public double getAimOffset() {
		return offset.get();
	}
	
	private class Shot {
		public double aimOffset;
		public double decay;
		public int ticksLeft;
		
		public Shot(double aimOffset, double decay, int ticksLeft) {
			this.aimOffset = aimOffset;
			this.decay = decay;
			this.ticksLeft = ticksLeft;
		}
		
		public double decay() {
			if (ticksLeft > 0) {
				this.aimOffset -= this.decay;
				this.ticksLeft --;
			} else {
				this.aimOffset = 0.0d;
			}
			return this.aimOffset;
		}
	}
}
