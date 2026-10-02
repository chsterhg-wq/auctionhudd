AUCTION HUD  (Fabric, Minecraft 1.21.11, client-side only)

BUILD
  1. Install JDK 21 and Gradle 8.14+
  2. In this folder run:  gradle build
  3. Take build/libs/auctionhud-1.0.0.jar (NOT the -sources jar)
  4. Put it in .minecraft/mods with Fabric API 0.141.2+1.21.11, run Fabric Loader 0.18.1+

COMMANDS
  /auction run <item> [time]   e.g. /auction run minecraft:elytra 60   or   ... 2m
  /auction worth <amount>      set the worth by hand (e.g. 385m) if the scan fails
  /auction end | cancel
  /auction debug               echoes chat lines containing "paid"
