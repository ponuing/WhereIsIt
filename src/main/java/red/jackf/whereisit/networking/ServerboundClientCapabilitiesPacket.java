package red.jackf.whereisit.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import red.jackf.whereisit.WhereIsIt;

/**
 * Sent by clients on join to advertise optional features they understand.
 */
public record ServerboundClientCapabilitiesPacket(boolean supportsEntityIds) implements CustomPacketPayload {
    public static final Type<ServerboundClientCapabilitiesPacket> TYPE = new Type<>(WhereIsIt.id("c2s_clientcaps"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundClientCapabilitiesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            ServerboundClientCapabilitiesPacket::supportsEntityIds,
            ServerboundClientCapabilitiesPacket::new
    );

    @Override
    public @NotNull Type<ServerboundClientCapabilitiesPacket> type() {
        return TYPE;
    }
}