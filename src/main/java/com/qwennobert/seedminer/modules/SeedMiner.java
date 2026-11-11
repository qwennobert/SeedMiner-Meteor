package com.qwennobert.seedminer.modules;

import com.github.cubiomes.Cubiomes;
import com.qwennobert.seedminer.SeedMinerAddon;
import com.qwennobert.seedminer.compress.Compressible;
import com.qwennobert.seedminer.compress.Compressor;
import com.qwennobert.seedminer.predict.OreKind;
import com.qwennobert.seedminer.predict.OreScanner;
import com.qwennobert.seedminer.predict.PredictedOre;
import com.qwennobert.seedminer.predict.ScanParams;
import com.qwennobert.seedminer.seed.ResolvedSeed;
import com.qwennobert.seedminer.seed.SeedSource;
import com.qwennobert.seedminer.util.BaritoneControl;
import com.qwennobert.seedminer.util.ToolSelector;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SwingMode;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SeedMiner extends Module {
    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgAutoTool = settings.createGroup("Auto Tool", false);
    private final SettingGroup sgMining = settings.createGroup("Mining", false);
    private final SettingGroup sgAntiDetection = settings.createGroup("Anti-Detection", false);
    private final SettingGroup sgAutoCompress = settings.createGroup("Auto Compress", false);
    private final SettingGroup sgRender = settings.createGroup("Render", false);
    private final SettingGroup sgSeed = settings.createGroup("Seed", false);
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);

    // Targets

    private final Setting<List<Block>> blocks = sgTargets.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Ores to predict and mine. Stone and deepslate variants are treated the same.")
        .defaultValue(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS)
        .filter(OreKind::isSupported)
        .onChanged(v -> invalidate())
        .build()
    );

    private final Setting<Integer> minY = sgTargets.add(new IntSetting.Builder()
        .name("min-y")
        .description("Ignore ores below this height.")
        .defaultValue(-64)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .onChanged(v -> invalidate())
        .build()
    );

    private final Setting<Integer> maxY = sgTargets.add(new IntSetting.Builder()
        .name("max-y")
        .description("Ignore ores above this height.")
        .defaultValue(320)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .onChanged(v -> invalidate())
        .build()
    );

    private final Setting<Boolean> oreVeins = sgTargets.add(new BoolSetting.Builder()
        .name("ore-veins")
        .description("Also predict large copper/iron ore veins (incl. raw ore blocks). Slower to scan.")
        .defaultValue(false)
        .onChanged(v -> invalidate())
        .build()
    );

    private final Setting<Boolean> mineSeenOres = sgTargets.add(new BoolSetting.Builder()
        .name("mine-seen-ores")
        .description("Also mine ores of the selected types that are visible near you, even if the prediction missed them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> serverAntiXray = sgTargets.add(new BoolSetting.Builder()
        .name("server-anti-xray")
        .description("The server may use anti-xray (e.g. Paper). Only blocks that touch air are trusted then, because hidden blocks can be fake ores or fake stone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<VerifyMode> verifyMode = sgTargets.add(new EnumSetting.Builder<VerifyMode>()
        .name("verify")
        .description("Check predictions against loaded chunks. SkipAir: drop positions that are air (caves, already mined). Exact: also drop positions that are not the ore (with server-anti-xray only for blocks that touch air).")
        .defaultValue(VerifyMode.SkipAir)
        .build()
    );

    // Safety

    private final Setting<Boolean> stopWhenFull = sgSafety.add(new BoolSetting.Builder()
        .name("stop-when-full")
        .description("Disable when the drops of the selected ores don't fit into your inventory anymore. Until then, only ores whose drops still fit are mined.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> leaveWhenFull = sgSafety.add(new BoolSetting.Builder()
        .name("leave-when-full")
        .description("Also leave the server when the drops don't fit anymore.")
        .defaultValue(false)
        .visible(stopWhenFull::get)
        .build()
    );

    private final Setting<Integer> timeLimit = sgSafety.add(new IntSetting.Builder()
        .name("time-limit")
        .description("Disable after this many minutes. 0 = no limit.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 240)
        .build()
    );

    private final Setting<Boolean> leaveAtTimeLimit = sgSafety.add(new BoolSetting.Builder()
        .name("leave-at-time-limit")
        .description("Also leave the server when the time limit is reached.")
        .defaultValue(false)
        .visible(() -> timeLimit.get() > 0)
        .build()
    );

    private final Setting<Integer> minHealth = sgSafety.add(new IntSetting.Builder()
        .name("min-health")
        .description("Stop and disable when your health (incl. absorption) drops to this value. 0 = off.")
        .defaultValue(8)
        .range(0, 36)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> requirePickaxe = sgSafety.add(new BoolSetting.Builder()
        .name("require-pickaxe")
        .description("Disable when there is no usable pickaxe in your hotbar: none at all, or all of them worn down to min-durability.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> leaveWhenNoPickaxe = sgSafety.add(new BoolSetting.Builder()
        .name("leave-when-no-pickaxe")
        .description("Also leave the server then.")
        .defaultValue(false)
        .visible(requirePickaxe::get)
        .build()
    );

    private final Setting<Boolean> pauseNearPlayers = sgSafety.add(new BoolSetting.Builder()
        .name("pause-near-players")
        .description("Pause mining while another player is close.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> playerRange = sgSafety.add(new IntSetting.Builder()
        .name("player-range")
        .description("Distance for pause-near-players.")
        .defaultValue(32)
        .range(4, 256)
        .sliderRange(8, 128)
        .visible(pauseNearPlayers::get)
        .build()
    );

    // Auto Tool

    private final Setting<Boolean> autoTool = sgAutoTool.add(new BoolSetting.Builder()
        .name("auto-tool")
        .description("Select the tool for every block Baritone breaks instead of letting Baritone do it. Tools must be in the hotbar.")
        .defaultValue(true)
        .onChanged(v -> applyBaritoneSettings())
        .build()
    );

    private final Setting<Boolean> fortuneOnlyOnOres = sgAutoTool.add(new BoolSetting.Builder()
        .name("fortune-only-on-ores")
        .description("Use fortune pickaxes only on ores and a pickaxe without fortune for everything else, so the fortune pickaxe lasts longer.")
        .defaultValue(true)
        .visible(autoTool::get)
        .build()
    );

    private final Setting<Boolean> fortuneFallback = sgAutoTool.add(new BoolSetting.Builder()
        .name("fortune-fallback")
        .description("If there is no other pickaxe, use the fortune pickaxe for other blocks too. Otherwise the module stops.")
        .defaultValue(false)
        .visible(() -> autoTool.get() && fortuneOnlyOnOres.get())
        .build()
    );

    private final Setting<Integer> minToolDurability = sgAutoTool.add(new IntSetting.Builder()
        .name("min-durability")
        .description("Never use a tool with less durability left than this (percent), so it never breaks. Without a usable pickaxe the module stops (see require-pickaxe in Safety).")
        .defaultValue(5)
        .range(0, 50)
        .sliderRange(0, 25)
        .build()
    );

    private final Setting<Boolean> repairWithMending = sgAutoTool.add(new BoolSetting.Builder()
        .name("repair-with-mending")
        .description("When a pickaxe with Mending wears down below repair-below, ores nearby that give experience (nether quartz, coal, lapis, redstone, diamond, emerald) are mined first, with that pickaxe in hand, until it is back at repair-until. Ancient debris gives no experience, so mining it never repairs anything.")
        .defaultValue(true)
        .visible(autoTool::get)
        .build()
    );

    private final Setting<Integer> repairBelow = sgAutoTool.add(new IntSetting.Builder()
        .name("repair-below")
        .description("Start repairing when the pickaxe has less durability left than this (percent).")
        .defaultValue(20)
        .range(1, 95)
        .sliderRange(5, 50)
        .visible(() -> autoTool.get() && repairWithMending.get())
        .build()
    );

    private final Setting<Integer> repairUntil = sgAutoTool.add(new IntSetting.Builder()
        .name("repair-until")
        .description("Repair until the pickaxe has this much durability left (percent), then go back to mining.")
        .defaultValue(60)
        .range(5, 100)
        .sliderRange(20, 100)
        .visible(() -> autoTool.get() && repairWithMending.get())
        .build()
    );

    // Mining

    private final Setting<Boolean> autoMine = sgMining.add(new BoolSetting.Builder()
        .name("auto-mine")
        .description("Let Baritone walk to and mine the predicted ores. Disable to only highlight them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> collectDrops = sgMining.add(new BoolSetting.Builder()
        .name("collect-drops")
        .description("Walk to the drops of mined ores before moving on, so none are left behind.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> collectRadius = sgMining.add(new IntSetting.Builder()
        .name("collect-radius")
        .description("How far away (blocks) drops are collected.")
        .defaultValue(6)
        .range(1, 16)
        .sliderRange(2, 12)
        .visible(collectDrops::get)
        .build()
    );

    private final Setting<Integer> collectTimeout = sgMining.add(new IntSetting.Builder()
        .name("collect-timeout")
        .description("Give up on a drop after this many seconds.")
        .defaultValue(8)
        .range(1, 60)
        .sliderRange(2, 20)
        .visible(collectDrops::get)
        .build()
    );

    private final Setting<Double> veinPriority = sgMining.add(new DoubleSetting.Builder()
        .name("vein-priority")
        .description("How much groups of ores are preferred over single ores. 0 = always the nearest ore, 0.5 = a group of 4 may be twice as far away, 1 = the group with the least walking per ore.")
        .defaultValue(0.5)
        .range(0, 3)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<Double> verticalCost = sgMining.add(new DoubleSetting.Builder()
        .name("vertical-cost")
        .description("How much height differences count when choosing the next ore. 1 = plain 3D distance, higher values prefer ores on your level.")
        .defaultValue(1.5)
        .range(0, 10)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Integer> maxDistance = sgMining.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Ignore ores that are further away than this horizontally (blocks).")
        .defaultValue(96)
        .range(8, 512)
        .sliderRange(16, 256)
        .build()
    );

    private final Setting<Integer> targetTimeout = sgMining.add(new IntSetting.Builder()
        .name("target-timeout")
        .description("Skip an ore if you get no closer to it for this many seconds.")
        .defaultValue(60)
        .range(5, 600)
        .sliderRange(10, 300)
        .build()
    );

    private final Setting<Integer> retries = sgMining.add(new IntSetting.Builder()
        .name("path-retries")
        .description("How often to retry an ore when Baritone gives up on the path without getting closer.")
        .defaultValue(2)
        .range(0, 10)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Boolean> disableWhenDone = sgMining.add(new BoolSetting.Builder()
        .name("disable-when-done")
        .description("Disable the module when no ores are left. Otherwise it waits for new chunks.")
        .defaultValue(false)
        .build()
    );

    // Anti-Detection

    private final Setting<Boolean> antiDetection = sgAntiDetection.add(new BoolSetting.Builder()
        .name("anti-detection")
        .description("Makes the mining pattern less obvious: limits ores per minute, adds random pauses and stone detours.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxOresPerMinute = sgAntiDetection.add(new IntSetting.Builder()
        .name("max-ores-per-minute")
        .description("Mine at most this many ores within any 60 seconds. 0 = no limit.")
        .defaultValue(8)
        .range(0, 120)
        .sliderRange(0, 30)
        .visible(antiDetection::get)
        .build()
    );

    private final Setting<Boolean> randomPause = sgAntiDetection.add(new BoolSetting.Builder()
        .name("random-pause")
        .description("Wait a random time after each ore before going to the next one.")
        .defaultValue(true)
        .visible(antiDetection::get)
        .build()
    );

    private final Setting<Double> pauseMin = sgAntiDetection.add(new DoubleSetting.Builder()
        .name("pause-min")
        .description("Shortest pause in seconds.")
        .defaultValue(1.5)
        .range(0, 120)
        .sliderRange(0, 15)
        .visible(() -> antiDetection.get() && randomPause.get())
        .build()
    );

    private final Setting<Double> pauseMax = sgAntiDetection.add(new DoubleSetting.Builder()
        .name("pause-max")
        .description("Longest pause in seconds.")
        .defaultValue(6)
        .range(0, 120)
        .sliderRange(0, 30)
        .visible(() -> antiDetection.get() && randomPause.get())
        .build()
    );

    private final Setting<Boolean> stoneDetours = sgAntiDetection.add(new BoolSetting.Builder()
        .name("stone-detours")
        .description("Sometimes mine a random stone block nearby after an ore instead of heading straight to the next one.")
        .defaultValue(false)
        .visible(antiDetection::get)
        .build()
    );

    private final Setting<Integer> detourChance = sgAntiDetection.add(new IntSetting.Builder()
        .name("detour-chance")
        .description("Chance in percent to make a stone detour after an ore.")
        .defaultValue(25)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(() -> antiDetection.get() && stoneDetours.get())
        .build()
    );

    private final Setting<Integer> detourDistance = sgAntiDetection.add(new IntSetting.Builder()
        .name("detour-distance")
        .description("How far away (blocks) the stone for a detour may be.")
        .defaultValue(4)
        .range(2, 8)
        .sliderRange(2, 8)
        .visible(() -> antiDetection.get() && stoneDetours.get())
        .build()
    );

    // Auto Compress

    private final Setting<Boolean> autoCompress = sgAutoCompress.add(new BoolSetting.Builder()
        .name("auto-compress")
        .description("When the drops of the selected ores don't fit into the inventory anymore, place a crafting table from your inventory, craft blocks with the recipe book (e.g. diamond blocks) and pick the table up again.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> compressItems = sgAutoCompress.add(new ItemListSetting.Builder()
        .name("compress-items")
        .description("Which items are crafted into blocks.")
        .defaultValue(Items.DIAMOND)
        .filter(Compressible::supports)
        .visible(autoCompress::get)
        .build()
    );

    private final Setting<Integer> compressAtFreeSlots = sgAutoCompress.add(new IntSetting.Builder()
        .name("compress-at-free-slots")
        .description("Crafting starts when the drops of the selected ores don't fit anymore (like stop-when-full). Above 0 it also starts earlier, as soon as fewer than this many slots are empty.")
        .defaultValue(0)
        .range(0, 9)
        .sliderRange(0, 9)
        .visible(autoCompress::get)
        .build()
    );

    private final Setting<Boolean> dropJunk = sgAutoCompress.add(new BoolSetting.Builder()
        .name("drop-junk")
        .description("Before crafting, throw out junk (stone, deepslate, gravel, ...) until there is room, keeping one stack of building blocks for Baritone. Otherwise junk lying around is picked up into the freed slots and the crafting table doesn't fit back in. Off: only craft when no junk lies next to you.")
        .defaultValue(true)
        .visible(autoCompress::get)
        .build()
    );

    private final Setting<List<Item>> junkItems = sgAutoCompress.add(new ItemListSetting.Builder()
        .name("junk-items")
        .description("Items that may be thrown out to make room.")
        .defaultValue(
            Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DEEPSLATE, Items.TUFF, Items.DIORITE, Items.GRANITE,
            Items.ANDESITE, Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.POINTED_DRIPSTONE, Items.GRAVEL, Items.FLINT, Items.DIRT,
            Items.SAND, Items.NETHERRACK, Items.BASALT, Items.SMOOTH_BASALT, Items.BLACKSTONE, Items.SOUL_SAND, Items.SOUL_SOIL,
            Items.END_STONE
        )
        .visible(() -> autoCompress.get() && dropJunk.get())
        .build()
    );

    private final Setting<Boolean> showCraftingGui = sgAutoCompress.add(new BoolSetting.Builder()
        .name("show-crafting-gui")
        .description("Show the crafting window while crafting. Off (silent): the window is not drawn, but it is really opened, clicked and closed like a player does.")
        .defaultValue(false)
        .visible(autoCompress::get)
        .build()
    );

    private final Setting<Integer> compressDelayMin = sgAutoCompress.add(new IntSetting.Builder()
        .name("action-delay-min")
        .description("Shortest pause between two actions (placing, opening, clicking, ...) in milliseconds.")
        .defaultValue(100)
        .range(0, 2000)
        .sliderRange(0, 1000)
        .visible(autoCompress::get)
        .build()
    );

    private final Setting<Integer> compressDelayMax = sgAutoCompress.add(new IntSetting.Builder()
        .name("action-delay-max")
        .description("Longest pause between two actions in milliseconds.")
        .defaultValue(300)
        .range(0, 2000)
        .sliderRange(0, 1000)
        .visible(autoCompress::get)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlight predicted ores.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderLimit = sgRender.add(new IntSetting.Builder()
        .name("render-limit")
        .description("Maximum number of highlighted ores (nearest first).")
        .defaultValue(300)
        .range(1, 5000)
        .sliderRange(50, 1000)
        .visible(render::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of predicted ores.")
        .defaultValue(new SettingColor(0, 200, 255, 25))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of predicted ores.")
        .defaultValue(new SettingColor(0, 200, 255, 200))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder()
        .name("target-color")
        .description("Color of the ore that is currently being mined.")
        .defaultValue(new SettingColor(255, 60, 60, 220))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> tracer = sgRender.add(new BoolSetting.Builder()
        .name("tracer")
        .description("Draw a line to the current target.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    // Seed

    private final Setting<SeedSource> seedSource = sgSeed.add(new EnumSetting.Builder<SeedSource>()
        .name("seed-source")
        .description("Where the world seed comes from. Auto: manual seed if set, otherwise SeedMapper (/sm:config Seed set, saved seeds, online database).")
        .defaultValue(SeedSource.Auto)
        .onChanged(v -> invalidate())
        .build()
    );

    private final Setting<String> manualSeed = sgSeed.add(new StringSetting.Builder()
        .name("manual-seed")
        .description("World seed (number or text like in the world creation screen). Leave empty to use the seed from SeedMapper.")
        .defaultValue("")
        .onChanged(v -> invalidate())
        .build()
    );

    private final Setting<Boolean> largeBiomes = sgSeed.add(new BoolSetting.Builder()
        .name("large-biomes")
        .description("Enable if the world was created with the Large Biomes preset.")
        .defaultValue(false)
        .onChanged(v -> invalidate())
        .build()
    );

    // Advanced

    private final Setting<Integer> chunkRadius = sgAdvanced.add(new IntSetting.Builder()
        .name("chunk-radius")
        .description("How many chunks around you are scanned.")
        .defaultValue(4)
        .range(0, 16)
        .sliderRange(0, 12)
        .onChanged(v -> lastScanCenter = null)
        .build()
    );

    private final Setting<Boolean> followPlayer = sgAdvanced.add(new BoolSetting.Builder()
        .name("follow-player")
        .description("Keep scanning new chunks around you while you move.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> configureBaritone = sgAdvanced.add(new BoolSetting.Builder()
        .name("configure-baritone")
        .description("Sets Baritone's allowBreak to true while active (required) and the options below. Restored when disabled.")
        .defaultValue(true)
        .onChanged(v -> applyBaritoneSettings())
        .build()
    );

    private final Setting<Boolean> allowPlace = sgAdvanced.add(new BoolSetting.Builder()
        .name("allow-place")
        .description("Allow Baritone to place blocks (bridging, pillaring, blocking lava).")
        .defaultValue(true)
        .visible(configureBaritone::get)
        .onChanged(v -> applyBaritoneSettings())
        .build()
    );

    private final Setting<Boolean> allowParkour = sgAdvanced.add(new BoolSetting.Builder()
        .name("allow-parkour")
        .description("Allow Baritone to jump over gaps.")
        .defaultValue(false)
        .visible(configureBaritone::get)
        .onChanged(v -> applyBaritoneSettings())
        .build()
    );

    private final OreScanner scanner = new OreScanner();
    private final Map<BlockPos, PredictedOre> ores = new HashMap<>();
    /** Positions that were mined, skipped or found invalid. Kept until the seed/world changes. */
    private final Set<BlockPos> finished = new HashSet<>();
    /** Predicted positions where the real ore block was seen, so that turning into air means it was mined. */
    private final Set<BlockPos> seenOre = new HashSet<>();
    /** The ore kinds and height range of the current scan, used for ores that are seen but were not predicted. */
    private Set<OreKind> selectedKinds = EnumSet.noneOf(OreKind.class);
    private int scanMinY, scanMaxY;
    private final List<PredictedOre> renderList = new ArrayList<>();

    private BaritoneControl baritone;
    private ResolvedSeed seed, resolvedSeed;
    private int seedTimer;
    private ResourceKey<Level> dimension;
    private ChunkPos lastScanCenter;
    private boolean paramsDirty = true;

    private PredictedOre target;
    private int targetTicks, targetRetries, retryWaitTicks, verifyTimer;
    /** Closest the eyes got to the current target, getting closer resets the timeout and the retries. */
    private double targetBestDistance;
    /** Baritone was stopped to break a block directly, so it stopping is not a failed path. */
    private boolean baritoneStopped;
    private int mined, skipped, invalid;

    // Anti-detection state
    private final Random random = new Random();
    /** Real time (ms) of recently mined ores, for the per-minute limit. */
    private final Deque<Long> minedTimes = new ArrayDeque<>();
    private long pauseUntil;
    private BlockPos detourPos;
    private int detourTicks;

    // Drop collection state
    private ItemEntity collectItem;
    private int collectTicks, collectRetries;
    /** Drops that could not be reached, by entity id. */
    private final Set<Integer> ignoredDrops = new HashSet<>();
    /** Ticks left to wait for the drops of the ore that was just mined, the server sends them a moment later. */
    private int dropWaitTicks;
    private static final int DROP_WAIT_TICKS = 20;
    /** The target ore is done, but the follow-up (detour, pause) waits until its drops are collected. */
    private boolean targetDonePending;

    /** Set from the block break event, the module is stopped on the next tick (not in the middle of Baritone's tick). */
    private String toolStop;
    private boolean warnedNoSeed;
    private String status = "Idle";
    private Compressor compressor;
    private boolean wasEating;

    // Mending repair state
    /** Hotbar slot of the Mending pickaxe that is being repaired, -1 while none needs it. */
    private int repairSlot = -1;
    private final Map<BlockPos, PredictedOre> xpOres = new HashMap<>();
    private int xpScanTimer;
    /** When the module was enabled, for the time limit. */
    private long startedAt;

    public SeedMiner() {
        super(SeedMinerAddon.CATEGORY, "seed-miner", "Predicts ores from the world seed (via SeedMapper) and mines them with Baritone.");
    }

    @Override
    public void onActivate() {
        startedAt = System.currentTimeMillis();
        if (!BaritoneUtils.IS_AVAILABLE && autoMine.get()) {
            warning("Baritone (Meteor build) is not installed, ores will only be highlighted.");
        }

        if (BaritoneUtils.IS_AVAILABLE && baritone == null) baritone = new BaritoneControl();
        if (baritone != null && compressor == null) compressor = new Compressor(mc, baritone, message -> info("%s", message), message -> warning("%s", message));
        applyBaritoneSettings();

        warnedNoSeed = false;
        toolStop = null;
        repairSlot = -1;
        xpOres.clear();
        lastScanCenter = null;
        pauseUntil = 0;
        ignoredDrops.clear();
        clearTarget();
        status = "Waiting for seed";
    }

    @Override
    public void onDeactivate() {
        if (compressor != null) compressor.abort();
        if (baritone != null) {
            baritone.stop();
            baritone.restore();
            baritone.setExternalAutoTool(false);
        }
        target = null;
        detourPos = null;
        collectItem = null;
        status = "Idle";
    }

    /** Silent auto compress: the crafting window is open, but not drawn. */
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (compressor != null && compressor.hidesScreen(event.screen)) event.cancel();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (compressor != null) compressor.reset();
        resetAll();
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (toolStop != null) {
            String reason = toolStop;
            toolStop = null;
            stop(reason, requirePickaxe.get() && leaveWhenNoPickaxe.get());
            return;
        }

        if (!updateParams()) return;

        // Scan chunks around the player
        ChunkPos center = mc.player.chunkPosition();
        if (lastScanCenter == null || (followPlayer.get() && !center.equals(lastScanCenter))) {
            scanner.scanAround(center, chunkRadius.get());
            lastScanCenter = center;
        }

        // Collect finished chunks
        for (OreScanner.ChunkResult result; (result = scanner.poll()) != null; ) {
            for (PredictedOre ore : result.ores()) {
                if (!finished.contains(ore.pos())) ores.put(ore.pos(), ore);
            }
        }

        if (++verifyTimer >= 10) {
            verifyTimer = 0;
            if (mineSeenOres.get()) addSeenOres();
            verifyLoaded();
            updateRenderList();
        }

        if (!autoMine.get() || baritone == null) {
            if (target != null) clearTarget();
            status = ores.isEmpty() ? scanStatus("No ores") : "Highlighting " + ores.size() + " ores";
            return;
        }

        // Let Auto Eat (or you) eat in peace: no breaking, no tool switching, Baritone waits. Otherwise the tool switch
        // interrupts eating every tick and the block keeps being mined with the food in hand.
        if (isEating()) {
            if (!wasEating) {
                wasEating = true;
                mc.gameMode.stopDestroyBlock();
                if (baritone.isWorking()) {
                    baritone.stop();
                    baritoneStopped = true;
                }
            }
            status = "Eating";
            return;
        }
        wasEating = false;

        if (!safetyCheck()) return;

        updateRepair();
        mine();
    }

    private boolean isEating() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat != null && autoEat.isActive() && autoEat.eating) return true;
        return mc.player.isUsingItem() && mc.player.getUseItem().has(DataComponents.FOOD);
    }

    /**
     * Picks the tool right before a block starts breaking, for Baritone and also for blocks you break yourself, and
     * makes sure no tool breaks.
     */
    @EventHandler
    private void onStartBreaking(StartBreakingBlockEvent event) {
        // Breaking while eating would switch away from the food
        if (isEating() || toolStop != null) {
            event.cancel();
            return;
        }

        BlockState state = mc.level.getBlockState(event.blockPos);
        if (state.isAir() || state.getDestroySpeed(mc.level, event.blockPos) == 0) return;

        if (autoTool.get()) {
            int slot = selectTool(state);
            if (slot == ToolSelector.ONLY_FORTUNE) {
                event.cancel();
                toolStop = "Only a fortune pickaxe can mine " + state.getBlock().getName().getString()
                    + " (put another pickaxe in your hotbar or enable fortune-fallback)";
                return;
            }
            if (slot >= 0 && slot != mc.player.getInventory().getSelectedSlot()) InvUtils.swap(slot, false);
        }

        // Whatever is in hand now (picked here, by Baritone or by you) must not break
        ItemStack held = mc.player.getMainHandItem();
        if (ToolSelector.isWorn(held, minToolDurability.get())) {
            event.cancel();
            toolStop = held.getHoverName().getString() + " is worn down to " + ToolSelector.durability(held) + "% (min-durability " + minToolDurability.get() + "%)";
        }
    }

    private int selectTool(BlockState state) {
        OreKind kind = OreKind.of(state.getBlock());

        // While repairing, the Mending pickaxe mines the ores that give experience, and everything else while experience
        // orbs are around: they only repair what is in your hand (or worn) when they reach you
        if (repairSlot != -1 && ((kind != null && kind.experience() >= 1) || xpOrbsNearby())) return repairSlot;

        boolean ore = kind != null && kind.fortuneAffects();
        return ToolSelector.select(mc.player.getInventory(), state, ore, fortuneOnlyOnOres.get(), fortuneFallback.get(), minToolDurability.get());
    }

    /**
     * Notices ores the moment the client learns about them. With anti-xray an ore only becomes visible once it is
     * exposed, and a fast pickaxe breaks it right after, so the periodic check alone could miss it.
     */
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        PredictedOre ore = ores.get(event.pos);
        if (ore == null) return;

        // An ore that is broken was real (you can only reach exposed blocks, and anti-xray shows those truthfully).
        // An ore that appears is only trusted if it is exposed, anti-xray fills hidden blocks with fake ores.
        boolean broken = isGone(event.newState) && ore.kind().matches(event.oldState.getBlock());
        boolean appeared = ore.kind().matches(event.newState.getBlock()) && isTrusted(ore.pos());
        if (broken || appeared) seenOre.add(ore.pos());
    }

    /** Resolves the seed and the scan parameters. Returns false if there is nothing to do yet. */
    private boolean updateParams() {
        // Asking SeedMapper is not free, once per second is plenty
        if (seed == null || paramsDirty || ++seedTimer >= 20) {
            seedTimer = 0;
            resolvedSeed = ResolvedSeed.resolve(mc, seedSource.get(), manualSeed.get(), largeBiomes.get());
        }

        ResolvedSeed resolved = resolvedSeed;
        if (resolved == null) {
            status = "Waiting for seed";
            if (!warnedNoSeed) {
                warning("No seed available. Set manual-seed or use /sm:config Seed set <seed> (SeedMapper).");
                warnedNoSeed = true;
            }
            return false;
        }

        ResourceKey<Level> currentDimension = mc.level.dimension();
        if (!resolved.equals(seed) || currentDimension != dimension) {
            if (seed == null || resolved.seed() != seed.seed()) info("Using seed (highlight)%d(default) from %s.", resolved.seed(), resolved.origin());
            seed = resolved;
            dimension = currentDimension;
            paramsDirty = true;
        }

        if (paramsDirty) {
            paramsDirty = false;

            int cubiomesDimension;
            if (dimension == Level.NETHER) cubiomesDimension = Cubiomes.DIM_NETHER();
            else if (dimension == Level.END) cubiomesDimension = Cubiomes.DIM_END();
            else cubiomesDimension = Cubiomes.DIM_OVERWORLD();

            Set<OreKind> kinds = EnumSet.noneOf(OreKind.class);
            for (Block block : blocks.get()) {
                OreKind kind = OreKind.of(block);
                if (kind != null) kinds.add(kind);
            }

            ScanParams params = new ScanParams(seed.seed(), seed.version(), cubiomesDimension, seed.generatorFlags(),
                kinds, Math.min(minY.get(), maxY.get()), Math.max(minY.get(), maxY.get()), oreVeins.get());
            selectedKinds = kinds;
            scanMinY = params.minY();
            scanMaxY = params.maxY();

            if (scanner.setParams(params)) {
                ores.clear();
                finished.clear();
                seenOre.clear();
                renderList.clear();
                clearTarget();
                lastScanCenter = null;
            }
        }

        return true;
    }

    /**
     * Compares predictions with the real world in loaded chunks. Ores that were seen and are gone now were mined
     * (e.g. by Baritone on the way to another ore), predictions that don't match are dropped depending on the verify mode.
     */
    private void verifyLoaded() {
        VerifyMode mode = verifyMode.get();

        for (Iterator<PredictedOre> it = ores.values().iterator(); it.hasNext(); ) {
            PredictedOre ore = it.next();
            if (ore == target || !isLoaded(ore.pos())) continue;

            BlockState state = mc.level.getBlockState(ore.pos());
            boolean trusted = isTrusted(ore.pos());
            if (ore.kind().matches(state.getBlock())) {
                if (trusted) seenOre.add(ore.pos());
                continue;
            }

            boolean gone = isGone(state);
            boolean drop;
            if (gone && seenOre.contains(ore.pos())) {
                countMined();
                drop = true;
            }
            else {
                // The prediction doesn't know bedrock, positions near the bottom of the world can be unbreakable
                drop = isUnbreakable(state, ore.pos()) || switch (mode) {
                    case Off -> false;
                    case SkipAir -> gone;
                    case Exact -> gone || trusted;
                };
                if (drop) invalid++;
            }

            if (drop) {
                it.remove();
                seenOre.remove(ore.pos());
                finished.add(ore.pos());
            }
        }
    }

    /** Radius (blocks) around the player in which real ore blocks are added as targets. */
    private static final int SEEN_ORE_RADIUS = 5;

    /**
     * Adds ore blocks of the selected kinds near the player that the prediction missed. Without anti-xray the client
     * knows every block, with anti-xray only exposed ones (hidden ones may be fake), which are exactly the ones left
     * next to the tunnel.
     */
    private void addSeenOres() {
        if (selectedKinds.isEmpty()) return;

        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEEN_ORE_RADIUS, -SEEN_ORE_RADIUS, -SEEN_ORE_RADIUS), center.offset(SEEN_ORE_RADIUS, SEEN_ORE_RADIUS, SEEN_ORE_RADIUS))) {
            if (pos.getY() < scanMinY || pos.getY() > scanMaxY || ores.containsKey(pos) || finished.contains(pos)) continue;

            OreKind kind = OreKind.of(mc.level.getBlockState(pos).getBlock());
            if (kind == null || !selectedKinds.contains(kind) || !isTrusted(pos)) continue;

            BlockPos immutable = pos.immutable();
            ores.put(immutable, new PredictedOre(immutable, kind));
            seenOre.add(immutable);
        }
    }

    /**
     * Whether the block the client sees at this position can be believed. Anti-xray only hides blocks that don't
     * touch air (or other see-through blocks) and shows exposed blocks as they really are.
     */
    private boolean isTrusted(BlockPos pos) {
        if (!serverAntiXray.get()) return true;

        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            if (!mc.level.getBlockState(neighbor.setWithOffset(pos, direction)).isSolidRender()) return true;
        }
        return false;
    }

    private static boolean isGone(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    private boolean isUnbreakable(BlockState state, BlockPos pos) {
        return state.getDestroySpeed(mc.level, pos) < 0;
    }

    /** Disables the module and, if wanted, leaves the server the way Meteor's Auto Log does. */
    private void stop(String reason, boolean leave) {
        info("%s, %s.", reason, leave ? "leaving the server" : "stopping");
        toggle();
        if (!leave) return;

        // Otherwise Auto Reconnect would join again right away
        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
        if (autoReconnect.isActive()) autoReconnect.toggle();
        mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal("[Seed Miner] " + reason + ".")));
    }

    private boolean safetyCheck() {
        if (minHealth.get() > 0 && PlayerUtils.getTotalHealth() <= minHealth.get()) {
            error("Health is low, stopping.");
            toggle();
            return false;
        }

        // Crafting blocks to make room is finished first, it only takes a few seconds
        if (compressor.isActive()) {
            status = compressor.tick();
            return false;
        }

        boolean full = !hasRoomForAnyDrop();
        if (autoCompress.get() && (full || freeSlots() < compressAtFreeSlots.get())) {
            Compressor.Options options = compressOptions();
            if (compressor.canStart(options)) {
                baritone.stop();
                baritoneStopped = true;
                collectItem = null;
                compressor.start(options);
                status = "Compressing";
                return false;
            }
        }

        if (stopWhenFull.get() && full) {
            stop("No room left for the drops of the selected ores", leaveWhenFull.get());
            return false;
        }

        if (timeLimit.get() > 0 && System.currentTimeMillis() - startedAt >= timeLimit.get() * 60_000L) {
            stop("Time limit of " + timeLimit.get() + (timeLimit.get() == 1 ? " minute" : " minutes") + " reached", leaveAtTimeLimit.get());
            return false;
        }

        if (requirePickaxe.get() && !hasPickaxe(true)) {
            String reason = hasPickaxe(false) ? "All pickaxes in your hotbar are worn down to min-durability (" + minToolDurability.get() + "%)" : "No pickaxe in your hotbar";
            stop(reason, leaveWhenNoPickaxe.get());
            return false;
        }

        if (pauseNearPlayers.get() && isPlayerNearby()) {
            if (target != null || detourPos != null || collectItem != null) {
                baritone.stop();
                target = null;
                detourPos = null;
                collectItem = null;
            }
            status = "Paused, player nearby";
            return false;
        }

        return true;
    }

    private void mine() {
        BlockHitResult directHit = null;

        if (target != null) {
            BlockPos pos = target.pos();
            boolean loaded = isLoaded(pos);
            BlockState state = loaded ? mc.level.getBlockState(pos) : null;

            boolean trusted = loaded && isTrusted(pos);
            if (trusted && target.kind().matches(state.getBlock())) seenOre.add(pos);

            // Getting closer is progress: the timeout and the path retries only count while you get nowhere
            double distance = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
            if (distance < targetBestDistance - 1) {
                targetBestDistance = distance;
                targetTicks = 0;
                targetRetries = 0;
                retryWaitTicks = 0;
            }

            boolean gone = loaded && isGone(state);
            boolean wrong = loaded && !gone && ((trusted && verifyMode.get() == VerifyMode.Exact && !target.kind().matches(state.getBlock())) || isUnbreakable(state, pos));

            if (gone || wrong) {
                // Only an ore that was really there counts as mined. Otherwise the prediction was wrong
                // (stone, cave, already mined), even if Baritone had to break the block to get there.
                if (gone && seenOre.contains(pos)) {
                    // Ores mined only for their experience don't count
                    if (selectedKinds.contains(target.kind())) countMined();
                    dropWaitTicks = DROP_WAIT_TICKS;
                }
                else invalid++;
                finishTarget();
            }
            else if (loaded && mc.player.onGround() && !touchesFluid(pos) && ((directHit = findReachableFace(pos)) != null || (directHit = findBlockInTheWay(pos)) != null)) {
                // In reach: break it from here, or first the block that hides it. Baritone's goal is to stand inside
                // the ore, which can be impossible (e.g. ore above your head and no blocks to pillar up with).
                // Only while standing, in the middle of a jump (pillaring up) the ore is out of reach again a moment later.
                // Not when water or lava touches the ore, it would flow out towards you. Baritone has its own rules for that.
                if (baritone.isWorking()) {
                    baritone.stop();
                    baritoneStopped = true;
                }
                breakDirectly(directHit);
                if (++targetTicks > targetTimeout.get() * 20) skipNoProgress(pos);
            }
            else if (baritone.wasHijacked()) {
                warning("Baritone was given another goal, pausing. Re-enable the module to continue.");
                baritone.stop();
                toggle();
                return;
            }
            else if (++targetTicks > targetTimeout.get() * 20) skipNoProgress(pos);
            else if (!baritone.isWorking()) {
                // Baritone reached the goal without the block breaking, or could not find a path. If it was stopped
                // here to break a block directly, it simply continues.
                if (baritoneStopped) {
                    baritoneStopped = false;
                    baritone.mine(pos);
                }
                // Right after a teleport Baritone finds no way until the chunks around you have arrived, that's no failure
                else if (!isAreaLoaded(mc.player.blockPosition())) retryWaitTicks = 0;
                // Ask again after a moment (1 s, 2 s, ...)
                else if (++retryWaitTicks >= 20 * (targetRetries + 1)) {
                    retryWaitTicks = 0;
                    // An ore mined only for its experience is given up right away, there are plenty of others
                    boolean xpOnly = !selectedKinds.contains(target.kind());
                    if (!xpOnly && targetRetries++ < retries.get()) baritone.mine(pos);
                    else {
                        if (!xpOnly) {
                            warning("Baritone could not reach ore at %s, skipping.", pos.toShortString());
                            skipped++;
                        }
                        finishTarget();
                    }
                }
            }

            if (target == null) {
                targetDonePending = true;
                directHit = null;
            }
        }

        // Pick up the drops before moving on, Baritone would otherwise walk away during the pickup delay
        if (target == null && detourPos == null && collectDrops.get()) {
            if (collectItem == null) findDrop();
            if (collectItem != null) {
                if (baritone.wasHijacked()) {
                    warning("Baritone was given another goal, pausing. Re-enable the module to continue.");
                    baritone.stop();
                    toggle();
                    return;
                }
                dropWaitTicks = 0;
                updateCollect();
                status = "Collecting drops";
                return;
            }
            // The block is gone on your side right away, its drops only show up once the server has broken it too.
            // When you stand inside the ore that doesn't matter, but not when it was broken from a few blocks away.
            if (dropWaitTicks > 0) {
                dropWaitTicks--;
                status = "Collecting drops";
                return;
            }
        }

        if (targetDonePending) {
            targetDonePending = false;
            onTargetDone();
        }

        if (detourPos != null) {
            if (baritone.wasHijacked()) {
                warning("Baritone was given another goal, pausing. Re-enable the module to continue.");
                baritone.stop();
                toggle();
                return;
            }
            if (!updateDetour()) return;
        }

        if (target == null) {
            String wait = antiDetectionWait();
            if (wait != null) {
                baritone.stop();
                status = wait;
                return;
            }

            // While repairing, ores that give experience come first
            target = repairSlot != -1 ? nextXpOre() : null;
            if (target != null) seenOre.add(target.pos());
            else target = nextTarget();

            if (target == null) {
                baritone.stop();
                if (scanner.pendingChunks() == 0 && disableWhenDone.get()) {
                    info("No ores left (mined %d, skipped %d).", mined, skipped);
                    toggle();
                }
                status = scanStatus("No ores in range");
                return;
            }

            targetTicks = 0;
            targetRetries = 0;
            retryWaitTicks = 0;
            targetBestDistance = Double.MAX_VALUE;
            baritoneStopped = false;
            baritone.mine(target.pos());
        }

        status = (repairSlot == -1 ? "" : repairInfo() + ": ") + "Mining " + target.kind().name().toLowerCase().replace('_', ' ') + " at " + target.pos().toShortString()
            + (directHit == null ? "" : directHit.getBlockPos().equals(target.pos()) ? " (in reach)" : " (uncovering)");
    }

    // Drop collection

    /** Looks for the nearest drop of a selected ore and lets Baritone walk to it. */
    private void findDrop() {
        double best = collectRadius.get() * collectRadius.get();
        ItemEntity found = null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved() || ignoredDrops.contains(item.getId())) continue;
            if (!isWantedDrop(item.getItem()) || !hasRoomFor(item.getItem().getItem())) continue;

            double distance = item.distanceToSqr(mc.player);
            if (distance <= best) {
                best = distance;
                found = item;
            }
        }

        if (found != null) {
            collectItem = found;
            collectTicks = 0;
            collectRetries = 0;
            baritone.walkTo(found.blockPosition());
        }
    }

    private void updateCollect() {
        ItemEntity item = collectItem;

        // Picked up (or despawned, burned, ...)
        if (item.isRemoved()) {
            collectItem = null;
            return;
        }

        if (++collectTicks > collectTimeout.get() * 20) {
            giveUpDrop();
            return;
        }

        if (!baritone.isWorking()) {
            // In pickup range (a bit less than the server's, to be sure): just wait, fresh drops can only be picked up
            // after a short delay
            if (mc.player.getBoundingBox().inflate(0.8, 0.4, 0.8).intersects(item.getBoundingBox())) return;

            // The item moved (fell down) or Baritone gave up, try again
            if (collectRetries++ < 2) baritone.walkTo(item.blockPosition());
            else giveUpDrop();
        }
    }

    private void giveUpDrop() {
        ignoredDrops.add(collectItem.getId());
        collectItem = null;
        baritone.stop();
    }

    /** The item can still be picked up: there is a free slot, or a stack of it that isn't full yet. */
    private boolean hasRoomFor(Item item) {
        Inventory inventory = mc.player.getInventory();
        if (inventory.getFreeSlot() != -1) return true;

        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        return offhand.is(item) && offhand.getCount() < offhand.getMaxStackSize();
    }

    private Compressor.Options compressOptions() {
        return new Compressor.Options(compressItems.get(), showCraftingGui.get(), compressDelayMin.get(), compressDelayMax.get(), dropJunk.get(), junkItems.get());
    }

    private int freeSlots() {
        int free = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        }
        return free;
    }

    private boolean hasRoomForAnyDrop() {
        for (Block block : blocks.get()) {
            OreKind kind = OreKind.of(block);
            if (kind != null && hasRoomFor(kind.drop())) return true;
        }
        return false;
    }

    private boolean isWantedDrop(ItemStack stack) {
        for (Block block : blocks.get()) {
            OreKind kind = OreKind.of(block);
            if (kind != null && kind.isDrop(stack.getItem())) return true;
        }
        return false;
    }

    // Anti-detection

    private void countMined() {
        mined++;
        long now = System.currentTimeMillis();
        minedTimes.addLast(now);
        purgeMinedTimes(now);
    }

    private void purgeMinedTimes(long now) {
        while (!minedTimes.isEmpty() && now - minedTimes.peekFirst() >= 60_000) minedTimes.removeFirst();
    }

    /** Called when the current target ore is done (mined, wrong or skipped): maybe a stone detour, then maybe a pause. */
    private void onTargetDone() {
        if (!antiDetection.get()) return;

        if (stoneDetours.get() && random.nextInt(100) < detourChance.get() && startDetour()) return;
        schedulePause();
    }

    private void schedulePause() {
        if (!antiDetection.get() || !randomPause.get()) return;

        double min = Math.min(pauseMin.get(), pauseMax.get());
        double max = Math.max(pauseMin.get(), pauseMax.get());
        pauseUntil = System.currentTimeMillis() + (long) ((min + random.nextDouble() * (max - min)) * 1000);
    }

    /** Picks a random stone block near the player and lets Baritone mine it. */
    private boolean startDetour() {
        BlockPos base = mc.player.blockPosition();
        int d = detourDistance.get();

        for (int i = 0; i < 30; i++) {
            BlockPos pos = base.offset(random.nextInt(2 * d + 1) - d, random.nextInt(3) - 1, random.nextInt(2 * d + 1) - d);
            if (pos.equals(base) || pos.equals(base.above()) || !isLoaded(pos) || ores.containsKey(pos)) continue;

            BlockState state = mc.level.getBlockState(pos);
            if (!state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.BASE_STONE_NETHER)) continue;

            detourPos = pos;
            detourTicks = 0;
            baritone.mine(pos);
            status = "Detour (stone) at " + pos.toShortString();
            return true;
        }

        return false;
    }

    /** Returns true when the detour is over. */
    private boolean updateDetour() {
        boolean gone = isGone(mc.level.getBlockState(detourPos));

        // Give Baritone a moment to start, then end the detour once it is idle, the block is gone or it takes too long
        if (gone || ++detourTicks > 20 * 20 || (detourTicks > 5 && !baritone.isWorking())) {
            detourPos = null;
            schedulePause();
            return true;
        }

        status = "Detour (stone) at " + detourPos.toShortString();
        return false;
    }

    /** Returns a status text while anti-detection wants to wait before the next ore, otherwise null. */
    private String antiDetectionWait() {
        if (!antiDetection.get()) return null;

        long now = System.currentTimeMillis();
        if (now < pauseUntil) return "Pause (" + seconds(pauseUntil - now) + "s)";

        int limit = maxOresPerMinute.get();
        if (limit > 0) {
            purgeMinedTimes(now);
            if (minedTimes.size() >= limit) {
                return "Waiting (rate limit, " + seconds(60_000 - (now - minedTimes.peekFirst())) + "s)";
            }
        }

        return null;
    }

    private static long seconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    /** Time left until the time limit as "Time left: m:ss", or null without a limit. */
    public String timeLeftInfo() {
        if (!isActive() || timeLimit.get() <= 0) return null;

        long left = Math.max(0, timeLimit.get() * 60_000L - (System.currentTimeMillis() - startedAt)) / 1000;
        return String.format("Time left: %d:%02d", left / 60, left % 60);
    }

    /** Short summary for the GUI, HUD and command, or null when anti-detection is off. */
    public String antiDetectionInfo() {
        if (!antiDetection.get()) return null;

        purgeMinedTimes(System.currentTimeMillis());
        int limit = maxOresPerMinute.get();
        return "Last minute: " + minedTimes.size() + (limit > 0 ? "/" + limit : "") + " ores";
    }

    // Mending repair

    /** Radius (blocks) around the player in which ores that give experience are mined while repairing. */
    private static final int XP_ORE_RADIUS = 16;

    /**
     * Starts repairing a Mending pickaxe that wore down below repair-below and ends once it is back at repair-until.
     * Meanwhile, ores near you that give experience are mined first, with that pickaxe in hand.
     */
    private void updateRepair() {
        int slot = pickaxeToRepair();
        if (slot != repairSlot) {
            if (slot != -1) {
                ItemStack pickaxe = mc.player.getInventory().getItem(slot);
                info("%s is worn down to %d%%, repairing it with Mending: ores nearby that give experience come first.", pickaxe.getHoverName().getString(), ToolSelector.durability(pickaxe));
            }
            else {
                ItemStack pickaxe = mc.player.getInventory().getItem(repairSlot);
                if (ToolSelector.durability(pickaxe) >= repairUntil.get()) info("%s is repaired (%d%%).", pickaxe.getHoverName().getString(), ToolSelector.durability(pickaxe));
            }
            repairSlot = slot;
            xpOres.clear();
            xpScanTimer = 20;
        }
        if (repairSlot == -1) return;

        if (++xpScanTimer >= 20) {
            xpScanTimer = 0;
            scanXpOres();
        }

        // Leave a target that gives no experience (e.g. ancient debris) as soon as one that does is in sight
        if (target != null && target.kind().experience() < 1 && !xpOres.isEmpty()) clearTarget();
    }

    /** Hotbar slot of the Mending pickaxe to repair, or -1. Once started, it is repaired up to repair-until. */
    private int pickaxeToRepair() {
        if (!repairWithMending.get() || !autoTool.get()) return -1;

        Inventory inventory = mc.player.getInventory();
        if (repairSlot != -1 && needsRepair(inventory.getItem(repairSlot), Math.max(repairUntil.get(), repairBelow.get()))) return repairSlot;
        for (int slot = 0; slot < 9; slot++) {
            if (needsRepair(inventory.getItem(slot), repairBelow.get())) return slot;
        }
        return -1;
    }

    /** A pickaxe Mending can repair with less durability left than below (percent), but not yet worn down to min-durability. */
    private boolean needsRepair(ItemStack stack, int below) {
        return stack.is(ItemTags.PICKAXES) && ToolSelector.canMend(stack) && ToolSelector.durability(stack) < below
            && !ToolSelector.isWorn(stack, minToolDurability.get());
    }

    /**
     * Collects the ores near you that give experience and that the pickaxe can mine. With server-anti-xray only the
     * ones you can see, hidden ones may be fake. Ores next to water or lava are left alone, there are enough others
     * (in the Nether, quartz next to the lava sea is often out of reach).
     */
    private void scanXpOres() {
        xpOres.clear();
        ItemStack pickaxe = mc.player.getInventory().getItem(repairSlot);

        BlockPos center = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-XP_ORE_RADIUS, -XP_ORE_RADIUS, -XP_ORE_RADIUS), center.offset(XP_ORE_RADIUS, XP_ORE_RADIUS, XP_ORE_RADIUS))) {
            BlockState state = mc.level.getBlockState(pos);
            OreKind kind = OreKind.of(state.getBlock());
            if (kind == null || kind.experience() < 1 || finished.contains(pos) || !pickaxe.isCorrectToolForDrops(state) || !isTrusted(pos) || touchesFluid(pos)) continue;

            BlockPos immutable = pos.immutable();
            xpOres.put(immutable, ores.getOrDefault(immutable, new PredictedOre(immutable, kind)));
        }
    }

    /** The ore that gives the most experience for the way there. */
    private PredictedOre nextXpOre() {
        PredictedOre best = null;
        double bestCost = Double.MAX_VALUE;

        for (PredictedOre ore : xpOres.values()) {
            BlockPos pos = ore.pos();
            double dx = pos.getX() + 0.5 - mc.player.getX(), dy = (pos.getY() - mc.player.getY()) * verticalCost.get(), dz = pos.getZ() + 0.5 - mc.player.getZ();
            double cost = Math.sqrt(dx * dx + dy * dy + dz * dz) / ore.kind().experience();
            if (cost < bestCost) {
                bestCost = cost;
                best = ore;
            }
        }

        return best;
    }

    /** Experience orbs fly to you from up to 8 blocks away. */
    private boolean xpOrbsNearby() {
        return !mc.level.getEntitiesOfClass(ExperienceOrb.class, mc.player.getBoundingBox().inflate(8), orb -> true).isEmpty();
    }

    private String repairInfo() {
        int durability = ToolSelector.durability(mc.player.getInventory().getItem(repairSlot));
        return "Repairing pickaxe (" + durability + "% -> " + Math.max(repairUntil.get(), repairBelow.get()) + "%)";
    }

    // Breaking ores in reach

    /** Returns where a ray from the eyes first hits the block, if it is in reach and not hidden behind other blocks. */
    private BlockHitResult findReachableFace(BlockPos pos) {
        for (BlockHitResult hit : raycastsInReach(pos)) {
            if (hit.getBlockPos().equals(pos)) return hit;
        }
        return null;
    }

    /**
     * The block is in reach, but other blocks are in the way: returns the first of them if it is safe to break.
     * Mining it uncovers the ore, like a player would do instead of walking around.
     */
    private BlockHitResult findBlockInTheWay(BlockPos pos) {
        for (BlockHitResult hit : raycastsInReach(pos)) {
            if (!hit.getBlockPos().equals(pos) && isSafeToBreak(hit.getBlockPos())) return hit;
        }
        return null;
    }

    /** Rays from the eyes to the center and to the middle of each face of the block, as far as they are in reach. */
    private List<BlockHitResult> raycastsInReach(BlockPos pos) {
        List<BlockHitResult> hits = new ArrayList<>();
        Vec3 eyes = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange() - 0.25;
        Vec3 center = Vec3.atCenterOf(pos);
        if (eyes.distanceToSqr(center) > (reach + 0.9) * (reach + 0.9)) return hits;

        List<Vec3> points = new ArrayList<>();
        points.add(center);
        for (Direction direction : Direction.values()) {
            points.add(center.add(direction.getStepX() * 0.49, direction.getStepY() * 0.49, direction.getStepZ() * 0.49));
        }

        for (Vec3 point : points) {
            if (eyes.distanceToSqr(point) > reach * reach) continue;

            BlockHitResult hit = mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) hits.add(hit);
        }

        return hits;
    }

    private boolean isSafeToBreak(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || isUnbreakable(state, pos) || !state.getFluidState().isEmpty() || mc.level.getBlockEntity(pos) != null) return false;

        // Not the floor you are standing on
        AABB box = mc.player.getBoundingBox();
        if (new AABB(box.minX, box.minY - 0.5, box.minZ, box.maxX, box.minY, box.maxZ).intersects(new AABB(pos))) return false;

        // No water or lava flowing in, no sand or gravel coming down
        return !touchesFluid(pos) && !(mc.level.getBlockState(pos.above()).getBlock() instanceof FallingBlock);
    }

    private boolean touchesFluid(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!mc.level.getFluidState(pos.relative(direction)).isEmpty()) return true;
        }
        return false;
    }

    /** Looks at the block and keeps mining it, like holding the attack key. */
    private void breakDirectly(BlockHitResult hit) {
        mc.player.setYRot((float) Rotations.getYaw(hit.getLocation()));
        mc.player.setXRot((float) Rotations.getPitch(hit.getLocation()));

        if (mc.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection())) {
            SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND);
        }
    }

    private PredictedOre nextTarget() {
        PredictedOre best = null;
        double bestCost = Double.MAX_VALUE;
        double maxDistSq = maxDistance.get() * maxDistance.get();

        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        Map<BlockPos, Integer> veinSizes = veinPriority.get() > 0 ? veinSizes(maxDistSq) : null;

        // With stop-when-full, ores whose drops don't fit anymore are left for later
        Set<OreKind> full = EnumSet.noneOf(OreKind.class);
        if (stopWhenFull.get()) {
            for (Block block : blocks.get()) {
                OreKind kind = OreKind.of(block);
                if (kind != null && !hasRoomFor(kind.drop())) full.add(kind);
            }
        }

        for (PredictedOre ore : ores.values()) {
            if (full.contains(ore.kind())) continue;
            BlockPos pos = ore.pos();
            double dx = pos.getX() + 0.5 - px, dy = pos.getY() - py, dz = pos.getZ() + 0.5 - pz;
            // Horizontal only, ores are often far below the player (e.g. diamonds when standing on a mountain)
            double horizontalSq = dx * dx + dz * dz;
            if (horizontalSq > maxDistSq) continue;

            // Distance with height weighted, like the way there: diagonally down counts as far as it really is
            double vertical = dy * verticalCost.get();
            double cost = Math.sqrt(horizontalSq + vertical * vertical);

            // Prefer groups of ores over single ones: with vein-priority 1 this is the walking distance per ore
            if (veinSizes != null) cost /= Math.pow(veinSizes.get(pos), veinPriority.get());

            // Finish the ores you can already see right next to you before walking off, otherwise a vein can be
            // left half mined and its rest ends up out of range later
            if (seenOre.contains(pos) && horizontalSq + dy * dy <= SEEN_ORE_RADIUS * SEEN_ORE_RADIUS) cost -= 1000;

            if (cost < bestCost) {
                bestCost = cost;
                best = ore;
            }
        }

        return best;
    }

    /** Radius (blocks) in which ores count as one group, so a block of stone between two ores still connects them. */
    private static final int VEIN_GAP = 2;

    /** Number of ores in the group each ore within max-distance belongs to. */
    private Map<BlockPos, Integer> veinSizes(double maxDistSq) {
        double px = mc.player.getX(), pz = mc.player.getZ();
        Set<BlockPos> candidates = new HashSet<>();
        for (BlockPos pos : ores.keySet()) {
            double dx = pos.getX() + 0.5 - px, dz = pos.getZ() + 0.5 - pz;
            if (dx * dx + dz * dz <= maxDistSq) candidates.add(pos);
        }

        Map<BlockPos, Integer> sizes = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> group = new ArrayList<>();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        for (BlockPos start : candidates) {
            if (sizes.containsKey(start)) continue;

            group.clear();
            sizes.put(start, 0);
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                group.add(pos);

                for (int x = -VEIN_GAP; x <= VEIN_GAP; x++) {
                    for (int y = -VEIN_GAP; y <= VEIN_GAP; y++) {
                        for (int z = -VEIN_GAP; z <= VEIN_GAP; z++) {
                            neighbour.setWithOffset(pos, x, y, z);
                            if (candidates.contains(neighbour) && !sizes.containsKey(neighbour)) {
                                BlockPos next = neighbour.immutable();
                                sizes.put(next, 0);
                                queue.add(next);
                            }
                        }
                    }
                }
            }

            for (BlockPos pos : group) sizes.put(pos, group.size());
        }

        return sizes;
    }

    private void skipNoProgress(BlockPos pos) {
        warning("Got no closer to ore at %s for %d seconds, skipping.", pos.toShortString(), targetTimeout.get());
        skipped++;
        finishTarget();
    }

    private void finishTarget() {
        if (target != null) {
            ores.remove(target.pos());
            xpOres.remove(target.pos());
            seenOre.remove(target.pos());
            finished.add(target.pos());
        }
        target = null;
    }

    private void clearTarget() {
        if (baritone != null) baritone.stop();
        target = null;
        detourPos = null;
        collectItem = null;
        dropWaitTicks = 0;
        targetDonePending = false;
    }

    private boolean isLoaded(BlockPos pos) {
        return mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** The chunk of the position and the ones around it are loaded. */
    private boolean isAreaLoaded(BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (!mc.level.getChunkSource().hasChunk(cx + x, cz + z)) return false;
            }
        }
        return true;
    }

    /** A pickaxe in the hotbar or offhand. Usable: one that isn't worn down to min-durability. */
    private boolean hasPickaxe(boolean usable) {
        for (int i = 0; i < 9; i++) {
            if (isPickaxe(mc.player.getInventory().getItem(i), usable)) return true;
        }
        return isPickaxe(mc.player.getOffhandItem(), usable);
    }

    private boolean isPickaxe(ItemStack stack, boolean usable) {
        return stack.is(ItemTags.PICKAXES) && !(usable && ToolSelector.isWorn(stack, minToolDurability.get()));
    }

    private boolean isPlayerNearby() {
        double rangeSq = playerRange.get() * playerRange.get();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.distanceToSqr(mc.player) <= rangeSq) return true;
        }
        return false;
    }

    private String scanStatus(String idle) {
        int pending = scanner.pendingChunks();
        return pending > 0 ? "Scanning, " + pending + " chunks left" : idle;
    }

    private void updateRenderList() {
        renderList.clear();
        if (!render.get()) return;

        renderList.addAll(ores.values());
        if (renderList.size() > renderLimit.get()) {
            BlockPos playerPos = mc.player.blockPosition();
            renderList.sort((a, b) -> Double.compare(a.pos().distSqr(playerPos), b.pos().distSqr(playerPos)));
            renderList.subList(renderLimit.get(), renderList.size()).clear();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (PredictedOre ore : renderList) {
            if (ore == target) continue;
            event.renderer.box(ore.pos(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }

        if (target != null) {
            BlockPos pos = target.pos();
            event.renderer.box(pos, targetColor.get(), targetColor.get(), shapeMode.get(), 0);
            if (tracer.get()) {
                event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, targetColor.get());
            }
        }
    }

    // Saved settings

    /** Version of the settings layout, saved with the module. 2 = groups as since build 34. */
    private static final int SETTINGS_LAYOUT = 2;

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        if (tag != null) tag.putInt("layout", SETTINGS_LAYOUT);
        return tag;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        if (tag.getIntOr("layout", 1) < SETTINGS_LAYOUT) migrateLayout(tag);
        return super.fromTag(tag);
    }

    /**
     * Build 34 moved settings into other groups. Meteor restores a setting only from the group it is in now, so
     * saved values are sorted into their new groups before loading. The groups also start collapsed once, except
     * Targets and Safety.
     */
    private void migrateLayout(CompoundTag tag) {
        Map<String, ListTag> saved = new HashMap<>();
        for (Tag oldGroup : tag.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(oldGroup instanceof CompoundTag oldGroupTag)) continue;
            for (Tag setting : oldGroupTag.getListOrEmpty("settings")) {
                if (!(setting instanceof CompoundTag settingTag)) continue;
                String name = settingTag.getStringOr("name", "");
                for (SettingGroup group : settings) {
                    if (group.get(name) != null) saved.computeIfAbsent(group.name, n -> new ListTag()).add(settingTag);
                }
            }
        }

        ListTag groups = new ListTag();
        for (SettingGroup group : settings) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putString("name", group.name);
            groupTag.putBoolean("sectionExpanded", group == sgTargets || group == sgSafety);
            groupTag.put("settings", saved.getOrDefault(group.name, new ListTag()));
            groups.add(groupTag);
        }

        CompoundTag settingsTag = new CompoundTag();
        settingsTag.put("groups", groups);
        tag.put("settings", settingsTag);
    }

    // GUI

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        list.add(theme.label("Seed: " + (seed == null ? "none" : seed.seed() + " (" + seed.origin() + ")")));
        list.add(theme.label("Status: " + status));
        list.add(theme.label("Predicted: " + ores.size() + "  Mined: " + mined + "  Skipped: " + skipped + "  Invalid: " + invalid));
        String antiDetection = antiDetectionInfo();
        if (antiDetection != null) list.add(theme.label("Anti-Detection: " + antiDetection));
        String timeLeft = timeLeftInfo();
        if (timeLeft != null) list.add(theme.label(timeLeft));

        WHorizontalList buttons = list.add(theme.horizontalList()).widget();

        WButton rescan = buttons.add(theme.button("Rescan")).widget();
        rescan.action = this::rescan;

        WButton skip = buttons.add(theme.button("Skip target")).widget();
        skip.action = this::skipTarget;

        WButton reset = buttons.add(theme.button("Reset stats")).widget();
        reset.action = () -> mined = skipped = invalid = 0;

        return list;
    }

    // API for the command and HUD

    /** Forgets all predictions (including mined/skipped positions) and scans again. */
    public void rescan() {
        ores.clear();
        finished.clear();
        seenOre.clear();
        renderList.clear();
        clearTarget();
        scanner.reset();
        lastScanCenter = null;
    }

    public void skipTarget() {
        if (target == null) return;
        skipped++;
        finishTarget();
        if (baritone != null) baritone.stop();
    }

    public void setManualSeed(String value) {
        manualSeed.set(value);
    }

    private void resetAll() {
        rescan();
        seed = resolvedSeed = null;
        dimension = null;
        paramsDirty = true;
    }

    private void invalidate() {
        paramsDirty = true;
    }

    /** Applies or restores the Baritone settings, also when they are changed while the module is running. */
    private void applyBaritoneSettings() {
        if (baritone == null || !isActive()) return;

        if (configureBaritone.get()) baritone.configure(allowPlace.get(), allowParkour.get());
        else baritone.restore();

        baritone.setExternalAutoTool(autoTool.get());
    }

    public String status() {
        return isActive() ? status : "Disabled";
    }

    public ResolvedSeed seed() {
        return seed;
    }

    public int predicted() {
        return ores.size();
    }

    public int mined() {
        return mined;
    }

    public int skipped() {
        return skipped;
    }

    public int invalid() {
        return invalid;
    }

    @Override
    public String getInfoString() {
        return isActive() ? Integer.toString(ores.size()) : null;
    }

    public enum VerifyMode {
        Off,
        SkipAir,
        Exact
    }
}
