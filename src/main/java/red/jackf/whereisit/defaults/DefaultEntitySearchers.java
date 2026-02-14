package red.jackf.whereisit.defaults;

import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import red.jackf.jackfredlib.api.base.ResultHolder;
import red.jackf.whereisit.api.SearchRequest;
import red.jackf.whereisit.api.SearchResult;
import red.jackf.whereisit.api.search.EntitySearcher;
import red.jackf.whereisit.config.WhereIsItConfig;

public class DefaultEntitySearchers {

    public static void setup() {
        EntitySearcher.EVENT.register((request, player, entity) -> {
            if (!WhereIsItConfig.INSTANCE.instance().getCommon().debug.enableDefaultSearchers) return ResultHolder.pass();
            if (entity instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty() && SearchRequest.check(stack, request)) {
                        return buildResult(entity, stack);
                    }
                }
            }
            return ResultHolder.pass();
        });
    }

    private static ResultHolder<SearchResult> buildResult(Entity entity, ItemStack stack) {
        var pos = entity.blockPosition();
        var result = SearchResult.builder(pos);
        result.item(stack);
        result.name(null, null);
        result.entityId(entity.getId());
        return ResultHolder.value(result.build());
    }
}