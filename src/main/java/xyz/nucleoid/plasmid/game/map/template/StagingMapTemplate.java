package xyz.nucleoid.plasmid.game.map.template;

import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A staging map represents an in-world map template before it has been compiled to a static file.
 * <p>
 * It stores regions and arbitrary data destined to be compiled into a {@link MapTemplate}.
 */
public final class StagingMapTemplate {
    private final ServerWorld world;
    private final Identifier identifier;
    private final BlockBounds bounds;

    private final List<TemplateRegion> regions = new ArrayList<>();

    public StagingMapTemplate(ServerWorld world, Identifier identifier, BlockBounds bounds) {
        this.world = world;
        this.identifier = identifier;
        this.bounds = bounds;
    }

    private void setDirty() {
        StagingMapManager.get(this.world).setDirty(true);
    }

    public void addRegion(String marker, BlockBounds bounds, CompoundTag tag) {
        this.regions.add(new TemplateRegion(marker, bounds, tag));
        this.setDirty();
    }

    public void removeRegion(TemplateRegion region) {
        this.regions.remove(region);
        this.setDirty();
    }

    public Identifier getIdentifier() {
        return this.identifier;
    }

    public BlockBounds getBounds() {
        return this.bounds;
    }

    public Collection<TemplateRegion> getRegions() {
        return this.regions;
    }

    public CompoundTag serialize(CompoundTag root) {
        root.putString("identifier", this.identifier.toString());
        this.bounds.serialize(root);

        ListTag regionList = new ListTag();
        for (TemplateRegion region : this.regions) {
            regionList.add(region.serialize(new CompoundTag()));
        }
        root.put("regions", regionList);

        return root;
    }

    public static StagingMapTemplate deserialize(ServerWorld world, CompoundTag root) {
        Identifier identifier = new Identifier(root.getString("identifier"));

        BlockBounds bounds = BlockBounds.deserialize(root);

        StagingMapTemplate map = new StagingMapTemplate(world, identifier, bounds);
        ListTag regionList = root.getList("regions", NbtType.COMPOUND);
        for (int i = 0; i < regionList.size(); i++) {
            CompoundTag regionRoot = regionList.getCompound(i);
            map.regions.add(TemplateRegion.deserialize(regionRoot));
        }

        return map;
    }

    /**
     * Compiles this staging map template into a map template.
     * <p>
     * It copies the block and entity data from the world and stores it within the template.
     * All positions are made relative.
     *
     * @return The compiled map.
     */
    public MapTemplate compile() {
        MapTemplate map = MapTemplate.createEmpty();
        map.bounds = this.globalToLocal(this.bounds);

        for (TemplateRegion region : this.regions) {
            map.addRegion(
                    region.getMarker(),
                    this.globalToLocal(region.getBounds()),
                    region.getData()
            );
        }

        for (BlockPos pos : this.bounds.iterate()) {
            BlockPos localPos = this.globalToLocal(pos);

            BlockState state = this.world.getBlockState(pos);
            map.setBlockState(localPos, state);

            BlockEntity entity = this.world.getBlockEntity(pos);
            if (entity != null) {
                map.setBlockEntity(localPos, entity);
            }
        }

        // @TODO Make this more flexible
        this.compileEntities(map, EntityType.ARMOR_STAND);
        this.compileEntities(map, EntityType.ITEM_FRAME);
        this.compileEntities(map, EntityType.PAINTING);

        return map;
    }

    /**
     * Compiles the entities of the specified type which are inside the map.
     *
     * @param map The map.
     * @param type The entity type.
     * @param <T> The entity type.
     */
    private <T extends Entity> void compileEntities(MapTemplate map, EntityType<T> type) {
        this.world.getEntitiesByType(type, this.bounds.toBox(), entity -> !entity.removed)
                .forEach(entity -> {
                    final Vec3d localPos = this.globalToLocal(entity.getPos());
                    map.addEntity(entity, localPos);
                });
    }

    private BlockPos globalToLocal(BlockPos pos) {
        return pos.subtract(this.bounds.getMin());
    }

    private Vec3d globalToLocal(Vec3d pos) {
        BlockPos min = this.bounds.getMin();
        return pos.subtract(min.getX(), min.getY(), min.getZ());
    }

    private BlockBounds globalToLocal(BlockBounds bounds) {
        return new BlockBounds(this.globalToLocal(bounds.getMin()), this.globalToLocal(bounds.getMax()));
    }
}
