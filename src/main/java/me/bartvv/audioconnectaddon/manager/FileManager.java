package me.bartvv.audioconnectaddon.manager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class FileManager extends FileConfiguration {

	private boolean debug = false;
	private JavaPlugin javaPlugin;
	private File file;
	private YamlConfiguration configuration;
	private YamlConfiguration backendConfiguration;
	private String fileName;
	private File pathName;
	private Map< String, Object > cache;
	private String parent;

	public void setDebug( boolean debug ) {
		this.debug = debug;
	}

	public void setParent( String parent ) {
		this.parent = parent;
	}

	private int unsavedChanges = 0;
	private int maxUnsavedChanges = 10;

	public FileManager( JavaPlugin javaPlugin, String name ) {
		this( javaPlugin, name, 10, javaPlugin.getDataFolder(), false );
	}

	public FileManager( JavaPlugin javaPlugin, String name, int maxUnsavedChanges ) {
		this( javaPlugin, name, maxUnsavedChanges, javaPlugin.getDataFolder(), false );
	}

	public FileManager( JavaPlugin javaPlugin, String name, int maxUnsavedChanges, File file ) {
		this( javaPlugin, name, maxUnsavedChanges, file, false );
	}

	public FileManager( JavaPlugin javaPlugin, String name, int maxUnsavedChanges, File file, boolean debug ) {
		Validate.notNull( javaPlugin, "JavaPlugin cannot be null" );
		Validate.notNull( name, "Name cannot be null!" );
		Validate.notNull( maxUnsavedChanges, "unsaved changes cannot be null!" );
		if ( !name.endsWith( ".yml" ) ) {
			name = name + ".yml";
		}
		setDebug( debug );
		this.javaPlugin = javaPlugin;
		this.fileName = name;
		this.pathName = file;
		this.file = new File( file, name );
		if ( this.javaPlugin.getDataFolder().exists() ) {
			this.javaPlugin.getDataFolder().mkdirs();
		}
		if ( !file.getParentFile().exists() ) {
			debug( "Creating parentfile dirs" );
			file.getParentFile().mkdirs();
		}
		this.cache = Maps.newHashMap();

		if ( !this.file.exists() ) {
			InputStream input = javaPlugin.getResource( name );
			OutputStream output = null;
			try {
				Files.createDirectories( this.file.getParentFile().toPath() );
				if ( input == null ) {
					file.createNewFile();
				} else {
					output = new FileOutputStream( this.file );
					byte[] buffer = new byte[ 1024 ];
					int length = 0;
					while ( ( length = input.read( buffer ) ) > 0 ) {
						output.write( buffer, 0, length );
					}
					output.flush();
					output.close();
				}
				debug( "Saved resource " + name );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		this.configuration = YamlConfiguration.loadConfiguration( this.file );

		try {
			InputStream input = javaPlugin.getResource( this.fileName );
			Reader reader = new InputStreamReader( input );

			this.backendConfiguration = YamlConfiguration.loadConfiguration( reader );
			try {
				reader.close();
				input.close();
			} catch ( IOException e ) {
				debug( "Failed to close " + this.file.getName() + "!" );
				e.printStackTrace();
				return;
			}
		} catch ( Exception exc ) {
			debug( this.file.getName() + " has no backend configuration!" );
		}
	}

	public FileManager( File file ) {
		this.cache = Maps.newHashMap();
		this.file = file;
		this.configuration = YamlConfiguration.loadConfiguration( this.file );
		this.backendConfiguration = YamlConfiguration.loadConfiguration( this.file );
	}

	public void rename( File newFile ) {
		this.file.renameTo( newFile );
	}

	public List< String > getStringList( String path ) {
		return getStringList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< String > getStringList( String path, boolean load ) {
		List< String > str;
		Object obj = get( path, load, null );
		if ( String.class.isInstance( obj ) )
			str = Lists.newArrayList( obj.toString() );
		else
			str = ( List< String > ) obj;
		if ( ( str != null ) && ( !str.isEmpty() ) ) {
			for ( int i = 0; i < str.size(); i++ ) {
				if ( str.get( i ) instanceof String ) {
					str.set( i, ChatColor.translateAlternateColorCodes( '&', str.get( i ) ) );
				}
			}
		}
		return str;
	}

	public void set( String path, Object obj ) {
		set( path, obj, false );
	}

	public void set( String path, Object obj, boolean save ) {
		if ( this.maxUnsavedChanges == -1 ) {
			throw new RuntimeException( "Saving is disabled for file " + this.file.getName() );
		}
		this.cache.put( path, obj );
		if ( ( obj instanceof Location ) ) {
			Location loc = ( Location ) obj;
			this.configuration.set( path + ".world", loc.getWorld().getName() );
			this.configuration.set( path + ".X", Double.valueOf( loc.getX() ) );
			this.configuration.set( path + ".Y", Double.valueOf( loc.getY() ) );
			this.configuration.set( path + ".Z", Double.valueOf( loc.getZ() ) );
			this.configuration.set( path + ".yaw", Float.valueOf( loc.getYaw() ) );
			this.configuration.set( path + ".pitch", Float.valueOf( loc.getPitch() ) );
		} else {
			this.configuration.set( path, obj );
		}
		this.unsavedChanges += 1;
		if ( save ) {
			if ( save() ) {
				this.unsavedChanges = 0;
			} else {
				debug( "Failed to save config" );
				this.javaPlugin.getLogger().log( Level.WARNING,
						"Config could not be saved for plugin: " + this.javaPlugin.getName() + "!" );
				this.javaPlugin.getLogger().log( Level.WARNING, "Please contact plugin owner to fix this! " );
				this.javaPlugin.getLogger().log( Level.WARNING, "Also include the stacktrace shown above!" );
			}
		} else if ( this.unsavedChanges >= this.maxUnsavedChanges ) {
			if ( save() ) {
				this.unsavedChanges = 0;
			} else {
				debug( "Failed to save config" );
				this.javaPlugin.getLogger().log( Level.WARNING,
						"Config could not be saved for plugin: " + this.javaPlugin.getDescription().getName()
								+ "! (Version: " + this.javaPlugin.getDescription().getVersion() + ")" );
				this.javaPlugin.getLogger().log( Level.WARNING, "Please contact plugin owner ("
						+ this.javaPlugin.getDescription().getAuthors().toString().replace( "[", "" ).replace( "]", "" )
						+ ") to fix this! " );
				this.javaPlugin.getLogger().log( Level.WARNING, "Also include the stacktrace shown above!" );
			}
		}
	}

	public String getString( String path ) {
		return getString( path, false, null );
	}

	public String getString( String path, boolean load ) {
		return getString( path, load, null );
	}

	@Override
	public String getString( String path, String def ) {
		return getString( path, false, def );
	}

	public String getString( String path, boolean load, String def ) {
		Object obj = get( path, load, def );
		String translate = obj == null ? null : obj.toString();
		return translate == null ? null : ChatColor.translateAlternateColorCodes( '&', translate );
	}

	public Location getLocation( String path ) {
		return getLocation( path, false );
	}

	public Location getLocation( String path, boolean load ) {
		try {
			Validate.notNull( path, "Path cannot be null" );
			Location loc = null;
			Object obj = null;
			if ( load ) {
				String worldName = getString( path + ".world", load );
				World world = Bukkit.getWorld( worldName );

				Double x = Double.valueOf( getDouble( path + ".X", true ) );
				Double y = Double.valueOf( getDouble( path + ".Y", true ) );
				Double z = Double.valueOf( getDouble( path + ".Z", true ) );
				Double yaw = Double.valueOf( getDouble( path + ".yaw", true ) );
				Double pitch = Double.valueOf( getDouble( path + ".pitch", true ) );
				loc = new Location( world, x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw.floatValue(),
						pitch.floatValue() );
				if ( loc != null ) {
					this.cache.put( path, loc );
				}
			} else {
				obj = this.cache.get( path );
				if ( ( obj != null ) && ( ( obj instanceof Location ) ) ) {
					loc = ( Location ) obj;
				} else {
					obj = getLocation( path, true );
					if ( ( obj != null ) && ( ( obj instanceof Location ) ) ) {
						this.cache.put( path, obj );
					}
				}
			}
			return ( Location ) obj;
		} catch ( Exception exc ) {}
		return null;
	}

	public int getInt( String path ) {
		return getInt( path, false, -1 );
	}

	public int getInt( String path, int def ) {
		return getInt( path, false, def );
	}

	public int getInt( String path, boolean load ) {
		return getInt( path, load, -1 );
	}

	public int getInt( String path, boolean load, int def ) {
		return ( ( Integer ) get( path, load, def ) );
	}

	public long getLong( String path ) {
		return getLong( path, false, -1 );
	}

	public long getLong( String path, long def ) {
		return getLong( path, false, def );
	}

	public long getLong( String path, boolean load ) {
		return getLong( path, load, -1 );
	}

	public long getLong( String path, boolean load, long def ) {
		return ( ( Long ) get( path, load, def ) );
	}

	public double getDouble( String path ) {
		return getDouble( path, false, -1.0D );
	}

	public double getDouble( String path, boolean load ) {
		return getDouble( path, load, -1.0D );
	}

	@Override
	public double getDouble( String path, double def ) {
		return getDouble( path, false, def );
	}

	public double getDouble( String path, boolean load, Double def ) {
		Object obj = get( path, load, def );
		if ( obj instanceof Double ) {
			return ( double ) obj;
		} else if ( obj instanceof Integer ) {
			return ( int ) obj;
		}
		return def;
	}

	public Object get( String path ) {
		return get( path, false, null );
	}

	@Override
	public Object get( String path, Object def ) {
		return get( path, false, def );
	}

	public Object get( String path, boolean load ) {
		return get( path, load, null );
	}

	public Object get( String path, boolean load, Object def ) {
		Validate.notNull( path, "Path cannot be null" );
		if ( this.parent != null && !path.startsWith( this.parent ) )
			path = this.parent + "." + path;
		Object obj;
		if ( load ) {
			debug( "Getting an object from path " + path );
			obj = this.configuration.get( path, def );
			if ( obj != null ) {
				this.cache.put( path, obj );
			} else {
				debug( "Getting an object from the backend configuration path: " + path );
				obj = this.backendConfiguration.get( path, def );
				if ( obj == null ) {
					debug( "Path: " + path + " does not exist in file: " + this.file.getName() );
					return null;
				}
				this.cache.put( path, obj );
			}
		} else {
			debug( "Trying to get an object from the cache with path " + path );
			obj = this.cache.get( path );
			if ( obj == null ) {
				debug( "Path is not in cache! Trying to load it from the file" );
				obj = get( path, true, def );
			}
		}
		debug( "Returning the " + ( obj == null ? "default" : "cached object" ) + "!" );
		return obj == null ? def : obj;
	}

	public List< ? > getList( String path ) {
		return getList( path, false, null );
	}

	@Override
	public List< ? > getList( String path, List< ? > def ) {
		return getList( path, false, def );
	}

	public List< ? > getList( String path, boolean load ) {
		return getList( path, load, null );
	}

	public List< ? > getList( String path, boolean load, List< ? > def ) {
		return ( SuperList< ? > ) get( path, load, def );
	}

	@Override
	public List< Boolean > getBooleanList( String path ) {
		return getBooleanList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Boolean > getBooleanList( String path, boolean load ) {
		return ( List< Boolean > ) get( path, load );
	}

	@Override
	public List< Byte > getByteList( String path ) {
		return getByteList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Byte > getByteList( String path, boolean load ) {
		return ( List< Byte > ) get( path, load );
	}

	@Override
	public List< Character > getCharacterList( String path ) {
		return getCharacterList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Character > getCharacterList( String path, boolean load ) {
		return ( List< Character > ) get( path, load );
	}

	@Override
	public List< Double > getDoubleList( String path ) {
		return getDoubleList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Double > getDoubleList( String path, boolean load ) {
		return ( List< Double > ) get( path, load );
	}

	@Override
	public List< Float > getFloatList( String path ) {
		return getFloatList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Float > getFloatList( String path, boolean load ) {
		return ( List< Float > ) get( path, load );
	}

	@Override
	public List< Integer > getIntegerList( String path ) {
		return getIntegerList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Integer > getIntegerList( String path, boolean load ) {
		return ( List< Integer > ) get( path, load );
	}

	@Override
	public List< Long > getLongList( String path ) {
		return getLongList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Long > getLongList( String path, boolean load ) {
		return ( List< Long > ) get( path, load );
	}

	@Override
	public List< Map< ?, ? > > getMapList( String path ) {
		return getMapList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Map< ?, ? > > getMapList( String path, boolean load ) {
		return ( List< Map< ?, ? > > ) get( path, load );
	}

	@Override
	public List< Short > getShortList( String path ) {
		return getShortList( path, false );
	}

	@SuppressWarnings( "unchecked" )
	public List< Short > getShortList( String path, boolean load ) {
		return ( List< Short > ) get( path, load );
	}

	public boolean getBoolean( String path ) {
		return getBoolean( path, false, false );
	}

	public boolean getBoolean( String path, boolean def ) {
		return getBoolean( path, false, def );
	}

	public boolean getBoolean( String path, boolean load, boolean def ) {
		return ( ( Boolean ) get( path, load, def ) );
	}

	public ItemStack getItemStack( String path ) {
		return getItemStack( path, false, null );
	}

	public ItemStack getItemStack( String path, ItemStack def ) {
		return getItemStack( path, false, def );
	}

	public ItemStack getItemStack( String path, boolean load ) {
		return getItemStack( path, load, null );
	}

	public ItemStack getItemStack( String path, boolean load, ItemStack def ) {
		Validate.notNull( path, "Path cannot be null" );
		ItemStack itemStack;
		if ( load ) {
			itemStack = this.configuration.getItemStack( path );
			this.cache.put( path, itemStack );
		} else {
			Object obj = this.cache.get( path );
			if ( ( obj != null ) && ( ( obj instanceof ItemStack ) ) ) {
				itemStack = ( ItemStack ) obj;
			} else {
				itemStack = getItemStack( path, true );
			}
		}
		return itemStack == null ? def : itemStack;
	}

	@Override
	public boolean contains( String path ) {
		Object obj = get( path );
		return obj != null;
	}

	public YamlConfiguration getConfiguration() {
		return this.configuration;
	}

	public ConfigurationSection getConfigurationSection( String path ) {
		return getSection( path, false );
	}

	public ConfigurationSection getSection( String path, boolean load ) {
		Validate.notNull( path, "Path cannot be null" );
		ConfigurationSection section;
		if ( load ) {
			section = this.configuration
					.getConfigurationSection( ( this.parent == null ? "" : this.parent + "." ) + path );
			this.cache.put( path, section );
			debug( "Got configurationsection from path " + path );
		} else {
			Object obj = this.cache.get( path );
			if ( ( obj != null ) && ( ( obj instanceof ConfigurationSection ) ) ) {
				section = ( ConfigurationSection ) obj;
			} else {
				section = getSection( path, true );
			}
		}
		return section;
	}

	public void resetCache( boolean save ) {
		if ( save ) {
			save();
		}
		this.cache.clear();
		debug( "Resetted cache" );
	}

	public void setMaxUnsavedChanges( int maxUnsavedChanges ) {
		this.maxUnsavedChanges = maxUnsavedChanges;
	}

	public boolean save( boolean async ) {
		if ( this.maxUnsavedChanges == -1 ) {
			throw new RuntimeException( "Saving is disabled for file " + this.file.getName() );
		}
		if ( this.unsavedChanges == 0 ) {
			return true;
		}
		if ( async ) {
			new BukkitRunnable() {

				public void run() {
					try {
						configuration.save( file );
						unsavedChanges = 0;
						cache.clear();
						debug( file.getName() + " saved" );
					} catch ( IOException ioexception ) {
						debug( "Failed to save " + file.getName() );
						ioexception.printStackTrace();
					}
				}
			}.runTaskAsynchronously( javaPlugin );
			return true;
		} else {
			try {
				this.configuration.save( this.file );
				this.unsavedChanges = 0;
				this.cache.clear();
				debug( this.file.getName() + " saved" );
				return true;
			} catch ( IOException ioexception ) {
				debug( "Failed to save " + this.file.getName() );
				ioexception.printStackTrace();
			}
			return false;
		}
	}

	public boolean save() {
		return save( false );
	}

	public File getFile() {
		return this.file;
	}

	public void save( File file ) throws IOException {
		this.file = file;
		save();
	}

	public void save( String file ) throws IOException {
		save();
	}

	private void debug( String debug ) {
		if ( this.debug ) {
			System.out.println( "[" + this.javaPlugin.getName() + "] [DEBUG] " + debug );
		}
	}

	public String saveToString() {
		return this.configuration.saveToString();
	}

	public void loadFromString( String contents ) throws InvalidConfigurationException {
		this.configuration.loadFromString( contents );
	}

	protected String buildHeader() {
		throw new UnsupportedOperationException( "Not supported" );
	}

	public void reload() {
		save( false );
		this.file = new File( this.pathName, this.fileName );
		if ( this.javaPlugin.getDataFolder().exists() ) {
			this.javaPlugin.getDataFolder().mkdirs();
		}
		if ( !file.getParentFile().exists() ) {
			debug( "Creating parentfile dirs" );
			file.getParentFile().mkdirs();
		}
		this.cache = Maps.newHashMap();

		InputStream input = javaPlugin.getResource( this.fileName );
		OutputStream output = null;

		if ( !this.file.exists() ) {
			try {
				Files.createDirectories( this.file.getParentFile().toPath() );
				output = new FileOutputStream( this.file );
				byte[] buffer = new byte[ 1024 ];
				int length = 0;
				while ( ( length = input.read( buffer ) ) > 0 ) {
					output.write( buffer, 0, length );
				}
				debug( "Saved resource " + this.fileName );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		}
		this.configuration = YamlConfiguration.loadConfiguration( this.file );

		Reader reader = new InputStreamReader( input );

		this.backendConfiguration = YamlConfiguration.loadConfiguration( reader );
		try {
			reader.close();
			input.close();
		} catch ( IOException e ) {
			debug( "Failed to create " + this.file.getName() + "!" );
			e.printStackTrace();
			return;
		}
	}

	public Material getMaterial( String path, Material def ) {
		Object obj = get( path );
		try {
			return Material.getMaterial( obj.toString() );
		} catch ( Exception exc ) {}
		return def;
	}

	public class SuperList< E > extends ArrayList< E > {

		private static final long serialVersionUID = 1L;

		@Override
		public E get( int index ) {
			try {
				return super.get( index );

			} catch ( Exception exc ) {}
			return null;
		}

		@Override
		public E remove( int index ) {
			try {
				return super.remove( index );
			} catch ( Exception exc ) {}
			return null;
		}
	}
}
