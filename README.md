# Tempest

Tempest is a Minecraft 1.8.9 client built on MCP with OptiFine, made for PvP servers such as Hypixel and for anticheat testing. It has about 120 modules, seven click GUIs, a theme engine, a replay recorder and a lot of HUD customisation.

> Tempest is for learning, anticheat research and private or test servers. Using it on servers that forbid modified clients can get you banned. You use it at your own risk.

## Features

- **Combat:** Aura with many auto-block modes, Velocity, Criticals, Back Track, Lag Range, Tick Base, Target Strafe, Auto Pot, Auto Head and more.
- **Movement:** Speed, Flight, Phase, Long Jump, No Slow, Spider and Step, with modes for Watchdog, Grim, NCP and Vulcan.
- **Player:** Scaffold with several rotation styles, Clutch, Stealer with Chest Aura, Manager, Auto Armor, Bed Nuker and Notebot.
- **Visuals:** Array List (with patterns and Edge Ember glow), Target HUD, watermarks, tab GUIs, ESP, Glow ESP, Name Tags, Tracers, Item Physics, Cosmetics, block-hit animations and Ambience.
- **Click GUIs:** Tempest, Exhibition, Bankroll, Astolfo, Storm, Tiles and Columns. Every one has a Themes page with presets and Array List patterns.
- **Themes:** more than 100 colour presets with speed, wave length, saturation and direction controls, plus a Liquid Glass look.
- **Replays:** record games, watch them back, set up camera paths and render videos (ffmpeg or PNG frames).
- **Accounts:** Microsoft login through the device code flow. Saved logins are encrypted with Windows DPAPI and stay on your PC.
- **Version support:** connect to newer servers through ViaMCP.

## Building

You need **JDK 22** and **Maven**.

```
git clone https://github.com/<your-account>/Tempest-Client.git
cd Tempest-Client
mvn clean package
```

The finished jar is `target/Tempest.jar`.

## Running from an IDE

1. Open the project in IntelliJ IDEA as a Maven project, using JDK 22.
2. Make a run configuration with main class `Start`.
3. Set the working directory to a folder of your choice. The game keeps its saves, options and the `Tempest` data folder there.
4. `Start` reads the 1.8.9 assets from `%APPDATA%\.minecraft\assets`, so launch Minecraft 1.8.9 once from the official launcher first.

## Default controls

- **Right Shift** opens the click GUI. You can change the key in Client Settings.
- Commands start with `.` in chat, for example `.bind`, `.config` and `.h` to hide a module from the Array List.

## Data folder

Tempest keeps its own files in `Tempest/` inside the game folder: configs, binds, friends, replays, songs, GIFs and your encrypted accounts. Nothing in it is uploaded anywhere.

## Credits

Tempest started from the [Simp](https://github.com/x0lumie/Simp) client. It also uses or adapts code and assets from these projects, which keep their own licences:

- **Minecraft libraries:** OptiFine, ViaMCP / ViaLoadingBase and ViaVersion.
- **Other clients:** ideas and code adapted from Tenacity, Exhibition, OpenAstolfo, Sigma, Moon, Radium, Rise, Yuri, OpenMyau, OpenJade, Flux, Opal, Virtue, Pulsive, Novoline and Exire.
- **[ReplayMod](https://github.com/ReplayModding/ReplayMod) (GPL):** the replay recorder and viewer.
- **[WaveyCapes](https://github.com/tr7zw/WaveyCapes):** cape physics, used under the tr7zw Protective License (non-commercial).
- **Animation:** GeckoLib easing functions.
- **Fonts:** Lato (SIL Open Font License), Manrope and others.
- **3D models:** the KayKit character models (CC0).

## Licence

See [LICENSE](LICENSE). Parts adapted from other projects stay under their original licences, as listed in Credits.
