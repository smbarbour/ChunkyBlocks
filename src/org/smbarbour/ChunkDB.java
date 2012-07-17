package org.smbarbour;

import com.avaje.ebean.validation.Length;
import com.avaje.ebean.validation.NotEmpty;
import com.avaje.ebean.validation.NotNull;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

@Entity()
@Table(name="chunks")
public class ChunkDB {
	@Id
	private int rowid;
	
	@Length(max=16)
	@NotEmpty
	private String player;
	
	@Length(max=64)
	@NotEmpty
	private String tag;
	
	@NotEmpty
	private String world;
	
	@NotNull
	private int x;
	
	@NotNull
	private int z;

	public int getRowid() {
		return rowid;
	}

	public void setRowid(int rowid) {
		this.rowid = rowid;
	}

	public String getPlayer() {
		return player;
	}

	public void setPlayer(String player) {
		this.player = player;
	}

	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

	public String getWorld() {
		return world;
	}

	public void setWorld(String world) {
		this.world = world;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getZ() {
		return z;
	}

	public void setZ(int z) {
		this.z = z;
	}
	
	public void setChunk(Chunk keepChunk) {
		this.world = keepChunk.getWorld().getName();
		this.x = keepChunk.getX();
		this.z = keepChunk.getZ();
	}
	
	public Chunk getChunk() {
		World currentWorld = Bukkit.getServer().getWorld(this.world);
		return currentWorld.getChunkAt(this.x, this.z);
	}
	
}
