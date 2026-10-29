package dev.tanghulu.addon.util.tree;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Track only logs matching the current tree, including those that drift out of its original box. */
public final class TreeLoot {
    private final Set<Item> logs = new HashSet<>();
    private final Set<UUID> tracked = new HashSet<>();
    private Box box;

    public void begin(List<BlockPos> tree, Collection<Block> blocks) {
        logs.clear(); tracked.clear();
        for (Block block : blocks) logs.add(block.asItem());
        BlockPos first = tree.getFirst();
        box = new Box(first);
        for (BlockPos p : tree) box = box.union(new Box(p));
        box = box.expand(6, 8, 6);
    }

    public boolean accepts(Item item, UUID id, Vec3d position) {
        if (!logs.contains(item) || box == null) return false;
        if (!tracked.contains(id) && !box.contains(position)) return false;
        tracked.add(id);
        return true;
    }

    public List<ItemEntity> find(Iterable<Entity> entities) {
        List<ItemEntity> result = new ArrayList<>();
        for (Entity entity : entities) if (entity instanceof ItemEntity item && item.isAlive()
            && accepts(item.getStack().getItem(), item.getUuid(), item.getEntityPos())) result.add(item);
        Set<UUID> live = new HashSet<>();
        for (ItemEntity item : result) live.add(item.getUuid());
        tracked.retainAll(live);
        return result;
    }
}
