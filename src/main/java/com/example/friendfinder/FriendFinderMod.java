package com.example.friendfinder;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FriendFinderMod implements ModInitializer {
    public static final String MOD_ID = "friend-finder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Friend Finder Mod initialized!");
    }
} 