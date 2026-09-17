# MaidPatina

A **Minecraft Forge** addon for **[Touhou Little Maid]** that adds two copper-maintenance work modes - **Rust Removal** and **Waxing** - and an **Advanced Honey Gathering** task that guides a bee through pollination and honey delivery.

**English** | [简体中文](doc/i18n/README_zh_cn.md)

| | |
|---|---|
| Mod id | `maidpatina` |
| Minecraft | 1.20.1 |
| Forge | 47.4.22 or newer (loader 47+) |
| Touhou Little Maid | 1.5.3 or newer (Forge / MC 1.20.1) |
| Java | 17 |
| License | [MIT](LICENSE) |

MaidPatina adds no blocks, items or entities of its own. Everything it does is implemented as Touhou Little Maid **maid tasks** plus supporting AI behaviors, so it is safe to add to an existing world.

## Features

### Rust Removal - `maidpatina:rust_removal`

*Icon: iron axe*

* **Tool:** any item in the `maidpatina:rust_removal_tools` item tag, or any item that can perform the Forge `AXE_SCRAPE` tool action (so vanilla axes work out of the box).
* **Behaviour:** while working, the maid scans a 3 block cube around herself, keeps only blocks that are inside her work range, that are reachable by line of sight, and that have a previous oxidation stage (`WeatheringCopper#getPrevious`), walks to the closest one, and scrapes **one oxidation stage** off it.
* Plays the vanilla axe-scrape sound and scrape particles, swings her arm, and consumes one durability from the tool (one whole item if the tool is not damageable).

### Waxing - `maidpatina:waxing`

*Icon: honeycomb*

* **Tool:** `minecraft:honeycomb`, or any item in the `maidpatina:waxing_items` item tag.
* **Behaviour:** the same scan, walk and apply loop as Rust Removal, but for blocks registered in vanilla `HoneycombItem.WAXABLES`.
* **Mod compatibility:** 1.20.1 Create injects its own copper blocks into that vanilla map during common setup, so Create's copper also gets waxed. Same applies to any other mod that does the same.
* Plays the vanilla wax-on sound and particle, and consumes one item.

### Advanced Honey Gathering - `maidpatina:advanced_honey`

*Icon: honey bottle*

This task is driven by what the maid is showing, so it needs three things ready at once:

| Requirement | Where it must be | Condition |
|---|---|---|
| Hive | head display slot, main hand, or off hand | `minecraft:beehive` or `minecraft:bee_nest` |
| Flower | head display slot, main hand, or off hand | any item in `#minecraft:flowers` |
| Harvest tool | maid backpack | shears (Forge `SHEARS_HARVEST`) or `minecraft:glass_bottle` |

Both the flower and the hive are looked up with the priority **head display > main hand > off hand**, and they must end up in **different** slots (an item can never be both). The task UI lists all three conditions, and the maid refuses to start if one is missing.

The work cycle:

1. **Reserve a bee** - the closest eligible bee within 20 blocks. A bee is eligible when it is an adult, alive, not angry, has no stinger, has no target, carries no nectar, accepts the held flower (`Bee#isFood`), and is not reserved by another maid. A reservation lasts 5 seconds if the maid stalls, and one session is capped at 60 seconds.
2. **Pollination (5 s)** - the maid stands still and turns to a fixed yaw while luring the bee onto the flower position: on top of her head for the head slot, or beside the matching hand otherwise. Once the bee has held position for 5 seconds, the maid marks it as nectar-bearing.
3. **Deposit (2 s)** - the maid lures the bee onto the hive position the same way and holds for 2 seconds. She then stores **1 honeycomb** (shears take 1 durability) or **1 honey bottle** (spends one glass bottle) in her backpack, plays the vanilla harvest sound, and the bee gives up its nectar.
4. **Cooldown** - that bee cannot be used again for 5 minutes. If the backpack is full or the harvest tool is gone, no honey is produced and the maid simply starts over.

### Cooperative rust removal + waxing

A waxing maid standing in front of oxidized copper cannot wax it yet, so she asks for help instead of giving up:

* She calls the nearest **Rust Removal maid of the same owner** within 16 blocks. The helper must be in the `WORK` schedule, able to move, able to break that block, and carrying a scraping tool.
* The helper walks over and scrapes **one oxidation stage every 10 ticks (0.5 s)** until the block is clean enough to wax.
* The waxer stays close and watches; once the block is waxable and she is within 3 blocks with line of sight, **she applies the wax herself** - the helper never places the wax.
* The waxer then looks at her helper, swings her off hand, emits heart particles and plays a thank-you sound.
* The link is robust: if no helper can be found, if the request times out after 20 seconds, or if the helper dies, is reassigned or loses her tool, the waxer **finishes the job alone**. Blocks are also reserved, so two waxing maids never fight over the same block.

## Data-driven tool tags

Both tool sets are item tags, so packs and modpacks can extend or replace them without touching the mod:

`data/maidpatina/tags/items/rust_removal_tools.json`

```json
{
  "replace": false,
  "values": [
    { "id": "create:sand_paper", "required": false },
    { "id": "create:red_sand_paper", "required": false }
  ]
}
```

`data/maidpatina/tags/items/waxing_items.json`

```json
{
  "replace": false,
  "values": [
    "minecraft:honeycomb"
  ]
}
```

Because the Forge `AXE_SCRAPE` action is also accepted, a datapack only needs to add exotic tools (like Create's sand paper, which is already listed as an optional entry) - plain axes always work. On top of the tag, Rust Removal accepts any item whose scrape action is enabled.

## Known placeholders

The following are intentional placeholders marked with `TODO` in the source, to be replaced when Touhou Little Maid ships dedicated voices:

* the waxing maid's call-for-help voice (`PatinaCoordinationManager`)
* the Advanced Honey Gathering ambient voice (`AdvancedHoneyTask`)
* the thank-you voice/action for a helper and for the bee (`PatinaCoordinationManager`, `MaidAdvancedHoneyTask`)

[Touhou Little Maid]: https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid
