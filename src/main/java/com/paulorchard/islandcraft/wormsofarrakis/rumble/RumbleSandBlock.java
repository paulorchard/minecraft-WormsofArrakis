package com.paulorchard.islandcraft.wormsofarrakis.rumble;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Prompt 15 part 1: a bottom-aligned layer of sand, level/16 of a block high (1 to 15), like vanilla snow layers. It is only ever sent to clients as a fake block by the
 * sand rumble and never exists in a real world: no item, no loot, no tab. If one ever gets into a world (a command, a leaked save) it removes itself on the next tick, and
 * {@link RumbleGuard} replaces it with air when its chunk loads.
 */
public class RumbleSandBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 15);
    private static final VoxelShape[] SHAPES = new VoxelShape[16];
    static {
        for (int i = 1; i <= 15; i++) SHAPES[i] = Block.box(0.0, 0.0, 0.0, 16.0, i, 16.0);
        SHAPES[0] = SHAPES[1];
    }

    public RumbleSandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LEVEL); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPES[state.getValue(LEVEL)]; }

    /** Thin and not opaque for the light engine, as vanilla layered snow is (the shape decides what light passes). */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) { return true; }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
}
