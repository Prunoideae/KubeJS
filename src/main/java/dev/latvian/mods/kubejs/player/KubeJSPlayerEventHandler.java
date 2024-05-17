package dev.latvian.mods.kubejs.player;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.latvian.mods.kubejs.CommonProperties;
import dev.latvian.mods.kubejs.bindings.event.PlayerEvents;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;

public class KubeJSPlayerEventHandler {
	public static void init() {
		PlayerEvent.PLAYER_JOIN.register(KubeJSPlayerEventHandler::loggedIn);
		PlayerEvent.PLAYER_QUIT.register(KubeJSPlayerEventHandler::loggedOut);
		TickEvent.PLAYER_POST.register(KubeJSPlayerEventHandler::tick);
		ChatEvent.DECORATE.register(KubeJSPlayerEventHandler::chatDecorate);
		ChatEvent.RECEIVED.register(KubeJSPlayerEventHandler::chatReceived);
		PlayerEvent.PLAYER_ADVANCEMENT.register(KubeJSPlayerEventHandler::advancement);
		PlayerEvent.OPEN_MENU.register(KubeJSPlayerEventHandler::inventoryOpened);
		PlayerEvent.CLOSE_MENU.register(KubeJSPlayerEventHandler::inventoryClosed);
	}

	public static void loggedIn(ServerPlayer player) {
		PlayerEvents.LOGGED_IN.post(ScriptType.SERVER, new SimplePlayerKubeEvent(player));
		player.inventoryMenu.addSlotListener(player.kjs$getInventoryChangeListener());

		if (!ConsoleJS.SERVER.errors.isEmpty() && !CommonProperties.get().hideServerScriptErrors) {
			player.displayClientMessage(ConsoleJS.SERVER.errorsComponent("/kubejs errors server"), false);
		}

		player.kjs$getStages().sync();
	}

	public static void respawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean keepData) {
		newPlayer.kjs$setRawPersistentData(oldPlayer.kjs$getRawPersistentData());
		newPlayer.inventoryMenu.addSlotListener(newPlayer.kjs$getInventoryChangeListener());
		PlayerEvents.RESPAWNED.post(ScriptType.SERVER, new PlayerRespawnedKubeEvent(newPlayer, oldPlayer, keepData));
		newPlayer.kjs$getStages().sync();
	}

	public static void loggedOut(ServerPlayer player) {
		PlayerEvents.LOGGED_OUT.post(ScriptType.SERVER, new SimplePlayerKubeEvent(player));
	}

	public static void tick(Player player) {
		if (PlayerEvents.TICK.hasListeners()) {
			PlayerEvents.TICK.post(player, new SimplePlayerKubeEvent(player));
		}
	}

	public static void chatDecorate(ServerPlayer player, ChatEvent.ChatComponent component) {
		PlayerEvents.DECORATE_CHAT.post(ScriptType.SERVER, new PlayerChatDecorateKubeEvent(player, component));
	}

	public static EventResult chatReceived(ServerPlayer player, Component component) {
		return PlayerEvents.CHAT.hasListeners() ? PlayerEvents.CHAT.post(ScriptType.SERVER, new PlayerChatReceivedKubeEvent(player, component)).arch() : EventResult.pass();
	}

	public static void advancement(ServerPlayer player, AdvancementHolder advancement) {
		if (PlayerEvents.ADVANCEMENT.hasListeners()) {
			var id = advancement.id();
			PlayerEvents.ADVANCEMENT.post(new PlayerAdvancementKubeEvent(player, id), id);
		}
	}

	public static void inventoryOpened(Player player, AbstractContainerMenu menu) {
		if (!(menu instanceof InventoryMenu)) {
			menu.addSlotListener(player.kjs$getInventoryChangeListener());
		}

		if (PlayerEvents.INVENTORY_OPENED.hasListeners()) {
			PlayerEvents.INVENTORY_OPENED.post(player, menu, new InventoryKubeEvent(player, menu));
		}

		if (menu instanceof ChestMenu && PlayerEvents.CHEST_OPENED.hasListeners()) {
			PlayerEvents.CHEST_OPENED.post(player, menu, new ChestKubeEvent(player, menu));
		}
	}

	public static void inventoryClosed(Player player, AbstractContainerMenu menu) {
		if (PlayerEvents.INVENTORY_CLOSED.hasListeners()) {
			PlayerEvents.INVENTORY_CLOSED.post(player, menu, new InventoryKubeEvent(player, menu));
		}

		if (menu instanceof ChestMenu && PlayerEvents.CHEST_CLOSED.hasListeners()) {
			PlayerEvents.CHEST_CLOSED.post(player, menu, new ChestKubeEvent(player, menu));
		}
	}
}