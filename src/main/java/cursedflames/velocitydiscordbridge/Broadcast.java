package cursedflames.velocitydiscordbridge;

import club.minnced.discord.webhook.send.WebhookMessage;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;

public class Broadcast {
	public static final String UnknownServer = "Unknown Server";

	private final VelocityPlugin plugin;
	public Broadcast(VelocityPlugin plugin) {
		this.plugin = plugin;
	}

	public sealed interface BroadcastOrigin {
		record Discord() implements BroadcastOrigin {}
		// thanks java
		// wouldn't need to do this if we had rust-style enums
		Discord DISCORD = new Discord();
		record MinecraftServer(String name) implements BroadcastOrigin {}

		static MinecraftServer fromPlayer(Player player) {
			var server = player.getCurrentServer();
			if (server.isEmpty()) {
				VelocityPlugin.logger.warn("Failed to get current server of player: {}", player.getUsername());
				return new MinecraftServer(UnknownServer);
			}
			return new MinecraftServer(server.get().getServerInfo().getName());
		}
	}

	public record BroadcastUser(String name, String profilePictureUrl) {
		public static BroadcastUser fromMinecraft(Player player) {
			// TODO make this configurable
			return new BroadcastUser(player.getUsername(), "https://crafatar.com/renders/head/" + player.getUniqueId());
		}
		@SuppressWarnings("deprecation")
		public static BroadcastUser fromDiscord(User user) {
			var name = user.getName();
			var discrim = user.getDiscriminator();
			if (!discrim.equals("0000")) name += "#" + discrim;
			return new BroadcastUser(name, user.getEffectiveAvatarUrl());
		}
	}

	interface BroadcastMessage {
		Broadcast.BroadcastOrigin getOrigin();
		WebhookMessage toDiscord();
		Component toMinecraft();
	}

	public void broadcast(BroadcastMessage message) {
		var origin = message.getOrigin();
		if (!(origin instanceof BroadcastOrigin.Discord)) {
			sendToDiscord(message);
		}
		plugin.server.getAllServers().forEach(server -> {
			// Skip the origin server, if the broadcast is from a server
			if (!(origin instanceof BroadcastOrigin.MinecraftServer originServer && server.getServerInfo().getName().equals(originServer.name))) {
				sendToServer(server, message);
			}
		});
	}

	public void sendToDiscord(BroadcastMessage message) {
		plugin.webhookClient.send(message.toDiscord());
	}

	public void sendToServer(RegisteredServer server, BroadcastMessage message) {
		server.sendMessage(message.toMinecraft());
	}
}
