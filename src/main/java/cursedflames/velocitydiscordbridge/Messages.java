package cursedflames.velocitydiscordbridge;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import javax.annotation.Nullable;

import static cursedflames.velocitydiscordbridge.Broadcast.*;

public class Messages {
	public record BroadcastChatMessage(Broadcast.BroadcastOrigin origin, Broadcast.BroadcastUser user, String rawMessage) implements BroadcastMessage {
		@Override public Broadcast.BroadcastOrigin getOrigin() {
			return origin;
		}
		@Override public WebhookMessage toDiscord() {
			var message = new WebhookMessageBuilder();
			message.setUsername(user.name());
			message.setAvatarUrl(user.profilePictureUrl());
			message.setContent(rawMessage);
			return message.build();
		}
		@Override public Component toMinecraft() {
			if (origin instanceof Broadcast.BroadcastOrigin.Discord)
				return Component.text("<@%s> ".formatted(user.name())).append(MinecraftSerializer.INSTANCE.serialize(rawMessage));
			// Commented, since we use the same formatting for unknown origins
//			if (origin instanceof BroadcastOrigin.MinecraftServer)
			return Component.text("<%s> %s".formatted(user.name(), rawMessage)).color(NamedTextColor.GRAY);
		}
	}

	public enum JoinLeave {
		JOIN,
		LEAVE,
		SWAP
	}

	public record BroadcastServerJoinLeave(
			Broadcast.BroadcastOrigin origin,
			Broadcast.BroadcastUser user,
			JoinLeave type,
			@Nullable String oldServer,
			@Nullable String newServer) implements BroadcastMessage {
		@Override public Broadcast.BroadcastOrigin getOrigin() {
			return origin;
		}
		private String joinLeaveMessage(boolean useEmoji) {
			var emoji = !useEmoji ? "" : switch(type) {
				case JOIN -> "\uD83D\uDCE5 ";
				case LEAVE -> "\uD83D\uDCE4 ";
				case SWAP -> "\uD83D\uDD00 ";
			};
			return emoji + switch (type) {
				case JOIN -> "%s joined %s".formatted(user.name(), newServer);
				case LEAVE -> "%s left %s".formatted(user.name(), oldServer);
				case SWAP -> "%s left %s, joined %s".formatted(user.name(), oldServer, newServer);
			};
		}

		@Override public WebhookMessage toDiscord() {
			return new WebhookMessageBuilder()
					.setUsername("Server")
					.addEmbeds(new WebhookEmbedBuilder()
							.setTitle(new WebhookEmbed.EmbedTitle(joinLeaveMessage(true), null))
							.setColor(switch(type) {
								case JOIN -> 0x00FF00;
								case LEAVE -> 0xFF0000;
								case SWAP -> 0x0077FF;
							})
							.setThumbnailUrl(user.profilePictureUrl())
							.build())
					.build();
		}

		@Override public Component toMinecraft() {
			return Component.text(joinLeaveMessage(false), NamedTextColor.AQUA);
		}
	}
}
