package red.jackf.whereisit.serverside;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import red.jackf.jackfredlib.api.colour.Colour;
import red.jackf.jackfredlib.api.lying.Debris;
import red.jackf.jackfredlib.api.lying.Lie;
import red.jackf.jackfredlib.api.lying.entity.EntityLie;
import red.jackf.jackfredlib.api.lying.entity.builders.EntityBuilders;
import red.jackf.jackfredlib.api.lying.entity.builders.display.BlockDisplayBuilder;
import red.jackf.jackfredlib.api.lying.glowing.EntityGlowLie;
import red.jackf.whereisit.WhereIsIt;
import red.jackf.whereisit.api.SearchResult;
import red.jackf.whereisit.config.WhereIsItConfig;

import java.util.Collection;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/**
 * Handles sending results to a player which may not have the mod installed client side
 */
public class ServerSideRenderer {
    private static final Block MAIN_BLOCK = Blocks.STRUCTURE_BLOCK;
    private static final Block ALT_BLOCK = Blocks.JIGSAW;
    private static final float HIGHLIGHT = 0.7f;
    private static final Multimap<GameProfile, Lie> playerHighlightLies = MultimapBuilder.hashKeys()
            .arrayListValues().build();
    private static final byte GLOWING_FLAG_MASK = 0x40;
    private static final Scoreboard FAKE_TEAM_SCOREBOARD = new Scoreboard();
    private static int teamNonce = 0;
    private static EntityDataAccessor<Byte> sharedFlagsAccessor;
    private static final ChatFormatting[] ENTITY_GLOW_COLOURS = {
            ChatFormatting.AQUA,
            ChatFormatting.GREEN,
            ChatFormatting.YELLOW,
            ChatFormatting.GOLD,
            ChatFormatting.RED,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.BLUE,
            ChatFormatting.WHITE
    };

    /**
     * Fade all server-side lies for a player
     *
     * @param player Player to fade lies for
     */
    public static void fadeServerSide(ServerPlayer player) {
        playerHighlightLies.removeAll(player.getGameProfile()).forEach(Lie::fade);
    }

    /**
     * Build an entity lie to show the given player, using ALT_BLOCK as the block.
     *
     * @param level  Level to place the lie in
     * @param pos    Position to place the lie at
     * @param colour Colour of the lie
     * @return Built entity lie at the given position
     */
    private static Display.BlockDisplay makeAlternateBlockDisplay(ServerLevel level, BlockPos pos, Colour colour) {
        return EntityBuilders.blockDisplay(level).positionCentered(pos).state(ALT_BLOCK.defaultBlockState())
                .scaleAndCenter(HIGHLIGHT).glowing(true, colour).build();
    }

    /**
     * Convert an arbitrary RGB colour to the nearest supported entity glow team colour.
     */
    private static ChatFormatting toClosestEntityGlowColour(Colour colour) {
        ChatFormatting closest = ChatFormatting.WHITE;
        int bestDistance = Integer.MAX_VALUE;

        for (ChatFormatting candidate : ENTITY_GLOW_COLOURS) {
            Integer candidateRgb = candidate.getColor();
            if (candidateRgb == null) continue;

            int cr = (candidateRgb >> 16) & 0xFF;
            int cg = (candidateRgb >> 8) & 0xFF;
            int cb = candidateRgb & 0xFF;

            int dr = colour.r() - cr;
            int dg = colour.g() - cg;
            int db = colour.b() - cb;
            int distance = dr * dr + dg * dg + db * db;

            if (distance < bestDistance) {
                bestDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private static synchronized String nextEntityTeamName(int entityId) {
        int nonce = teamNonce++ & 0xFFFF;
        return "wii%08x%04x".formatted(entityId, nonce);
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> getSharedFlagsAccessor() {
        if (sharedFlagsAccessor != null) return sharedFlagsAccessor;

        try {
            var field = Entity.class.getDeclaredField("DATA_SHARED_FLAGS_ID");
            field.setAccessible(true);
            sharedFlagsAccessor = (EntityDataAccessor<Byte>) field.get(null);
            return sharedFlagsAccessor;
        } catch (ReflectiveOperationException ignored) {
            try {
                var field = Entity.class.getDeclaredField("field_5990");
                field.setAccessible(true);
                sharedFlagsAccessor = (EntityDataAccessor<Byte>) field.get(null);
                return sharedFlagsAccessor;
            } catch (ReflectiveOperationException ex) {
                WhereIsIt.LOGGER.error("Unable to resolve Entity shared flags accessor for server-side entity glow", ex);
                return null;
            }
        }
    }

    private static void sendForcedGlowPacket(ServerPlayer player, Entity entity, boolean glowing) {
        EntityDataAccessor<Byte> accessor = getSharedFlagsAccessor();
        if (accessor == null) return;

        byte flags = entity.getEntityData().get(accessor);
        byte value = glowing ? (byte) (flags | GLOWING_FLAG_MASK) : (byte) (flags & ~GLOWING_FLAG_MASK);
        var packet = new ClientboundSetEntityDataPacket(entity.getId(),
                java.util.List.of(SynchedEntityData.DataValue.create(accessor, value)));
        player.connection.send(packet);
    }

    /**
     * Return a random fade time, centered on the config time with some jitter
     *
     * @return Randomly generated fade time
     */
    private static int randomFadeTime() {
        int baseTime = WhereIsItConfig.INSTANCE.instance().getCommon().fadeoutTimeTicks;
        int random = (int) ((4 * TICKS_PER_SECOND) * Math.random() - (2 * TICKS_PER_SECOND));
        return baseTime + random;
    }

    /**
     * Render a set of results to the player, using JackFredLib's lying module
     *
     * @param player  Player to render server-side results for
     * @param results Results to render
     */
    public static void doServersideRendering(ServerPlayer player, Collection<SearchResult> results) {
        WhereIsIt.LOGGER.debug("Doing server-side rendering for {}", player.getScoreboardName());
        var level = (ServerLevel) player.level();

        for (SearchResult result : results) {
            Colour colour = Colour.fromHSV((float) Math.random(), 1, 1);

            int timeoutTicks = randomFadeTime();

            if (result.isEntityResult()) {
                if (result.entityId() == null) continue;
                int entityId = result.entityId();
                var entity = level.getEntity(entityId);
                if (entity == null) continue;
                long expiresAt = level.getGameTime() + timeoutTicks;
                ChatFormatting glowColour = toClosestEntityGlowColour(colour);
                PlayerTeam fakeTeam = new PlayerTeam(FAKE_TEAM_SCOREBOARD, nextEntityTeamName(entityId));
                fakeTeam.setColor(glowColour);
                String entityKey = entity.getScoreboardName();

                player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(fakeTeam, true));
                player.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(fakeTeam, entityKey, ClientboundSetPlayerTeamPacket.Action.ADD));

                var glow = EntityGlowLie.builder(entity)
                        .colour(null)
                        .onTick((player1, lie) -> {
                            if (!lie.entity().isAlive() || player1.level().getGameTime() >= expiresAt) {
                                lie.fade();
                                return;
                            }
                            sendForcedGlowPacket(player1, lie.entity(), true);
                        })
                        .onFade((player1, lie) -> {
                            playerHighlightLies.remove(player1.getGameProfile(), lie);
                            player1.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(fakeTeam, entityKey, ClientboundSetPlayerTeamPacket.Action.REMOVE));
                            player1.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(fakeTeam));
                        })
                        .createAndShow(player);

                playerHighlightLies.put(player.getGameProfile(), glow);
                sendForcedGlowPacket(player, entity, true);
                Debris.INSTANCE.schedule(glow, timeoutTicks);
                continue;
            }

            Vec3 mainPos = Vec3.atBottomCenterOf(result.pos().above());
            if (result.name() != null) mainPos = mainPos.add(result.nameOffset().subtract(0, 1, 0));

            BlockDisplayBuilder mainEntity = EntityBuilders.blockDisplay(level).position(mainPos)
                    .state(MAIN_BLOCK.defaultBlockState()).scaleAndCenter(HIGHLIGHT)
                    .addTranslation(new Vector3f(0, -0.5f, 0)).glowing(true, colour);

            if (result.name() != null) {
                mainEntity.customName(result.name()).alwaysRenderName(true);
                mainEntity.addTranslation(result.nameOffset().subtract(0, 1, 0).reverse().toVector3f());
            }

            EntityLie<Display.BlockDisplay> main = EntityLie.builder(mainEntity.build())
                    .onFade((player1, lie) -> playerHighlightLies.remove(player1.getGameProfile(), lie))
                    .createAndShow(player);

            playerHighlightLies.put(player.getGameProfile(), main);

            Debris.INSTANCE.schedule(main, timeoutTicks);

            for (BlockPos otherPos : result.otherPositions()) {
                EntityLie<Display.BlockDisplay> proxy = EntityLie.builder(makeAlternateBlockDisplay(level, otherPos, colour))
                        .onFade((player1, lie) -> playerHighlightLies.remove(player1.getGameProfile(), lie))
                        .createAndShow(player);
                playerHighlightLies.put(player.getGameProfile(), proxy);
                Debris.INSTANCE.schedule(proxy, timeoutTicks);
            }
        }
    }
}
