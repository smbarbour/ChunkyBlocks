package cah.melonar;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.ChatPaginator;
import org.bukkit.util.ChatPaginator.ChatPage;

import lib.PatPeter.SQLibrary.*;

public class ChunkyBlocks extends JavaPlugin implements Listener {
	private File pluginPath = new File("plugins" + File.pathSeparator + "ChunkyBlocks");
	private final Logger myLogger = Logger.getLogger("Minecraft");
	private PluginManager pm;
	private PluginDescriptionFile info;
	private FileConfiguration myConfig;
	private boolean debugMessages;
	private boolean useBlock;
	private int loadRange;
	private int minHeight;
	private int maxHeight;
	private int maxChunks;
	private Material clMaterial;
	private SQLite cbDatabase;

	@Override
	public void onDisable(){
		logMessage(Level.INFO, info.getName() + " is now disabled.");
	}

	@Override
	public void onEnable(){
		pm = getServer().getPluginManager();
		pm.registerEvents(this, this);
		info = this.getDescription();
		logMessage(Level.INFO, info.getName() + " version " + info.getVersion() + " is enabled.");
        if (!new File(pluginPath, "config.yml").isFile()) {
            this.saveDefaultConfig();
        }		
		loadConfig();
		cbDatabase = new SQLite(myLogger, "ChunkyBlocks", "ChunkyBlocks", pluginPath.getPath());
		loadDatabase();
	}

	private void loadDatabase() {
		cbDatabase.open();
		if(!cbDatabase.checkTable("Chunks")) {
			logMessage(Level.INFO, "Initializing table for first use.");
			String query = "CREATE TABLE chunks (rowid INT AUTO_INCREMENT PRIMARY_KEY, player VARCHAR(16), tag VARCHAR(32), world VARCHAR(64), x INT, z INT);";
			cbDatabase.createTable(query);
		}
	}

	private void loadConfig(){
		myConfig = this.getConfig();
		debugMessages = myConfig.getBoolean("debug",false);
		useBlock = myConfig.getBoolean("useBlock", true);
		loadRange = myConfig.getInt("radius", 1);
		minHeight = myConfig.getInt("minHeight",54);
		maxHeight = myConfig.getInt("maxHeight",74);
		clMaterial = Material.getMaterial(myConfig.getInt("blockType", 19));
		maxChunks = myConfig.getInt("maxChunksPerUser",1);
	}

	@EventHandler
	public final void cbMovement(PlayerMoveEvent pmEvent){
		Player currentPlayer = pmEvent.getPlayer();
		if(currentPlayer.hasPermission("chunkyblocks.notifyborder") && !pmEvent.getFrom().getChunk().equals(pmEvent.getTo().getChunk())){
			World currentWorld = currentPlayer.getWorld();
			for (int worldX = (-1 * loadRange); worldX <= loadRange; worldX++){
				for (int worldZ = (-1 * loadRange); worldZ <= loadRange; worldZ++){
					Chunk currentChunk = currentWorld.getChunkAt(pmEvent.getTo().getChunk().getX()+worldX, pmEvent.getTo().getChunk().getZ()+worldZ);
					String query = "SELECT player, tag from chunks where world = '" + currentChunk.getWorld().getName() + "' AND x = " + currentChunk.getX() + " AND z = " + currentChunk.getZ();
					ResultSet results = cbDatabase.query(query);
					try {
						if(results.next()){
							currentPlayer.sendMessage("This chunk is being kept loaded by " + results.getString("player") + " using a tag of " + results.getString("tag") + ".");
						}
						results.close();
					} catch (SQLException e) {
						logMessage(Level.WARNING, e.getMessage());
					}
				}
			}
		}
	}
	
	@EventHandler
	public final void cbChunkUnload(ChunkUnloadEvent cuEvent){
		World currentWorld = cuEvent.getWorld();
		for (int worldX = (-1 * loadRange); worldX <= loadRange; worldX++){
			for (int worldZ = (-1 * loadRange); worldZ <= loadRange; worldZ++){
				Chunk currentChunk = currentWorld.getChunkAt(cuEvent.getChunk().getX()+worldX, cuEvent.getChunk().getZ()+worldZ);
				String query = "SELECT player, tag from chunks where world = '" + currentChunk.getWorld().getName() + "' AND x = " + currentChunk.getX() + " AND z = " + currentChunk.getZ();
				ResultSet results = cbDatabase.query(query);
				try {
					if(results.next()){
						if(debugMessages){
							logMessage(Level.FINE, "Chunk (" + currentChunk.getWorld().toString() + ": " + worldX + ", " + worldZ + ") kept loaded by " + results.getString("player") + " with a tag of " + results.getString("tag"));
						}
					}
					results.close();
				} catch (SQLException e) {
					logMessage(Level.WARNING, e.getMessage());
				}
				if(useBlock==true){
					for (int chunkX = 0; chunkX <= 15; chunkX++){
						for (int chunkZ = 0; chunkZ <= 15; chunkZ++){
							for (int chunkY = minHeight; chunkY <= maxHeight; chunkY++){
								Block thisBlock = currentChunk.getBlock(chunkX, chunkY, chunkZ);
								if (thisBlock.getType() == clMaterial){
									if(debugMessages){
										logMessage(Level.FINE, "Chunk (" + currentChunk.getWorld().toString() + ": " + worldX + ", " + worldZ + ") kept loaded by block at (" + thisBlock.getLocation().toString() + ")");
									}
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

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Player player = null;
		if(sender instanceof Player) {
			player = (Player) sender;
		} else {
			sender.sendMessage("ChunkyBlocks commands are not available from the console.");
		}
		String commandName = cmd.getName().toLowerCase();
		if (commandName.equals("setchunk")){
			String tag = "";
			if(args.length > 0){
				tag = args[0];
			}
			if(tag.isEmpty()) {
				tag = "default";
			}
			if(!player.hasPermission("chunkyblocks.set")){
				player.sendMessage("You do not have permission to use this command.");
				this.getServer().broadcast("ChunkyBlocks: " + player.getName() + " tried to use /setchunk but does not have permission", Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
				return false;
			}
			String query = "SELECT sq1.owned, world, x, z FROM chunks, (SELECT count(*) AS owned FROM chunks WHERE player = '" + player.getName() + "') sq1 WHERE player = '" + player.getName() + "' AND tag = '" + tag + "';";
			ResultSet results = cbDatabase.query(query);
			Chunk here = player.getLocation().getChunk();
			try {
				if(results.next()){
					if(results.getString("world").isEmpty()){
						if(results.getInt("owned") < maxChunks){
							String insert = "INSERT INTO chunks(player, tag, world, x, z) VALUES('" + player.getName() + "','" + tag + "','" + here.getWorld().getName() + "'," + here.getX() + "," + here.getZ() + ");";
							cbDatabase.query(insert);
							player.sendMessage("Location (" + tag + ") added to chunkloading list.");
							results.close();
							return true;
						} else {
							player.sendMessage("You have already reached your chunkloading limit.");
							results.close();
							return false;
						}
					} else {
						String insert = "UPDATE chunks SET world = '" + here.getWorld().getName() + "', x = " + here.getX() + ", z = " + here.getZ() + " WHERE player = '" + player.getName() + "' AND tag = '" + tag + "';";
						cbDatabase.query(insert);
						player.sendMessage("Location (" + tag + ") added to chunkloading list.");
						results.close();
						return true;
					}
				} else {
					String update = "INSERT INTO chunks(player, tag, world, x, z) VALUES('" + player.getName() + "','" + tag + "','" + here.getWorld().getName() + "'," + here.getX() + "," + here.getZ() + ");";
					cbDatabase.query(update);
					player.sendMessage("Location (" + tag + ") updated in chunkloading list.");
					results.close();
					return true;
				}
			} catch (SQLException e) {
				logMessage(Level.WARNING, e.getMessage());
			}
		}
		if (commandName.equals("removechunk")){
			String tag = "";
			if(args.length > 0){
				tag = args[0];
			}

			if(tag.isEmpty()) {
				tag = "default";
			}
			if(!player.hasPermission("chunkyblocks.remove")){
				player.sendMessage("You do not have permission to use this command.");
				this.getServer().broadcast("ChunkyBlocks: " + player.getName() + " tried to use /removechunk but does not have permission", Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
				return false;
			}
			String query = "SELECT rowid FROM chunks WHERE player = '" + player.getName() + "' AND tag = '" + tag + ";";
			ResultSet results = cbDatabase.query(query);
			try {
				if(!results.next()){
					player.sendMessage("A chunk with the label of " + tag + " was not found for your username.");
					results.close();
					return false;
				} else {
					String delete = "DELETE FROM chunks WHERE rowid = " + results.getInt("rowid") + ";";
					cbDatabase.query(delete);
					results.close();
					return true;
				}
			} catch (SQLException e) {
				logMessage(Level.WARNING, e.getMessage());
			}
		}
		if (commandName.equals("mychunks")){
			String page = "";
			if(args.length > 0){
				page = args[0];
			}
			if(page.isEmpty()){
				page = "1";
			}
			if(!player.hasPermission("chunkyblocks.list")){
				player.sendMessage("You do not have permission to use this command.");
				this.getServer().broadcast("ChunkyBlocks: " + player.getName() + " tried to use /mychunks but does not have permission", Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
				return false;
			}
			String query = "SELECT tag, world, x, z FROM chunks WHERE player = '" + player.getName() + "';";
			ResultSet results = cbDatabase.query(query);
			try {
				if(!results.next()){
					player.sendMessage("No chunks are registered to your name");
					results.close();
					return true;
				} else {
					String lines = "";
					while(!results.isAfterLast()){
						lines = lines + results.getString("tag") + " (" + results.getString("world") + ": " + results.getInt("x") + ", " + results.getInt("z") + ")\n";
						results.next();
					}
					ChatPage chat = ChatPaginator.paginate(lines, Integer.parseInt(page));
					player.sendMessage("Your chunks: " + chat.getPageNumber() + "/" + chat.getTotalPages());
					player.sendMessage(chat.getLines());
					results.close();
					return true;
				}
			} catch (SQLException e) {
				logMessage(Level.WARNING, e.getMessage());
			}
		}
		if (commandName.equals("listchunks")){
			String playerName = "";
			int page = 0;
			if(!player.hasPermission("chunkyblocks.adminlist")){
				player.sendMessage("You do not have permission to use this command.");
				return false;
			}
			if(args.length == 0){
				playerName = "-";
				page = 1;
			} else {
				try {
					page = Integer.parseInt(args[0]);
					playerName = "-";
				} catch (NumberFormatException nfe) {
					playerName = args[0];
					if(args.length == 1){
						page = 1;
					} else {
						try {
							page = Integer.parseInt(args[1]);
						} catch (NumberFormatException nfe2) {
							player.sendMessage("Expected page number... found '" + args[1] + "' instead.");
							return false;
						}
					}
				}
			}
			String query = "";
			if(playerName.equals("-")) {
				query = "SELECT player, tag, world, x, z FROM chunks;";
			} else {
				query = "SELECT player, tag, world, x, z FROM chunks WHERE player = '" + playerName + "';";
			}
			ResultSet results = cbDatabase.query(query);
			try {
				if(playerName.equals("-")) { playerName = "anyone"; }
				if(!results.next()){
					player.sendMessage("No chunks are registered to " + playerName);
					results.close();
					return true;
				} else {
					String lines = "";
					while(!results.isAfterLast()){
						lines = lines + results.getString("player") + " " + results.getString("tag") + " (" + results.getString("world") + ": " + results.getInt("x") + ", " + results.getInt("z") + ")\n";
						results.next();
					}
					ChatPage chat = ChatPaginator.paginate(lines, page);
					player.sendMessage("Chunks: " + chat.getPageNumber() + "/" + chat.getTotalPages());
					player.sendMessage(chat.getLines());
					results.close();
					return true;
				}
			} catch (SQLException e) {
				logMessage(Level.WARNING, e.getMessage());
			}
		}
		if (commandName.equals("telechunk")){	
			if(!player.hasPermission("chunkyblocks.adminteleport")){
				player.sendMessage("You do not have permission to use this command.");
				return true;
			}
			String playerName = "";
			if(args.length > 0){
				playerName = args[0];
			} else {
				player.sendMessage("You must specify a player's name!");
				return false;
			}
			String tag = "";
			if(args.length > 1){
				tag = args[1];
			} else {
				tag = "default";
			}
			String query = "SELECT world, x, z FROM chunks WHERE player = '" + playerName + "' and tag = '" + tag + "';";
			ResultSet results = cbDatabase.query(query);
			try {
				if(!results.next()){
					player.sendMessage("No chunk found for Player: " + playerName + ", with Tag: " + tag);
					results.close();
					return true;
				} else {
					Chunk destChunk = this.getServer().getWorld(results.getString("world")).getChunkAt(results.getInt("x"),results.getInt("z"));
					Location teleTo = null;
					for(int y=255;y>0;y--){
						if(!destChunk.getBlock(7, y-1, 7).isEmpty()){
							teleTo = destChunk.getBlock(7, y, 7).getLocation();
						}
					}
					if(teleTo == null){
						player.sendMessage("There appears to be a hole in the world at the teleport location!");
						results.close();
						return true;
					} else {
						player.teleport(teleTo, TeleportCause.COMMAND);
					}
				}
			} catch (SQLException e) {
				logMessage(Level.WARNING, e.getMessage());
			}
		}
		if (commandName.equals("removechunkadmin")){
			if(!player.hasPermission("chunkyblocks.adminremove")){
				player.sendMessage("You do not have permission to use this command.");
				return true;
			}
			String playerName = "";
			if(args.length > 0){
				playerName = args[0];
			} else {
				player.sendMessage("You must specify a player name.");
				return false;
			}
			String tag = "";
			if(args.length > 1){
				tag = args[1];
			} else {
				tag = "default";
			}
			String query = "SELECT rowid FROM chunks WHERE player = '" + playerName + "' AND tag = '" + tag + ";";
			ResultSet results = cbDatabase.query(query);
			try {
				if(!results.next()){
					player.sendMessage("A chunk with the label of " + tag + " was not found for " + playerName + ".");
					results.close();
					return true;
				} else {
					String delete = "DELETE FROM chunks WHERE rowid = " + results.getInt("rowid") + ";";
					cbDatabase.query(delete);
					results.close();
					return true;
				}
			} catch (SQLException e) {
				logMessage(Level.WARNING, e.getMessage());
			}
		}
		return false;
	}

	private void logMessage(Level logLevel, String message) {
		myLogger.log(logLevel, "[" + info.getName() + "]: " + message);
	}
}
