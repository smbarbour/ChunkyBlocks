package org.smbarbour;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.PersistenceException;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.ChatPaginator;
import org.bukkit.util.ChatPaginator.ChatPage;

import com.avaje.ebean.EbeanServer;

public class ChunkyBlocks extends JavaPlugin implements Listener {
	private File pluginPath = this.getDataFolder();
	private final Logger myLogger = Logger.getLogger("Minecraft");
	private PluginManager pm;
	private PluginDescriptionFile info;
	private FileConfiguration myConfig;
	private boolean debugMessages;
	private boolean useBlock;
	private boolean onlyOnlinePlayers;
	private int loadRange;
	private int maxChunks;
	private Material clMaterial;
	private EbeanServer database;
	private List<String> suppressed = new ArrayList<String>();

	@Override
	public List<Class<?>> getDatabaseClasses() {
		List<Class<?>> list = new ArrayList<Class<?>>();
		list.add(ChunkDB.class);
		return list;
	}
	
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
		this.database = this.getDatabase();
		loadDatabase();
		loadChunks();
	}

	private void loadChunks() {
		List<ChunkDB> results = database.find(ChunkDB.class).findList();
		Iterator<ChunkDB> it = results.iterator();
		while(it.hasNext()) {
			ChunkDB entry = it.next();
			World affectedWorld = this.getServer().getWorld(entry.getWorld());
			for (int worldX = (-1 * loadRange); worldX <= loadRange; worldX++){
				for (int worldZ = (-1 * loadRange); worldZ <= loadRange; worldZ++){
					affectedWorld.getChunkAt((entry.getX() + worldX), (entry.getZ() + worldZ)).load();
				}
			}
		}
	}

	private void loadChunks(Player user) {
		List<ChunkDB> results = database.find(ChunkDB.class).where().ieq("player", user.getName()).findList();
		Iterator<ChunkDB> it = results.iterator();
		while(it.hasNext()) {
			ChunkDB entry = it.next();
			World affectedWorld = this.getServer().getWorld(entry.getWorld());
			for (int worldX = (-1 * loadRange); worldX <= loadRange; worldX++){
				for (int worldZ = (-1 * loadRange); worldZ <= loadRange; worldZ++){
					affectedWorld.getChunkAt((entry.getX() + worldX), (entry.getZ() + worldZ)).load();
				}
			}
		}		
	}

	private void unloadChunks(Player user) {
		List<ChunkDB> results = database.find(ChunkDB.class).where().ieq("player", user.getName()).findList();
		Iterator<ChunkDB> it = results.iterator();
		while(it.hasNext()) {
			ChunkDB entry = it.next();
			World affectedWorld = this.getServer().getWorld(entry.getWorld());
			for (int worldX = (-1 * loadRange); worldX <= loadRange; worldX++){
				for (int worldZ = (-1 * loadRange); worldZ <= loadRange; worldZ++){
					affectedWorld.getChunkAt((entry.getX() + worldX), (entry.getZ() + worldZ)).unload(true, true);
				}
			}
		}				
	}
	
	private void loadDatabase() {
		try {
			database.find(ChunkDB.class).findRowCount();
		} catch (PersistenceException pe) {
			logMessage(Level.INFO, "Installing database on first use.");
			installDDL();
		}
	}

	private void loadConfig(){
		myConfig = this.getConfig();
		debugMessages = myConfig.getBoolean("debug",false);
		useBlock = myConfig.getBoolean("useBlock", true);
		loadRange = myConfig.getInt("radius", 1);
		clMaterial = Material.getMaterial(myConfig.getInt("blockType", 19));
		maxChunks = myConfig.getInt("maxChunksPerUser",1);
		onlyOnlinePlayers = myConfig.getBoolean("onlyOnlinePlayers",false);
	}

	@EventHandler
	public final void cbPlayerQuit(PlayerQuitEvent pqeEvent) {
		if (onlyOnlinePlayers) {
			unloadChunks(pqeEvent.getPlayer());
		}
	}
	
	@EventHandler
	public final void cbPlayerLogin(PlayerLoginEvent pleEvent) {
		if (onlyOnlinePlayers) {
			loadChunks(pleEvent.getPlayer());
		}
	}
	
	@EventHandler
	public final void cbMovement(PlayerMoveEvent pmEvent){
    	if(pmEvent.getFrom().distance(pmEvent.getTo()) == 0)
    	{
    		return;
    	}
		Player currentPlayer = pmEvent.getPlayer();
		if (suppressed.contains(currentPlayer.getName())){
			return;
		}
		if(currentPlayer.hasPermission("chunkyblocks.notifyborder") && !pmEvent.getFrom().getChunk().equals(pmEvent.getTo().getChunk())){
			World currentWorld = currentPlayer.getWorld();
			Chunk currentChunk = currentWorld.getChunkAt(currentPlayer.getLocation());
			List<ChunkDB> results = database.find(ChunkDB.class).where().ieq("world", currentChunk.getWorld().getName()).between("x", currentChunk.getX()-loadRange, currentChunk.getX()+loadRange ).between("z", currentChunk.getZ()-loadRange, currentChunk.getZ()+loadRange).findList();
			if (results.size() > 0) {
				currentPlayer.sendMessage("This chunk is being kept loaded by " + results.get(0).getPlayer() + " using a tag of " + results.get(0).getTag() + ".");
				return;
			}
		}
	}
	
	@EventHandler
	public final void cbChunkUnload(ChunkUnloadEvent cuEvent){
		World currentWorld = cuEvent.getWorld();
		for (int worldX = (-1 * loadRange); worldX <= loadRange; worldX++){
			for (int worldZ = (-1 * loadRange); worldZ <= loadRange; worldZ++){
				Chunk currentChunk = currentWorld.getChunkAt(cuEvent.getChunk().getX()+worldX, cuEvent.getChunk().getZ()+worldZ);
				List<ChunkDB> results = database.find(ChunkDB.class).where().ieq("world", currentChunk.getWorld().getName()).eq("x", currentChunk.getX()).eq("z", currentChunk.getZ()).findList();
				if (results.size() > 0) {
					Iterator<ChunkDB> rowIterator = results.iterator();
					while (rowIterator.hasNext())
					{
						ChunkDB row = rowIterator.next();
					if (!onlyOnlinePlayers || this.getServer().getOfflinePlayer(row.getPlayer()).isOnline()) {
						if(debugMessages){
							logMessage(Level.FINE, "Chunk (" + currentChunk.getWorld().getName() + ": " + worldX + ", " + worldZ + ") kept loaded by " + row.getPlayer() + " with a tag of " + row.getTag());
						}
						cuEvent.setCancelled(true);
						return;
					}
					}
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public final void onBlockPlace(BlockPlaceEvent bpEvent)
	{
		if (!bpEvent.isCancelled() && this.useBlock && bpEvent.getBlock().getType().equals(this.clMaterial))
		{
			Player actor = bpEvent.getPlayer();
			Chunk here = bpEvent.getBlock().getChunk();
			String tag = UUID.randomUUID().toString();
			Result result = registerChunk(actor, here, tag);
			actor.sendMessage(result.getMessage());
			bpEvent.setCancelled(!result.isSuccess());
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public final void onBlockBreak(BlockBreakEvent bbEvent)
	{
		if (!bbEvent.isCancelled() && this.useBlock && bbEvent.getBlock().getType().equals(this.clMaterial))
		{
			Player actor = bbEvent.getPlayer();
			Chunk here = bbEvent.getBlock().getChunk();
			Result result = removeChunk(actor, here);
			actor.sendMessage(result.getMessage());
			bbEvent.setCancelled(!result.isSuccess());
		}
	}

	public Result registerChunk(Player actor, Chunk here, String tag)
	{
		Result result = new Result();
		if (!actor.hasPermission("chunkyblocks.set"))
		{
			result.setMessage("You do not have permission to register chunks");
			result.setSuccess(false);
		} else {
			ChunkDB search = database.find(ChunkDB.class).where().ieq("world", here.getWorld().getName()).eq("x", here.getX()).eq("z", here.getZ()).findUnique();
			if (search != null)
			{
				result.setMessage("This chunk is already registered to " + search.getPlayer() + " as " + search.getTag() + ".");
				result.setSuccess(false);
			} else {
				ChunkDB existing = database.find(ChunkDB.class).where().ieq("player", actor.getName()).ieq("tag", tag).findUnique();
				if (existing == null)
				{
					int registered = database.find(ChunkDB.class).where().ieq("player", actor.getName()).findRowCount();
					if (registered < this.maxChunks || actor.hasPermission("chunkyblocks.overridechunklimit")) {
						ChunkDB newRecord = new ChunkDB();
						newRecord.setPlayer(actor.getName());
						newRecord.setChunk(here);
						newRecord.setTag(tag);
						database.save(newRecord);
						result.setMessage("This chunk is now registered to " + actor.getName() + " as " + tag + ".");
						result.setSuccess(true);
					} else {
						result.setMessage("You have already reached your chunkloading limit.");
						result.setSuccess(false);
					}
				} else {
					existing.setChunk(here);
					database.save(existing);
					result.setMessage("Location (" + tag + ") updated in chunkloading list.");
					result.setSuccess(true);
				}
			}
		}
		return result;
	}
	
	public Result removeChunk(Player actor, Chunk target)
	{
		Result result = new Result();
		if (!(actor.hasPermission("chunkyblocks.remove") || actor.hasPermission("chunkyblocks.adminremove")))
		{
			result.setMessage("You do not have permission to remove chunks");
			result.setSuccess(false);
		} else {
			ChunkDB search = database.find(ChunkDB.class).where().ieq("world", target.getWorld().getName()).eq("x", target.getX()).eq("z", target.getZ()).findUnique();
			if (search != null)
			{
				if (search.getPlayer().equals(actor.getName()) || actor.hasPermission("chunkyblocks.adminremove"))
				{
					result.setMessage("This chunk is no longer registered to " + search.getPlayer() + " as " + search.getTag() + ".");
					result.setSuccess(true);
					if (!actor.getName().equals(search.getPlayer()))
					{
						this.getServer().getPlayer(search.getPlayer()).sendMessage("Your chunk registered as " + search.getTag() + " has been removed from the list.");
					}
					database.delete(search);
				}
			}
		}
		return result;
	}
	
	public Chunk findChunk(Player actor, String tag)
	{
		Chunk here = null;
		ChunkDB search = database.find(ChunkDB.class).where().ieq("player", actor.getName()).ieq("tag",tag).findUnique();
		if (search != null)
		{
			here=search.getChunk();
		}
		return here;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Player player = null;
		Chunk here = null;
		if(sender instanceof Player) {
			player = (Player) sender;
			here = player.getLocation().getChunk();
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
			Result result = registerChunk(player,here,tag);
			player.sendMessage(result.getMessage());
		}		
		if (commandName.equals("removechunk")){
			String tag = "";
			if(args.length > 0){
				tag = args[0];
			}
			if(tag.isEmpty()) {
				tag = "default";
			}
			here = findChunk(player,tag);
			if (here == null)
			{
				player.sendMessage("No chunk was found with a label of " + ChatColor.AQUA + tag + ChatColor.RESET + ".");
				return true;
			}
			Result result = removeChunk(player, here);
			player.sendMessage(result.getMessage());
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
			List<ChunkDB> results = database.find(ChunkDB.class).where().ieq("player",player.getName()).findList();
			if (results == null) {
				player.sendMessage("No chunks are registered to your name");
				return true;
			} else {
				Iterator<ChunkDB> itResults = results.iterator();
				String lines = "";
				while(itResults.hasNext()) {
					ChunkDB row = itResults.next();
					lines = lines + row.getTag() + " (" + row.getWorld() + ": " + row.getX() + ", " + row.getZ() + ")\n";
				}
				ChatPage chat = ChatPaginator.paginate(lines, Integer.parseInt(page));
				player.sendMessage("Your chunks: " + chat.getPageNumber() + "/" + chat.getTotalPages());
				player.sendMessage(chat.getLines());
				return true;
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
			List<ChunkDB> results;
			if(playerName.equals("-")) {
				playerName = "anyone";
				results = database.find(ChunkDB.class).findList();
			} else {
				results = database.find(ChunkDB.class).where().ieq("player", playerName).findList();
			}
			if (results == null) {
				player.sendMessage("No chunks are registered to " + playerName);
				return true;
			} else {
				String lines = "";
				Iterator<ChunkDB> itResults = results.iterator();
				while (itResults.hasNext()) {
					ChunkDB row = itResults.next();
					lines = lines + row.getPlayer() + " " + row.getTag() + " (" + row.getWorld() + ": " + row.getX() + ", " + row.getZ() + ")\n";
				}
				ChatPage chat = ChatPaginator.paginate(lines, page);
				player.sendMessage("Chunks: " + chat.getPageNumber() + "/" + chat.getTotalPages());
				player.sendMessage(chat.getLines());
				return true;
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
			ChunkDB result = database.find(ChunkDB.class).where().ieq("player", playerName).ieq("tag", tag).findUnique();
			if (result == null) {
				player.sendMessage("No chunk found for Player: " + playerName + ", with Tag: " + tag);
				return true;
			} else {
				Chunk destChunk = result.getChunk();
				Location teleTo = null;
				for(int y=255;y>1;y--){
					if(!destChunk.getBlock(7, y-1, 7).isEmpty()){
						teleTo = destChunk.getBlock(7, y, 7).getLocation();
						break;
					}
				}
				if(teleTo == null){
					player.sendMessage("There appears to be a hole in the world at the teleport location!");
					return true;
				} else {
					player.teleport(teleTo, TeleportCause.COMMAND);
					return true;
				}
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
			here = findChunk(this.getServer().getPlayer(playerName),tag);
			Result result = removeChunk(this.getServer().getPlayer(playerName),here);
			player.sendMessage(result.getMessage());
		}
		if (commandName.equals("cbsilence")){
			if (suppressed.contains(player.getName())){
				suppressed.remove(player.getName());
				return true;
			} else {
				suppressed.add(player.getName());
			}
		}
		return false;
	}

	private void logMessage(Level logLevel, String message) {
		myLogger.log(logLevel, "[" + info.getName() + "]: " + message);
	}
}
