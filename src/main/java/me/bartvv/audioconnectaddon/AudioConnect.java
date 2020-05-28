package me.bartvv.audioconnectaddon;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import me.bartvv.audioconnectaddon.listener.MobListener;
import me.bartvv.audioconnectaddon.listener.PlayerListener;
import me.bartvv.audioconnectaddon.manager.FileManager;

@Getter
public class AudioConnect extends JavaPlugin {

	private FileManager config;
	public final NamespacedKey AUDIO_CONNECT = new NamespacedKey(this, "audioconnect");

	@Override
	public void onEnable() {
		this.config = new FileManager(this, "config.yml", -1, getDataFolder(), false);
		getServer().getPluginManager().registerEvents(new MobListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
	}

	@Override
	public void onDisable() {
		getServer().getOnlinePlayers().forEach(player -> player.getPersistentDataContainer().remove(AUDIO_CONNECT));
	}
}
