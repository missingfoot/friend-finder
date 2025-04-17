package com.example.friendfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;
import net.minecraft.block.Block;
import java.util.HashSet;

/**
 * Friend Finder Client - Shows the direction to your friends
 */
public class FriendFinderClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("FriendFinder");
	private static FriendFinderClient instance;

	public static FriendFinderClient getInstance() {
		return instance;
	}

	// Trackable target class to handle both players and waypoints
	private static class TrackableTarget {
		private final Vec3d position;
		private final String name;
		private final String dimension;
		private final boolean isPlayer;
		private final UUID id;
		private final BlockPos blockPos;
		private final Block block;

		public TrackableTarget(PlayerEntity player) {
			this.position = player.getPos();
			this.name = player.getName().getString();
			this.dimension = player.getWorld().getRegistryKey().getValue().toString();
			this.isPlayer = true;
			this.id = player.getUuid();
			this.blockPos = null;
			this.block = null;
		}

		public TrackableTarget(BlockPos pos, String blockName, String dimension) {
			this.position = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			this.name = blockName;
			this.dimension = dimension;
			this.isPlayer = false;
			this.id = UUID.randomUUID();
			this.blockPos = pos;
			this.block = MinecraftClient.getInstance().world.getBlockState(pos).getBlock();
		}

		public Vec3d getPosition() { return position; }
		public String getName() { return name; }
		public String getDimension() { return dimension; }
		public boolean isPlayer() { return isPlayer; }
		public UUID getId() { return id; }
		public BlockPos getBlockPos() { return blockPos; }
		public Block getBlock() { return block; }
	}

	private static final int TEXT_COLOR = 0xFFFFFF; // White color
	private static final double SAME_BLOCK_THRESHOLD = 1.0; // Distance threshold to consider players on the same block
	private static final double SAME_HEIGHT_THRESHOLD = 0.5; // Threshold to consider players at the same height
	private static final double ZERO_THRESHOLD = 0.1; // Threshold to consider a value as zero
	
	// Colors for different components
	private static final Formatting USERNAME_COLOR = Formatting.AQUA;      // Aqua for username
	private static final Formatting ANGLE_COLOR = Formatting.YELLOW;       // Yellow for angle
	private static final Formatting DISTANCE_COLOR = Formatting.GREEN;     // Green for distance
	private static final Formatting HEIGHT_COLOR = Formatting.LIGHT_PURPLE; // Purple for height
	
	// Toggle state - off by default
	private boolean isEnabled = false;
	
	// Last display text (to clear when disabled)
	private String lastDisplayText = "";
	
	// Keybindings
	private KeyBinding toggleKey;
	private KeyBinding cycleTargetKey;
	private KeyBinding addWaypointKey;
	
	// New fields for target management
	private List<TrackableTarget> targets = new ArrayList<>();
	private int currentTargetIndex = 0;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Friend Finder Mod...");
		instance = this;
		
		// Register keybindings
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Toggle Friend Finder",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			"Friend Finder"
		));
		
		cycleTargetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Cycle Target",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_N,
			"Friend Finder"
		));

		addWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Add Waypoint",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_M,
			"Friend Finder"
		));
		
		LOGGER.info("Registered keybindings: I (toggle), N (cycle), M (waypoint)");
		
		// Register key press handler
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LOGGER.info("Client tick event triggered");
			
			while (toggleKey.wasPressed()) {
				LOGGER.info("Toggle key pressed!");
				isEnabled = !isEnabled;
				
				// Clear the display when disabled
				if (!isEnabled && client.player != null) {
					client.player.sendMessage(Text.literal(""), true);
				}
			}
			
			// Handle target cycling
			while (cycleTargetKey.wasPressed() && isEnabled) {
				LOGGER.info("Cycle key pressed!");
				cycleTarget();
			}

			// Handle waypoint creation
			while (addWaypointKey.wasPressed() && isEnabled) {
				LOGGER.info("Waypoint key pressed!");
				addWaypoint();
			}
			
			// Update targets list
			if (isEnabled && client.player != null) {
				updateTargets();
			}
		});
		
		// Register HUD rendering
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			if (isEnabled) {
				renderFriendDirection(drawContext);
			}
		});
		
		LOGGER.info("Friend Finder Mod initialized!");
	}

	private void renderFriendDirection(Object drawContext) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		
		if (player == null || client.world == null) return;

		// Get the current target
		TrackableTarget currentTarget = null;
		if (!targets.isEmpty()) {
			currentTarget = targets.get(currentTargetIndex);
		}
		
		if (currentTarget != null) {
			// Calculate direction vector
			Vec3d playerPos = player.getPos();
			Vec3d targetPos = currentTarget.getPosition();
			Vec3d direction = targetPos.subtract(playerPos);
			
			// Check total distance
			double totalDistance = Math.sqrt(
				Math.pow(direction.x, 2) + 
				Math.pow(direction.y, 2) + 
				Math.pow(direction.z, 2)
			);
			
			// Initialize values
			int relativeAngleInt = 0;
			int horizontalDistanceInt = 0;
			int heightDifferenceInt = 0;
			String heightSign = "";
			boolean sameHeight = true;
			
			// Only calculate values if not on the same block
			if (totalDistance > SAME_BLOCK_THRESHOLD) {
				// Calculate the absolute angle in the world
				double worldAngle = Math.toDegrees(Math.atan2(direction.x, direction.z));
				
				// Get the player's current look direction (yaw)
				float playerYaw = player.getYaw();
				
				// Normalize and invert player yaw to match our coordinate system
				double normalizedPlayerYaw = (360 - playerYaw) % 360;
				
				// Calculate the relative angle
				double relativeAngle = (worldAngle - normalizedPlayerYaw) % 360;
				
				// Make sure the angle is between -180 and 180 degrees
				if (relativeAngle > 180) relativeAngle -= 360;
				if (relativeAngle < -180) relativeAngle += 360;
				
				// Handle angles near zero
				if (Math.abs(relativeAngle) < ZERO_THRESHOLD) {
					relativeAngle = 0;
				}
				
				// Calculate horizontal distance
				double horizontalDistance = Math.sqrt(
					Math.pow(direction.x, 2) + Math.pow(direction.z, 2)
				);
				
				// Handle horizontal distances near zero
				if (horizontalDistance < ZERO_THRESHOLD) {
					horizontalDistance = 0;
				}
				
				// Calculate height difference
				double heightDifference = direction.y;
				
				// Convert to integers
				relativeAngleInt = (int)Math.round(relativeAngle);
				horizontalDistanceInt = (int)Math.round(horizontalDistance);
				
				// Handle height information
				if (Math.abs(heightDifference) < SAME_HEIGHT_THRESHOLD) {
					sameHeight = true;
				} else {
					sameHeight = false;
					
					if (Math.abs(heightDifference) < ZERO_THRESHOLD) {
						heightDifferenceInt = 0;
						sameHeight = true;
					}
					else if (heightDifference > 0) {
						heightDifferenceInt = (int)Math.round(heightDifference);
						heightSign = "+";
					} 
					else {
						heightDifferenceInt = (int)Math.round(Math.abs(heightDifference));
						heightSign = "-";
					}
				}
			}
			
			// Create formatted text components with colors
			Text nameText = Text.literal(currentTarget.getName()).setStyle(Style.EMPTY.withColor(USERNAME_COLOR));
			
			// Special case for angle of exactly 0 or -0
			String angleValue = relativeAngleInt == 0 ? "0°" : relativeAngleInt + "°";
			Text angleText = Text.literal(angleValue).setStyle(Style.EMPTY.withColor(ANGLE_COLOR));
			
			// Special case for horizontal distance of exactly 0
			String distanceValue = horizontalDistanceInt == 0 ? "0" : "-" + horizontalDistanceInt;
			Text distanceText = Text.literal(distanceValue).setStyle(Style.EMPTY.withColor(DISTANCE_COLOR));
			
			// Height text (only created if not at same height)
			Text heightText = null;
			if (!sameHeight) {
				String heightValue = heightDifferenceInt == 0 ? "0" : heightSign + heightDifferenceInt;
				heightText = Text.literal(heightValue).setStyle(Style.EMPTY.withColor(HEIGHT_COLOR));
			}
			
			// Combine components into a final message
			Text finalText;
			
			// Display different text formats depending on position
			if (totalDistance <= SAME_BLOCK_THRESHOLD) {
				// On the same block
				Text zeroAngle = Text.literal("0°").setStyle(Style.EMPTY.withColor(ANGLE_COLOR));
				Text zeroDistance = Text.literal("0").setStyle(Style.EMPTY.withColor(DISTANCE_COLOR));
				
				finalText = Text.empty()
					.append(nameText)
					.append(" ")
					.append(zeroAngle)
					.append(" ")
					.append(zeroDistance);
			} else if (sameHeight) {
				// Normal distance but same height
				finalText = Text.empty()
					.append(nameText)
					.append(" ")
					.append(angleText)
					.append(" ")
					.append(distanceText);
			} else {
				// Different height
				finalText = Text.empty()
					.append(nameText)
					.append(" ")
					.append(angleText)
					.append(" ")
					.append(distanceText)
					.append(" ")
					.append(heightText);
			}
			
			// Display the text
			player.sendMessage(finalText, true);
		} else {
			// No target found
			player.sendMessage(Text.literal("No target selected").formatted(Formatting.RED), true);
		}
	}

	private PlayerEntity findNearestPlayer(PlayerEntity player) {
		PlayerEntity nearest = null;
		double minDistance = Double.MAX_VALUE;
		
		for (PlayerEntity otherPlayer : player.getWorld().getPlayers()) {
			if (otherPlayer != player) {
				double distance = player.squaredDistanceTo(otherPlayer);
				if (distance < minDistance) {
					minDistance = distance;
					nearest = otherPlayer;
				}
			}
		}
		
		return nearest;
	}

	private void updateTargets() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;

		// Remove all player targets first
		targets.removeIf(target -> target.isPlayer());
		
		LOGGER.info("Updating targets. Current targets before update: {}", targets.stream().map(TrackableTarget::getName).collect(Collectors.joining(", ")));

		// Add all players except the local player
		for (PlayerEntity player : client.world.getPlayers()) {
			if (player != client.player) {
				targets.add(new TrackableTarget(player));
			}
		}

		LOGGER.info("Targets after update: {}", targets.stream().map(TrackableTarget::getName).collect(Collectors.joining(", ")));
		LOGGER.info("Current target index: {}", currentTargetIndex);
		
		// If we have targets but current index is invalid, reset it
		if (!targets.isEmpty() && (currentTargetIndex < 0 || currentTargetIndex >= targets.size())) {
			LOGGER.info("Resetting invalid target index");
			currentTargetIndex = 0;
		}
	}

	private void cycleTarget() {
		if (targets.isEmpty()) {
			LOGGER.info("No targets available to cycle through");
			return;
		}
		
		LOGGER.info("Current targets before cycling: {}", targets.stream().map(TrackableTarget::getName).collect(Collectors.joining(", ")));
		LOGGER.info("Current target index before cycling: {}", currentTargetIndex);
		
		currentTargetIndex = (currentTargetIndex + 1) % targets.size();
		TrackableTarget newTarget = targets.get(currentTargetIndex);
		
		LOGGER.info("New target after cycling: {} at index {}", newTarget.getName(), currentTargetIndex);
	}

	private String generateUniqueWaypointName(String baseName) {
		int count = 0;
		String newName = baseName;
		boolean nameExists;
		
		do {
			final String currentName = newName;
			nameExists = false;
			
			// Check if this name exists in any non-player target
			for (TrackableTarget target : targets) {
				if (!target.isPlayer() && target.getName().equals(currentName)) {
					nameExists = true;
					break;
				}
			}
			
			if (nameExists) {
				count++;
				newName = baseName + " " + count;
			}
		} while (nameExists);
		
		return newName;
	}

	private void addWaypoint() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return;

		HitResult hit = client.crosshairTarget;
		if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
			LOGGER.info("No block targeted for waypoint");
			return;
		}

		BlockHitResult blockHit = (BlockHitResult) hit;
		BlockPos pos = blockHit.getBlockPos();
		BlockState state = client.world.getBlockState(pos);
		String blockName = state.getBlock().getName().getString();

		LOGGER.info("Adding waypoint at position: {}, block: {}", pos, blockName);
		
		TrackableTarget waypoint = new TrackableTarget(
			pos,
			generateUniqueWaypointName(blockName),
			client.player.getWorld().getRegistryKey().getValue().toString()
		);

		targets.add(waypoint);
		currentTargetIndex = targets.size() - 1;
		
		LOGGER.info("Waypoint added. Total targets: {}, Current index: {}", targets.size(), currentTargetIndex);
		LOGGER.info("All targets after adding: {}", targets.stream().map(TrackableTarget::getName).collect(Collectors.joining(", ")));
	}

	public void removeWaypoint(BlockPos pos) {
		LOGGER.info("Attempting to remove waypoint at position: {}", pos);
		
		int initialSize = targets.size();
		targets.removeIf(target -> {
			if (!target.isPlayer() && target.getBlockPos() != null) {
				// Check if the position matches
				boolean positionMatches = target.getBlockPos().equals(pos);
				
				// Also check if the block at this position is air or different
				if (positionMatches) {
					BlockState currentState = MinecraftClient.getInstance().world.getBlockState(pos);
					Block currentBlock = currentState.getBlock();
					Block targetBlock = target.getBlock();
					
					LOGGER.info("Checking waypoint at {}: Current block = {}, Target block = {}, Is air = {}", 
						pos, currentBlock.getName().getString(), targetBlock.getName().getString(), currentState.isAir());
					
					// Remove if block is air or different
					return currentState.isAir() || !currentBlock.equals(targetBlock);
				}
			}
			return false;
		});
		
		int removedCount = initialSize - targets.size();
		LOGGER.info("Removed {} waypoints at position {}", removedCount, pos);
		
		// If we removed any waypoints and have remaining targets, ensure current index is valid
		if (removedCount > 0) {
			if (targets.isEmpty()) {
				currentTargetIndex = 0;
				LOGGER.info("No targets remaining after waypoint removal");
			} else if (currentTargetIndex >= targets.size()) {
				currentTargetIndex = targets.size() - 1;
				LOGGER.info("Adjusted current target index to {} after waypoint removal", currentTargetIndex);
			}
		}
	}

	public void onInput(MinecraftClient client) {
		if (client.player == null) return;
		
		// Handle waypoint addition
		if (addWaypointKey.wasPressed()) {
			LOGGER.info("Waypoint key pressed!");
			
			// Get the block the player is looking at
			HitResult hit = client.crosshairTarget;
			LOGGER.info("Hit result type: " + (hit != null ? hit.getType() : "null"));
			
			if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
				BlockHitResult blockHit = (BlockHitResult)hit;
				BlockPos pos = blockHit.getBlockPos();
				BlockState state = client.world.getBlockState(pos);
				
				LOGGER.info("Block position: " + pos);
				LOGGER.info("Block state: " + state);
				
				// Create a waypoint for this block
				TrackableTarget waypoint = new TrackableTarget(
					pos,
					state.getBlock().getName().getString(),
					"waypoint"
				);
				
				// Add to targets list
				targets.add(waypoint);
				
				// Set as current target
				currentTargetIndex = targets.size() - 1;
				
				// Show confirmation message
				client.player.sendMessage(
					Text.literal("Added waypoint: " + waypoint.getName())
						.formatted(Formatting.YELLOW),
					true
				);
				
				// Debug messages
				LOGGER.info("=== WAYPOINT ADDED ===");
				LOGGER.info("Waypoint name: " + waypoint.getName());
				LOGGER.info("Waypoint position: " + pos);
				LOGGER.info("Total targets: " + targets.size());
				LOGGER.info("Current target index: " + currentTargetIndex);
				LOGGER.info("====================");
			} else {
				LOGGER.info("No valid block target found!");
			}
		}
		
		// Handle target cycling
		if (cycleTargetKey.wasPressed()) {
			LOGGER.info("Cycle key pressed!");
			LOGGER.info("Current targets size: " + targets.size());
			
			if (!targets.isEmpty()) {
				currentTargetIndex = (currentTargetIndex + 1) % targets.size();
				TrackableTarget target = targets.get(currentTargetIndex);
				
				LOGGER.info("=== TARGET CYCLED ===");
				LOGGER.info("New target name: " + target.getName());
				LOGGER.info("New target index: " + currentTargetIndex);
				LOGGER.info("===================");
				
				client.player.sendMessage(
					Text.literal("Now tracking: " + target.getName())
						.formatted(Formatting.GREEN),
					true
				);
			} else {
				LOGGER.info("No targets available to cycle!");
			}
		}
	}

	public void onClientTick(MinecraftClient client) {
		if (client.player == null || client.world == null || !isEnabled) return;

		// Only update player targets when necessary (e.g., when players join/leave)
		boolean playersChanged = false;
		List<PlayerEntity> currentPlayers = client.world.getPlayers();
		List<UUID> currentPlayerIds = currentPlayers.stream()
			.filter(p -> p != client.player)
			.map(PlayerEntity::getUuid)
			.collect(Collectors.toList());
		
		// Check if player list has changed
		if (targets.stream()
			.filter(TrackableTarget::isPlayer)
			.map(TrackableTarget::getId)
			.collect(Collectors.toSet())
			.equals(new HashSet<>(currentPlayerIds))) {
			playersChanged = true;
		}

		if (playersChanged) {
			// Remove all player targets first
			targets.removeIf(TrackableTarget::isPlayer);
			
			// Add all players except the client player
			for (PlayerEntity player : currentPlayers) {
				if (player != client.player) {
					targets.add(new TrackableTarget(player));
				}
			}
		}

		// Check waypoints only if we have any
		if (!targets.isEmpty()) {
			List<TrackableTarget> waypointsToRemove = new ArrayList<>();
			for (TrackableTarget target : targets) {
				if (!target.isPlayer()) {
					BlockPos pos = target.getBlockPos();
					if (pos != null) {
						// Cache the block state check
						BlockState state = client.world.getBlockState(pos);
						if (state.isAir() || !state.getBlock().equals(target.getBlock())) {
							waypointsToRemove.add(target);
						}
					}
				}
			}
			
			if (!waypointsToRemove.isEmpty()) {
				targets.removeAll(waypointsToRemove);
				if (!targets.isEmpty() && currentTargetIndex >= targets.size()) {
					currentTargetIndex = targets.size() - 1;
				}
			}
		}

		// Handle key presses (moved to end to prioritize updates)
		if (toggleKey.wasPressed()) {
			isEnabled = !isEnabled;
		}

		if (isEnabled) {
			if (cycleTargetKey.wasPressed() && !targets.isEmpty()) {
				currentTargetIndex = (currentTargetIndex + 1) % targets.size();
			}

			if (addWaypointKey.wasPressed()) {
				addWaypoint();
			}
		}
	}

	public boolean isEnabled() {
		return isEnabled;
	}
}