package red.jackf.whereisit.networking;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientCapabilities {
    private static final Map<UUID, Boolean> ENTITY_ID_SUPPORT = new ConcurrentHashMap<>();

    public static void setSupportsEntityIds(ServerPlayer player, boolean supports) {
        ENTITY_ID_SUPPORT.put(player.getUUID(), supports);
    }

    public static boolean supportsEntityIds(ServerPlayer player) {
        return ENTITY_ID_SUPPORT.getOrDefault(player.getUUID(), false);
    }

    public static void clear(ServerPlayer player) {
        ENTITY_ID_SUPPORT.remove(player.getUUID());
    }
}