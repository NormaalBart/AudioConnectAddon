package me.bartvv.audioconnectaddon.listener;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.audioconnect.flags.AudioTrack.DayTime;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.magmaguy.elitemobs.api.EliteMobDamagedByPlayerEvent;
import com.magmaguy.elitemobs.mobconstructor.EliteMobEntity;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;

public class MobListener implements Listener {

	private final me.bartvv.audioconnectaddon.AudioConnect audioConnect;

	private Cache<UUID, UUID> combatTag;
	private Map<UUID, List<String>> audioTrack;

	public MobListener(me.bartvv.audioconnectaddon.AudioConnect audioConnect) {
		this.audioConnect = audioConnect;
		this.audioTrack = Maps.newHashMap();
		this.combatTag = CacheBuilder.newBuilder()
				.expireAfterWrite(this.audioConnect.getConfig().getInt("combat-prevent"), TimeUnit.SECONDS)
				.removalListener(notification -> {
					if (notification.getCause() != RemovalCause.EXPIRED)
						return;
					Player player = Bukkit.getPlayer((UUID) notification.getKey());
					if (player != null) {
						stopSound(player, (UUID) notification.getValue());
						startRegionSound(player);
					}
				}).build();
		Bukkit.getScheduler().runTaskTimer(this.audioConnect, this.combatTag::cleanUp, 0, 20);
	}

	@SuppressWarnings("unchecked")
	@EventHandler
	public void on(EliteMobDamagedByPlayerEvent e) {
		EliteMobEntity eliteMobEntity = e.getEliteMobEntity();
		Player player = e.getPlayer();
		if (this.combatTag.getIfPresent(player.getUniqueId()) != null) {
			UUID uuidTarget = this.combatTag.getIfPresent(player.getUniqueId());
			if (uuidTarget.equals(eliteMobEntity.getLivingEntity().getUniqueId())) {
				this.combatTag.put(player.getUniqueId(), eliteMobEntity.getLivingEntity().getUniqueId());
				return;
			}
		}
		DayTime dayTime = getDayTime(eliteMobEntity.getLivingEntity().getWorld());
		try {
			List<AudioMessage> list = Lists.newArrayList();
			ConfigurationSection configurationSection = this.audioConnect.getConfig()
					.getSection("mobs." + eliteMobEntity.getName().replace(ChatColor.COLOR_CHAR, '&') + "."
							+ dayTime.toString().toLowerCase(), false);
			if (configurationSection == null)
				return;
			configurationSection.getKeys(false).forEach(trackId -> {
				Object object = this.audioConnect.getConfig()
						.get("mobs." + eliteMobEntity.getName().replace(ChatColor.COLOR_CHAR, '&') + "."
								+ dayTime.toString().toLowerCase() + "." + trackId);
				List<String> audioId = Lists.newArrayList();
				if (object instanceof List)
					audioId = (List<String>) object;
				else
					audioId.add(object.toString());
				if (trackId == null)
					return;
				AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId());
				audioId.forEach(audioIdString -> {
					if (audioIdString != null && !audioIdString.equals("*")) {
						audioMessageBuilder.audio(audioIdString);
					}
				});
				if (trackId != null && !trackId.equals("*")) {
					audioMessageBuilder.track(trackId);
				}
				list.add(audioMessageBuilder.build());
				this.audioTrack.compute(eliteMobEntity.getLivingEntity().getUniqueId(), (uuid, trackList) -> {
					if (trackList == null)
						trackList = Lists.newArrayList();
					trackList.add(trackId);
					return trackList;
				});
			});
			AudioConnect.getInstance().getClient().writeAndFlush(list.toArray(new AudioMessage[list.size()]));
			this.combatTag.put(player.getUniqueId(), eliteMobEntity.getLivingEntity().getUniqueId());
		} catch (Exception exc) {
		}
	}

	@EventHandler
	public void on(EntityDeathEvent e) {
		Bukkit.getOnlinePlayers().forEach(player -> {
			if (this.combatTag.getIfPresent(player.getUniqueId()) == null)
				return;

			UUID uuid = this.combatTag.getIfPresent(player.getUniqueId());
			if (!uuid.equals(e.getEntity().getUniqueId()))
				return;
			stopSound(player, uuid);
			startRegionSound(player);
		});
	}

	@SuppressWarnings("unchecked")
	private void startRegionSound(Player player) {
		RegionQuery regionQuery = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
		ApplicableRegionSet applicableRegionSet = regionQuery
				.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));
		List<ProtectedRegion> protectedRegions = applicableRegionSet.getRegions().stream()
				.sorted(new RegionComparator()).collect(Collectors.toList());
		for (ProtectedRegion protectedRegion : protectedRegions) {
			boolean found = false;
			Map<com.sk89q.worldguard.protection.flags.Flag<?>, Object> flags = protectedRegion.getFlags();

			if (flags != null)
				for (Entry<com.sk89q.worldguard.protection.flags.Flag<?>, Object> entry : flags.entrySet()) {
					if (entry.getKey().getName().equals(AudioConnect.getInstance().getAudioFlag().getName())) {
						found = true;
						AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId());
						for (AudioTrack audioTrack : (java.lang.Iterable<AudioTrack>) entry.getValue()) {
							String audioId = audioTrack.getAudioId();
							String trackId = audioTrack.getTrackId();
							if (audioId != null && !audioId.equals("*")) {
								audioMessageBuilder.audio(audioId);
							}
							if (trackId != null && !trackId.equals("*")) {
								audioMessageBuilder.track(trackId);
							}
						}
						AudioConnect.getInstance().getClient().writeAndFlush(audioMessageBuilder.build());
					}
				}
			if (found)
				break;
		}
	}

	private void stopSound(Player player, UUID uuid) {
		List<AudioMessage> list = Lists.newArrayList();
		this.audioTrack.getOrDefault(uuid, Collections.emptyList()).forEach(trackId -> {
			AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId());
			audioMessageBuilder.track(trackId);
			list.add(audioMessageBuilder.build());
		});
		if (!list.isEmpty())
			AudioConnect.getInstance().getClient().writeAndFlush(list.toArray(new AudioMessage[list.size()]));
		this.combatTag.invalidate(player.getUniqueId());
	}

	private DayTime getDayTime(World world) {
		return DayTime.VALUES.stream().filter(dayTime -> dayTime.check(world)).findFirst().get();
	}

	private class RegionComparator implements Comparator<ProtectedRegion> {

		@Override
		public int compare(ProtectedRegion o1, ProtectedRegion o2) {
			return Integer.compare(o2.getPriority(), o1.getPriority());
		}
	}
}
