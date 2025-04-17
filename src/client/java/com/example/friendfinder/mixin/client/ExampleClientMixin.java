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

@Mixin(ClientPlayerEntity.class)
public class ExampleClientMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FriendFinder");

    @Inject(at = @At("HEAD"), method = "breakBlock")
    private void onBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        LOGGER.info("Block broken at position: {}", pos);
        FriendFinderClient.getInstance().removeWaypoint(pos);
    }
} 