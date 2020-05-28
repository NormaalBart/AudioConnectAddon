package me.bartvv.audioconnectaddon.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import lombok.RequiredArgsConstructor;
import me.bartvv.audioconnectaddon.AudioConnect;

@RequiredArgsConstructor
public class PlayerListener implements Listener {

	private final AudioConnect audioConnect;

	@EventHandler
	public void on(PlayerQuitEvent e) {
		e.getPlayer().getPersistentDataContainer().remove(this.audioConnect.AUDIO_CONNECT);
	}
}
