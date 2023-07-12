package cursedflames.velocitydiscordbridge;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;

import java.util.List;

import static cursedflames.velocitydiscordbridge.Broadcast.BroadcastOrigin;
import static cursedflames.velocitydiscordbridge.Broadcast.BroadcastUser;
import static cursedflames.velocitydiscordbridge.Broadcast.UnknownServer;
import static cursedflames.velocitydiscordbridge.Messages.BroadcastChatMessage;
import static cursedflames.velocitydiscordbridge.Messages.BroadcastServerJoinLeave;
import static cursedflames.velocitydiscordbridge.Messages.JoinLeave;
import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MESSAGES;
import static net.dv8tion.jda.api.requests.GatewayIntent.MESSAGE_CONTENT;

@Plugin(
		id = "discord-bridge",
		name = "Velocity Discord Bridge",
		// TODO actually make gradle substitute this string
		version = "${version}"
)
public class VelocityPlugin {
	final ProxyServer server;
	static Logger logger;

	JDAWebhookClient webhookClient;

	private final Broadcast broadcaster;

	private static final String webhookUrl = "https://discord.com/api/webhooks/redacted";
	// FIXME put this in `.env`
	private static final String token = "redacted";
	private static final String bridgeChannelId = "redacted";

	@Inject
	public VelocityPlugin(ProxyServer server, Logger logger) {
		this.server = server;
		VelocityPlugin.logger = logger;
		this.broadcaster = new Broadcast(this);
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) throws InterruptedException {
		logger.info("Velocity Discord Bridge initializing");

		var jda = JDABuilder
				.create(token, List.of(GUILD_MESSAGES, MESSAGE_CONTENT))
				.addEventListeners(new JDAEventListener(this))
				.build();

		jda.awaitReady();

		webhookClient = JDAWebhookClient.withUrl(webhookUrl);
		logger.info("Velocity Discord Bridge ready");
	}

	@Subscribe
	public void onPlayerChat(PlayerChatEvent event) {
		broadcaster.broadcast(new BroadcastChatMessage(
				BroadcastOrigin.fromPlayer(event.getPlayer()),
				BroadcastUser.fromMinecraft(event.getPlayer()),
				event.getMessage()
		));
	}

	@Subscribe
	public void onServerConnect(ServerConnectedEvent event) {
		// join: broadcast to all except joined
		// swap: broadcast to all including old but excluding new
		var serverName = event.getServer().getServerInfo().getName();
		var prevServer = event.getPreviousServer();
		var prevServerName = prevServer.isPresent() ? prevServer.get().getServerInfo().getName() : null;
		broadcaster.broadcast(new BroadcastServerJoinLeave(new BroadcastOrigin.MinecraftServer(serverName),
						BroadcastUser.fromMinecraft(event.getPlayer()), prevServer.isPresent() ? JoinLeave.SWAP : JoinLeave.JOIN, prevServerName, serverName)
		);
	}

	@Subscribe
	public void onServerDisconnect(DisconnectEvent event) {
		// leave: broadcast to all except left
		var serverName = event.getPlayer().getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(UnknownServer);
		broadcaster.broadcast(new BroadcastServerJoinLeave(new BroadcastOrigin.MinecraftServer(serverName),
				BroadcastUser.fromMinecraft(event.getPlayer()), JoinLeave.LEAVE, serverName, null)
		);
	}

	static class JDAEventListener extends ListenerAdapter {
		private final VelocityPlugin plugin;
		public JDAEventListener(VelocityPlugin plugin) {
			this.plugin = plugin;
		}

		@Override
		public void onMessageReceived(MessageReceivedEvent event) {
			if (event.isWebhookMessage()) return;
			if (!event.getChannel().getId().equals(bridgeChannelId)) return;
			plugin.broadcaster.broadcast(new BroadcastChatMessage(
					BroadcastOrigin.DISCORD,
					BroadcastUser.fromDiscord(event.getAuthor()),
					event.getMessage().getContentRaw()
			));
		}
	}
}
