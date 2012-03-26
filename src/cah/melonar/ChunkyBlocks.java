package cah.melonar;

import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunkyBlocks extends JavaPlugin implements Listener {
	private final Logger logger = Logger.getLogger("Minecraft");
	private PluginManager pm;

	@Override
	public void onDisable(){
		PluginDescriptionFile pdfFile = this.getDescription();
		logger.info(pdfFile.getName() + " is now disabled.");
	}
	
	@Override
	public void onEnable(){
		pm = getServer().getPluginManager();
		pm.registerEvents(this, this);
		PluginDescriptionFile pdfFile = this.getDescription();
		logger.info(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled.");
		FileConfiguration myConfig = this.getConfig();
	}

	@EventHandler
	public final void cbChunkUnload(ChunkUnloadEvent cuEvent){
		World currentWorld = cuEvent.getWorld();
		for (int worldX = -3; worldX <= 3; worldX++){
			for (int worldZ = -3; worldZ <= 3; worldZ++){
				Chunk currentChunk = currentWorld.getChunkAt(cuEvent.getChunk().getX()+worldX, cuEvent.getChunk().getZ()+worldZ);
				for (int chunkX = 0; chunkX <= 15; chunkX++){
					for (int chunkZ = 0; chunkZ <= 15; chunkZ++){
						for (int chunkY = 54; chunkY <= 74; chunkY++){
							if (currentChunk.getBlock(chunkX, chunkY, chunkZ).getType() == Material.SPONGE){
								cuEvent.setCancelled(true);
								break;
							}
						}
					}
				}
			}
		}
	}

}
