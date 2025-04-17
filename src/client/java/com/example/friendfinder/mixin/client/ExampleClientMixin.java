package com.example.friendfinder.mixin.client;

import com.example.friendfinder.FriendFinderClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.world.World;

@Mixin(ClientPlayerEntity.class)
public class ExampleClientMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FriendFinder");
    private BlockPos lastInteractedPos = null;

    @Inject(at = @At("HEAD"), method = "breakBlock")
    private void onBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (FriendFinderClient.getInstance().isEnabled()) {
            FriendFinderClient.getInstance().removeWaypoint(pos);
        }
    }

    @Inject(at = @At("HEAD"), method = "interactBlock")
    private void onBlockInteract(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Only check if the mod is enabled and we haven't just checked this position
        if (FriendFinderClient.getInstance().isEnabled() && 
            (lastInteractedPos == null || !lastInteractedPos.equals(pos))) {
            World world = ((ClientPlayerEntity)(Object)this).getWorld();
            if (world != null) {
                BlockState state = world.getBlockState(pos);
                if (state.isAir()) {
                    FriendFinderClient.getInstance().removeWaypoint(pos);
                }
            }
            lastInteractedPos = pos;
        }
    }
} 