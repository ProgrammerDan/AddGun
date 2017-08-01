package com.programmerdan.minecraft.addgun.guns;

import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import net.minecraft.server.v1_12_R1.AxisAlignedBB;
import net.minecraft.server.v1_12_R1.MovingObjectPosition;
import net.minecraft.server.v1_12_R1.Vec3D;

/**
 * Collection of static utilities to help navigate the world of guns.
 * 
 * @author ProgrammerDan
 *
 */
public class Utilities {

	
	/**
	 * Using a Minecraft core utility, this determines where against a hitbox impact occurred, if at all.
	 * 
	 * See hitInformation() for more details.
	 * 
	 * @param origin The starting point of travel
	 * @param velocity The travel velocity
	 * @param entity The entity to test hit box against
	 * @return Location of impact, or the _origin_ if no hit detected.
	 */
	public static Location approximateHitBoxLocation(Location origin, Vector velocity, Entity entity) {
		
		MovingObjectPosition hit = hitInformation(origin, velocity, entity);

		if (hit == null) {
			return origin;
		} else {
			return new Location(origin.getWorld(), hit.pos.x, hit.pos.y, hit.pos.z, origin.getYaw(), origin.getPitch());
		}
	}
	
	public static HitDigest detailedHitBoxLocation(Location origin, Vector velocity, Entity entity) {
		MovingObjectPosition hit = hitInformation(origin, velocity, entity);
		
		if (hit == null) {
			return new HitDigest(HitPart.MISS, origin);
		} else {
			HitPart part = HitPart.MISS;
			net.minecraft.server.v1_12_R1.Entity hitentity = ((CraftEntity) entity).getHandle();
			//double locX = hitentity.locX;
			double locY = hitentity.locY;
			//double locZ = hitentity.locZ;
			//double hitX = hit.pos.x;
			double hitY = hit.pos.y;
			//double hitZ = hit.pos.z;
			double height = entity.getHeight();
			double head = hitentity.getHeadHeight();
			double midsection = (height - head > 0) ? (height - head) / 2 : height / 2;
			double legs = (height - head > 0) ? (height - head) / 5 : height / 5;

			if (hitY < locY + height && hitY >= locY + head) {
				part = HitPart.HEAD;
			} else if (hitY < locY + head && hitY >= locY + midsection) {
				part = HitPart.BODY;
			} else if (hitY < locY + midsection && hitY >= locY + legs) {
				part = HitPart.LEGS;
			} else if (hitY < locY + legs && hitY >= locY) {
				part = HitPart.FEET;
			}
			
			return new HitDigest(part, new Location(origin.getWorld(), hit.pos.x, hit.pos.y, hit.pos.z, origin.getYaw(), origin.getPitch()));
		}
	}
	
	/**
	 * Soft wrapper to convert Bukkit objects on vector and impact into a MovingObjectPosition representing
	 * if the passed entity was intersected by a vector "velocity" with origin of "origin". This assumes a 
	 * no-size projectile. Use other techniques for entity on entity boxtests.
	 * 
	 * @param origin Starting point of travel
	 * @param velocity Travel path
	 * @param entity The entity to test intersection against.
	 * @return a MovingObjectPosition with intersection details, or null.
	 */
	public static MovingObjectPosition hitInformation(Location origin, Vector velocity, Entity entity) {
		
		// we have the bullet's last tick location, its velocity, and the barycenter of the object it hit, and that
		// object's hitbox. We also know for sure that the object was intersected with.
		AxisAlignedBB boundingBox = ((CraftEntity) entity).getHandle().getBoundingBox();
		Vec3D origLocation = new Vec3D(origin.getX(), origin.getY(), origin.getZ());
		Vec3D origVector = new Vec3D(origin.getX() + velocity.getX(), origin.getY() + velocity.getY(), origin.getZ() + velocity.getZ());
		
		return boundingBox.b(origLocation, origVector);
	}


	/**
	 * Originally adapted from 1.8 computation TODO: doublecheck still valid.
	 * 
	 * @param p The player whose current XP to compute
	 * @return the number of XP in hotbar right now.
	 */
	public static int computeTotalXP(Player p) {
        float cLevel = (float) p.getLevel();
        float progress = p.getExp();
        float a = 1f, b = 6f, c = 0f, x = 2f, y = 7f;
        if (cLevel > 16 && cLevel <= 31) {
                a = 2.5f; b = -40.5f; c = 360f; x = 5f; y = -38f;
        } else if (cLevel >= 32) {
                a = 4.5f; b = -162.5f; c = 2220f; x = 9f; y = -158f;
        }
        return (int) Math.floor(a * cLevel * cLevel + b * cLevel + c + progress * (x * cLevel + y));
	}

}
