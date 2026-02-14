package red.jackf.whereisit.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;
import red.jackf.whereisit.WhereIsIt;
import red.jackf.whereisit.api.SearchRequest;
import red.jackf.whereisit.api.SearchResult;

import java.util.Collection;
import java.util.HashSet;

/**
 * Buffer format:
 * <li>id: long</li>
 * For each result:
 * <li>position: BlockPos</li>
 * <li>hasItemDetails: boolean</li>
 * <li>if (hasItemDetails) item: ItemStack</li>
 * <li>hasCustomName: boolean</li>
 * <li>if (hasCustomName) name: Component</li>
 * <li>if (hasCustomName) hasCustomNameOffset: boolean</li>
 * <li>if (hasCustomName && hasCustomNameOffset) nameOffset: 3 * double</li>
 * <li>numberOfOtherPositions: Collection&lt;BlockPos&gt;</li>
 * <li>entityId: Optional&lt;int&gt;</li>
 * After:
 * <li>hasRequest: boolean</li>
 * <li>if (hasRequest) request: SearchRequest</li>
 */
public record ClientboundResultsPacket(long id, Collection<SearchResult> results, @Nullable SearchRequest request, boolean legacyEncoding) implements CustomPacketPayload {
    public static final Type<ClientboundResultsPacket> TYPE = new Type<>(WhereIsIt.id("s2c_founditem"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundResultsPacket> INTERNAL_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            ClientboundResultsPacket::id,
            SearchResult.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new)),
            ClientboundResultsPacket::results,
            ByteBufCodecs.fromCodecWithRegistries(SearchRequest.CODEC),
            ClientboundResultsPacket::request,
            (id, results, request) -> new ClientboundResultsPacket(id, results, request, false)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundResultsPacket> LEGACY_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            ClientboundResultsPacket::id,
            SearchResult.LEGACY_STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new)),
            ClientboundResultsPacket::results,
            ByteBufCodecs.fromCodecWithRegistries(SearchRequest.CODEC),
            ClientboundResultsPacket::request,
            (id, results, request) -> new ClientboundResultsPacket(id, results, request, true)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundResultsPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientboundResultsPacket decode(RegistryFriendlyByteBuf buf) {
            int readerIndex = buf.readerIndex();
            try {
                ClientboundResultsPacket legacyPacket = LEGACY_STREAM_CODEC.decode(buf);
                if (!buf.isReadable()) {
                    return legacyPacket;
                }

                // unread bytes implies this wasn't actually legacy; reset and try the new format
                buf.readerIndex(readerIndex);
            } catch (Exception ignored) {
                buf.readerIndex(readerIndex);
            }

            try {
                return INTERNAL_STREAM_CODEC.decode(buf);
            } catch (Exception ex) {
                buf.readerIndex(readerIndex);
                WhereIsIt.LOGGER.debug("Failed to decode results packet with entity ids, trying legacy format instead", ex);
                try {
                    return LEGACY_STREAM_CODEC.decode(buf);
                } catch (Exception legacyEx) {
                    legacyEx.addSuppressed(ex);
                    throw legacyEx;
                }
            }
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ClientboundResultsPacket value) {
            if (value.legacyEncoding) {
                LEGACY_STREAM_CODEC.encode(buf, value);
            } else {
                INTERNAL_STREAM_CODEC.encode(buf, value);
            }
        }
    };

    public static ClientboundResultsPacket forClient(long id, Collection<SearchResult> results, @Nullable SearchRequest request, boolean clientSupportsEntities) {
        boolean encodeLegacy = !clientSupportsEntities;
        return new ClientboundResultsPacket(id, results, request, encodeLegacy);
    }

    public static final long WHEREIS_COMMAND_ID = -1L;

    @Override
    public Type<ClientboundResultsPacket> type() {
        return TYPE;
    }
}
