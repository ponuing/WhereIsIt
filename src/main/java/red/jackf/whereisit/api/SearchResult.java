package red.jackf.whereisit.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import red.jackf.whereisit.util.Codecs;

import java.util.*;

public final class SearchResult {
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchResult> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SearchResult::pos,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs::optional).map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::item,
            ComponentSerialization.OPTIONAL_STREAM_CODEC.map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::name,
            Codecs.VEC3.apply(ByteBufCodecs::optional).map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::customNameOffset,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new)),
            SearchResult::otherPositions,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs::optional).map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::entityId,
            SearchResult::new
    );

    /**
     * Legacy codec to support packets sent before entity ids were included in {@link SearchResult}.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SearchResult> LEGACY_STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SearchResult::pos,
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs::optional).map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::item,
            ComponentSerialization.OPTIONAL_STREAM_CODEC.map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::name,
            Codecs.VEC3.apply(ByteBufCodecs::optional).map(opt -> opt.orElse(null), Optional::ofNullable),
            SearchResult::customNameOffset,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.collection(HashSet::new)),
            SearchResult::otherPositions,
            SearchResult::legacy
    );

    private final BlockPos pos;
    private final @Nullable ItemStack item;
    private final @Nullable Component name;
    private final @Nullable Vec3 nameOffset;
    private final Set<BlockPos> otherPositions = new HashSet<>();
    private final @Nullable Integer entityId;

    private SearchResult(BlockPos pos,
                         @Nullable ItemStack item,
                         @Nullable Component name,
                         @Nullable Vec3 nameOffset,
                         Collection<BlockPos> otherPositions,
                         @Nullable Integer entityId) {
        this.pos = pos;
        this.item = item;
        this.name = name;
        this.nameOffset = nameOffset;
        this.otherPositions.addAll(otherPositions);
        this.entityId = entityId;
    }

    private static SearchResult legacy(BlockPos pos,
                                       @Nullable ItemStack item,
                                       @Nullable Component name,
                                       @Nullable Vec3 nameOffset,
                                       Collection<BlockPos> otherPositions) {
        return new SearchResult(pos, item, name, nameOffset, otherPositions, null);
    }

    public static Builder builder(BlockPos pos) {
        return new Builder(pos.immutable());
    }

    public BlockPos pos() {
        return pos;
    }

    public @Nullable ItemStack item() {
        return item;
    }

    public @Nullable Component name() {
        return name;
    }

    public @Nullable Vec3 customNameOffset() {
        return nameOffset;
    }

    public Set<BlockPos> otherPositions() {
        return otherPositions;
    }

    public @Nullable Integer entityId() {
        return entityId;
    }

    public boolean isEntityResult() {
        return entityId != null;
    }

    /**
     * @return Offset the label should be above the main position
     */
    public Vec3 nameOffset() {
        return nameOffset == null ? calculateDefaultOffset() : nameOffset;
    }

    /**
     * Return a copy of this result with additional other positions
     * @param otherPositions Other positions to add to the new result
     * @return Search result with other positions added
     */
    public SearchResult withOtherPositions(List<BlockPos> otherPositions) {
        var copy = new SearchResult(pos, item, name, nameOffset, otherPositions, entityId);
        copy.otherPositions.addAll(otherPositions);
        copy.otherPositions.remove(pos);
        return copy;
    }

    /**
     * Return a copy of this result with the given entity id attached.
     * @param entityId Entity id to attach
     * @return Search result with an entity id set
     */
    public SearchResult withEntityId(int entityId) {
        return new SearchResult(pos, item, name, nameOffset, otherPositions, entityId);
    }

    /**
     * Calculate the default offset, by taking the average position for this result and returning the center of the
     * block above
     * @return A block above the average position of this result
     */
    @ApiStatus.Internal
    private Vec3 calculateDefaultOffset() {
        if (otherPositions.isEmpty()) return new Vec3(0, 1, 0);

        var base = Vec3.atLowerCornerOf(pos);
        for (BlockPos otherPos : otherPositions)
            base = base.add(Vec3.atLowerCornerOf(otherPos));
        var factor = 1.0 / (otherPositions.size() + 1);
        return base.multiply(factor, factor, factor).subtract(Vec3.atLowerCornerOf(pos)).add(0, 1, 0);
    }

    public static class Builder {
        private final BlockPos pos;
        private @Nullable ItemStack item;
        private @Nullable Component name;
        private @Nullable Vec3 nameOffset;
        private final Set<BlockPos> otherPositions = new HashSet<>();
        private @Nullable Integer entityId;

        private Builder(BlockPos pos) {
            this.pos = pos;
        }

        public Builder item(ItemStack item) {
            this.item = item;
            return this;
        }

        public Builder name(Component name, Vec3 offset) {
            this.name = name;
            this.nameOffset = offset;
            return this;
        }

        public Builder otherPositions(Collection<BlockPos> others) {
            this.otherPositions.addAll(others);
            return this;
        }

        public Builder entityId(int entityId) {
            this.entityId = entityId;
            return this;
        }

        public SearchResult build() {
            return new SearchResult(pos, item, name, nameOffset, otherPositions, entityId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResult result = (SearchResult) o;
        return Objects.equals(pos, result.pos) && Objects.equals(item, result.item) && Objects.equals(name, result.name) && Objects.equals(nameOffset, result.nameOffset) && Objects.equals(otherPositions, result.otherPositions) && Objects.equals(entityId, result.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pos, item, name, nameOffset, otherPositions, entityId);
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "pos=" + pos +
                ", item=" + item +
                ", name=" + name +
                ", nameOffset=" + nameOffset +
                ", otherPositions=" + otherPositions +
                ", entityId=" + entityId +
                '}';
    }
}