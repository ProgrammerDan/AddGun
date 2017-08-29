package com.programmerdan.minecraft.addgun.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public abstract class GunEvent extends Event implements Cancellable {

	public GunEvent(boolean async) {
		super(async);
	}
	
	private boolean cancel = false;

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	@Override
	public void setCancelled(boolean cancelled) {
		this.cancel = cancelled;
	}
}
