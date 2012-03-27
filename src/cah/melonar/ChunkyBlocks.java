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
	private FileConfiguration myConfig = this.getConfig();
	private boolean useBlock;
	private int loadRadius;
	private int minHeight;
	private int maxHeight;
	private int maxChunks;
	private Material clMaterial;

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
		loadConfig();
	}

	private void loadConfig(){
		useBlock = myConfig.getBoolean("useBlock", true);
		loadRadius = myConfig.getInt("radius", 1);
		minHeight = myConfig.getInt("minHeight",54);
		maxHeight = myConfig.getInt("maxHeight",74);
		clMaterial = Material.getMaterial(myConfig.getInt("blockType", 19));
		maxChunks = myConfig.getInt("maxChunksPerUser",1);

	}

	@EventHandler
	public final void cbChunkUnload(ChunkUnloadEvent cuEvent){
		World currentWorld = cuEvent.getWorld();
		if(useBlock==true){
			for (int worldX = (-1 * loadRadius); worldX <= loadRadius; worldX++){
				for (int worldZ = (-1 * loadRadius); worldZ <= loadRadius; worldZ++){
					Chunk currentChunk = currentWorld.getChunkAt(cuEvent.getChunk().getX()+worldX, cuEvent.getChunk().getZ()+worldZ);
					for (int chunkX = 0; chunkX <= 15; chunkX++){
						for (int chunkZ = 0; chunkZ <= 15; chunkZ++){
							for (int chunkY = minHeight; chunkY <= maxHeight; chunkY++){
								if (currentChunk.getBlock(chunkX, chunkY, chunkZ).getType() == clMaterial){
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

}
