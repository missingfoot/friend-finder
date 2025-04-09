# Friend Finder Mod

A simple Minecraft mod that helps you locate other players on your server using a compass-like directional indicator.

## Features

- **Player Direction**: Shows the direction to the nearest player
- **Distance Display**: Shows exact block distance to the target player
- **Height Indicator**: Displays height difference with + or - indicators
- **Color Coding**: Different colors for distance and height values for better readability
- **Toggle System**: Press 'I' key to show/hide the directional information
- **Default State**: Starts disabled by default for non-intrusive gameplay

## Display Format

The mod displays information in the action bar (above your hotbar) in this format:
```
[Direction] [Distance] blocks [Height Difference]
```

- **Direction**: Shows as degrees (e.g., 180°)
- **Distance**: Shows exact block distance in white
- **Height**: Shows height difference with + or - prefix in a different color (hidden when at same height)

## Installation

1. Make sure you have Fabric Loader installed
2. Download the latest version of the mod from the releases page
3. Place the .jar file in your Minecraft mods folder
4. Launch Minecraft with Fabric loader

## Controls

- **I**: Toggle the Friend Finder display on/off
- The mod starts disabled by default

## Requirements

- Minecraft 1.21.5
- Fabric Loader
- Fabric API

## Building from Source

1. Clone the repository
2. Open a terminal in the project directory
3. Run `./gradlew build`
4. Find the built jar file in `build/libs/`

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Feel free to submit issues and enhancement requests! 