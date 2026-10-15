# swap

A Minecraft mod that shuffles what everything drops. Mine a diamond ore and you might get bread, kill a zombie and you might get a saddle.

## What it does

- Every block, mob and chest loot item drops a different, random item.
- The shuffle comes from the world seed: the same block always gives the same item in a world, and every item can still be obtained from exactly one source.
- Press **H** to open the discovery screen, which shows the drops you've found so far.
- Custom advancements for finding unusual drops.
- `/randomdrops query <item>` shows what an item drops, and `/randomdrops query <item> reverse` shows where to get it.

| Game rule | Default | Effect |
| --- | --- | --- |
| `randomdrops:shared_discovery` | `true` | All players share one list of discovered drops |
| `randomdrops:recipe_randomize` | `false` | Also shuffle crafting, smelting and brewing results |

## Install

Install it on the server and on every player's game.

1. Install [Fabric](https://fabricmc.net/use/) (Minecraft 26.2) with [Fabric API](https://modrinth.com/mod/fabric-api).
2. Build the mod with `./gradlew build` (needs Java 25+), or download the jar from the latest [build](https://github.com/Ractouf/swap/actions/workflows/build.yml) run.
3. Copy `build/libs/randomdrops-*.jar` into the `mods/` folder.

Built with Java and the Fabric modding API.
