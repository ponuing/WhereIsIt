package red.jackf.whereisit.api.search;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import red.jackf.jackfredlib.api.base.ResultHolder;
import red.jackf.whereisit.api.SearchRequest;
import red.jackf.whereisit.api.SearchResult;

public interface EntitySearcher {
    Event<EntitySearcher> EVENT = EventFactory.createArrayBacked(EntitySearcher.class, (listeners) -> (request, player, entity) -> {
        for (EntitySearcher listener : listeners) {
            ResultHolder<SearchResult> result = listener.search(request, player, entity);
            if (result.shouldTerminate()) {
                return result;
            }
        }
        return ResultHolder.pass();
    });

    ResultHolder<SearchResult> search(SearchRequest request, ServerPlayer player, Entity entity);
}