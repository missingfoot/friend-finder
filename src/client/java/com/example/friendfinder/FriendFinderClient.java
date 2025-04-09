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
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Friend Finder Client - Shows the direction to your friends
 */
public class FriendFinderClient implements ClientModInitializer {
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
	
	// Keybinding
	private KeyBinding toggleKey;

	@Override
	public void onInitializeClient() {
		// Register keybinding (default: I key)
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Toggle Friend Finder",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			"Friend Finder"
		));
		
		// Register key press handler
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				isEnabled = !isEnabled;
				
				// Clear the display when disabled
				if (!isEnabled && client.player != null) {
					client.player.sendMessage(Text.literal(""), true);
				}
			}
		});
		
		// Register HUD rendering
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			if (isEnabled) {
				renderFriendDirection(drawContext);
			}
		});
	}

	private void renderFriendDirection(Object drawContext) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		
		if (player == null || client.world == null) return;

		// Get the nearest player
		PlayerEntity nearestFriend = findNearestPlayer(player);
		
		if (nearestFriend != null) {
			// Calculate direction vector
			Vec3d playerPos = player.getPos();
			Vec3d friendPos = nearestFriend.getPos();
			Vec3d direction = friendPos.subtract(playerPos);
			
			// Check total distance
			double totalDistance = Math.sqrt(player.squaredDistanceTo(nearestFriend));
			
			// Initialize values
			int relativeAngleInt = 0;
			int horizontalDistanceInt = 0;
			int heightDifferenceInt = 0;
			String heightSign = "";
			boolean sameHeight = true; // Default to true
			
			// Only calculate values if players are not on the same block
			if (totalDistance > SAME_BLOCK_THRESHOLD) {
				// Calculate the absolute angle in the world
				double worldAngle = Math.toDegrees(Math.atan2(direction.x, direction.z));
				
				// Get the player's current look direction (yaw)
				float playerYaw = player.getYaw();
				
				// Normalize and invert player yaw to match our coordinate system
				// Minecraft's yaw: 0 = south, 90 = west, 180 = north, 270 = east
				double normalizedPlayerYaw = (360 - playerYaw) % 360;
				
				// Calculate the relative angle (how many degrees to turn to face friend)
				double relativeAngle = (worldAngle - normalizedPlayerYaw) % 360;
				
				// Make sure the angle is between -180 and 180 degrees
				// This makes it more intuitive: negative = turn left, positive = turn right
				if (relativeAngle > 180) relativeAngle -= 360;
				if (relativeAngle < -180) relativeAngle += 360;
				
				// Handle angles near zero to prevent -0°
				if (Math.abs(relativeAngle) < ZERO_THRESHOLD) {
					relativeAngle = 0;
				}
				
				// Calculate horizontal distance (ignoring Y axis)
				double horizontalDistance = Math.sqrt(
                    Math.pow(direction.x, 2) + Math.pow(direction.z, 2)
                );
				
				// Handle horizontal distances near zero
				if (horizontalDistance < ZERO_THRESHOLD) {
					horizontalDistance = 0;
				}
                
                // Calculate height difference (Y axis)
                double heightDifference = direction.y;
				
				// Convert to integers
				relativeAngleInt = (int)Math.round(relativeAngle);
				horizontalDistanceInt = (int)Math.round(horizontalDistance);
                
                // Handle height information
                if (Math.abs(heightDifference) < SAME_HEIGHT_THRESHOLD) {
                    // Players are at the same height
                    sameHeight = true;
                } else {
                    // Players are at different heights
                    sameHeight = false;
                    
                    // Check for values very close to zero
                    if (Math.abs(heightDifference) < ZERO_THRESHOLD) {
                        heightDifferenceInt = 0;
                        sameHeight = true;
                    }
                    // Clear height difference - player is above friend
                    else if (heightDifference > 0) {
                        heightDifferenceInt = (int)Math.round(heightDifference);
                        heightSign = "+";
                    } 
                    // Clear height difference - player is below friend
                    else {
                        heightDifferenceInt = (int)Math.round(Math.abs(heightDifference));
                        heightSign = "-";
                    }
                }
			}
			
			// Create formatted text components with colors
			Text nameText = Text.literal(nearestFriend.getName().getString()).setStyle(Style.EMPTY.withColor(USERNAME_COLOR));
			
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
			// No friend found
			player.sendMessage(Text.literal("No players found"), true);
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
}