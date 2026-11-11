package com.qwennobert.seedminer.compress;

import com.qwennobert.seedminer.util.BaritoneControl;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.player.SwingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Crafts items into blocks to make room, the way a player would: take a crafting table from the inventory, place it,
 * open it, use the recipe book, take the result with shift-click, close it, mine the table and pick it up again.
 * Every step goes to the server as the normal packets, with short random pauses in between. With the crafting window
 * hidden ("silent") only the drawing is skipped, the window is really open.
 */
public final class Compressor {
    public record Options(List<Item> items, boolean showGui, int delayMin, int delayMax, boolean dropJunk, List<Item> junk) {
    }

    private enum State {
        AIM_THROW, THROW, SETTLE, CLOSE_INVENTORY, TO_HOTBAR, SELECT, AIM_PLACE, PLACE, WAIT_PLACED, AIM_OPEN, OPEN, WAIT_OPEN,
        CRAFT, WAIT_GRID, TAKE, WAIT_TAKEN, CLOSE, AIM_BREAK, BREAK, WAIT_DROP, PICKUP
    }

    private static final long COOLDOWN_MS = 60_000;
    private static final int MAX_ROUNDS = 12;
    /** Free slots wanted before the table is placed: one for the table, one for the crafted blocks. */
    private static final int ROOM = 2;
    private static final int MAX_THROWS = 10;
    /** Kept when throwing out junk, so Baritone still has blocks to build with. */
    private static final List<Item> BUILDING_BLOCKS = List.of(Items.COBBLESTONE, Items.NETHERRACK, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.STONE, Items.END_STONE);

    private final Minecraft mc;
    private final BaritoneControl baritone;
    private final Consumer<String> info, warning;
    private final Random random = new Random();
    private final Set<Item> warnedNoRecipe = new HashSet<>();

    private State state;
    private Options options;
    private int stateTicks;
    private long nextActionAt;
    private long cooldownUntil;
    private boolean warnedNoSpace;

    private BlockHitResult placeHit;
    private BlockPos tablePos;
    private boolean tablePlaced;
    private ItemEntity tableDrop;
    private int pickupRetries;
    private Vec3 throwTarget;
    private int throwsLeft, pickupThrows;
    private boolean swapped;
    /** Crafting tables in the inventory before placing one, to notice when it is back. */
    private int tableCount;

    private Compressible current;
    private RecipeDisplayEntry recipe;
    private int resultCountBefore, rounds;
    private final StringBuilder crafted = new StringBuilder();
    /** Set when something went wrong: the table is still mined and picked up, then this is reported. */
    private String failure;

    public Compressor(Minecraft mc, BaritoneControl baritone, Consumer<String> info, Consumer<String> warning) {
        this.mc = mc;
        this.baritone = baritone;
        this.info = info;
        this.warning = warning;
    }

    public boolean isActive() {
        return state != null;
    }

    /** With the window hidden, the crafting screen is not shown; the screen handler is open anyway. */
    public boolean hidesScreen(Screen screen) {
        return isActive() && !options.showGui() && screen instanceof CraftingScreen;
    }

    /**
     * Everything needed is there: a crafting table, enough items with a known recipe, crafting frees at least one
     * slot, there is a spot for the table and room in the inventory (or junk to throw out).
     */
    public boolean canStart(Options options) {
        if (System.currentTimeMillis() < cooldownUntil) return false;
        if (!mc.player.onGround() || mc.player.isInWater() || mc.player.isInLava() || mc.player.isShiftKeyDown()) return false;
        if (findTable() < 0 || nextCompressible(options) == null || slotsSaved(options) < 1) return false;

        // Junk lying next to you is picked up the moment a slot gets free: then there is no room for the table
        if (!options.dropJunk() && junkInReach(options)) return false;
        if (options.dropJunk() && freeSlots() < ROOM && junkSlot(options) < 0 && freeSlots() < 1) return false;

        if (findPlaceSpot() == null) {
            if (!warnedNoSpace) warning.accept("Auto compress: no free spot for the crafting table here, trying again later.");
            warnedNoSpace = true;
            cooldownUntil = System.currentTimeMillis() + 5_000;
            return false;
        }
        warnedNoSpace = false;
        return true;
    }

    public void start(Options options) {
        this.options = options;
        crafted.setLength(0);
        failure = null;
        tablePlaced = false;
        tableDrop = null;
        pickupRetries = 0;
        pickupThrows = 0;
        swapped = false;
        throwsLeft = MAX_THROWS;
        rounds = 0;
        tableCount = mc.player.getInventory().countItem(Items.CRAFTING_TABLE);

        // Make room first, otherwise junk lying around takes the slots that crafting frees
        if (options.dropJunk() && freeSlots() < ROOM && (throwTarget = findThrowTarget()) != null) set(State.AIM_THROW);
        else afterRoom();
    }

    private void afterRoom() {
        set(SlotUtils.isHotbar(findTable()) ? State.SELECT : State.TO_HOTBAR);
    }

    /** Stops right away (module disabled): closes the crafting window if it is open. */
    public void abort() {
        if (state != null && mc.player != null && mc.player.containerMenu instanceof CraftingMenu) mc.player.closeContainer();
        state = null;
    }

    public void reset() {
        state = null;
        cooldownUntil = 0;
        warnedNoRecipe.clear();
        warnedNoSpace = false;
    }

    /** Runs one tick and returns the status text. */
    public String tick() {
        stateTicks++;
        if (stateTicks > timeout(state)) {
            fail("timed out (" + state.name().toLowerCase().replace('_', ' ') + ")");
            return status();
        }

        // Turning the head happens every tick, everything else after a short random pause
        switch (state) {
            case AIM_THROW -> {
                if (aim(throwTarget)) set(State.THROW);
                return status();
            }
            case AIM_PLACE -> {
                if (aim(placeHit.getLocation())) set(State.PLACE);
                return status();
            }
            case AIM_OPEN, AIM_BREAK -> {
                BlockHitResult hit = tableHit();
                if (hit == null) {
                    if (state == State.AIM_BREAK) set(State.WAIT_DROP);
                    else fail("the crafting table is not in sight");
                }
                else if (aim(hit.getLocation())) set(state == State.AIM_OPEN ? State.OPEN : State.BREAK);
                return status();
            }
            case BREAK -> {
                breakTable();
                return status();
            }
            default -> {
            }
        }

        if (System.currentTimeMillis() < nextActionAt) return status();

        switch (state) {
            case THROW -> {
                if (freeSlots() >= ROOM) {
                    set(State.CLOSE_INVENTORY);
                    break;
                }
                int slot = junkSlot(options);
                if (slot < 0 || throwsLeft-- <= 0) {
                    if (freeSlots() >= 1) set(State.CLOSE_INVENTORY);
                    else fail("no room in the inventory and no junk to throw out");
                    break;
                }
                // Like Ctrl+Q over the stack in the inventory: it flies off where you look
                if (options.showGui() && !(mc.gui.screen() instanceof InventoryScreen)) mc.gui.setScreen(new InventoryScreen(mc.player));
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, SlotUtils.indexToId(slot), 1, ContainerInput.THROW, mc.player);
                set(State.SETTLE);
            }
            case SETTLE -> {
                // Junk lying next to you is picked up into the slot that just got free; wait for that, then look again
                if (stateTicks >= 15) set(State.THROW);
            }
            case TO_HOTBAR -> {
                // Like pressing a number key over the table in the inventory
                int from = findTable();
                if (from < 0 || swapped) {
                    fail(from < 0 ? "no crafting table in the inventory" : "could not move the crafting table into the hotbar");
                    break;
                }
                swapped = true;
                if (options.showGui()) mc.gui.setScreen(new InventoryScreen(mc.player));
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, SlotUtils.indexToId(from), freeHotbarSlot(), ContainerInput.SWAP, mc.player);
                set(State.CLOSE_INVENTORY);
            }
            case CLOSE_INVENTORY -> {
                mc.player.closeContainer();
                afterRoom();
            }
            case SELECT -> {
                int slot = findTable();
                if (!SlotUtils.isHotbar(slot)) {
                    fail("the crafting table is not in the hotbar");
                    break;
                }
                InvUtils.swap(slot, false);
                placeHit = findPlaceSpot();
                if (placeHit == null) {
                    fail("no free spot for the crafting table");
                    break;
                }
                tablePos = placeHit.getBlockPos().relative(placeHit.getDirection());
                set(State.AIM_PLACE);
            }
            case PLACE -> {
                if (!mc.player.getMainHandItem().is(Items.CRAFTING_TABLE)) {
                    fail("the crafting table is not in the hand");
                    break;
                }
                if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, placeHit).consumesAction()) SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND);
                set(State.WAIT_PLACED);
            }
            case WAIT_PLACED -> {
                if (mc.level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
                    tablePlaced = true;
                    set(State.AIM_OPEN);
                }
            }
            case OPEN -> {
                BlockHitResult hit = tableHit();
                if (hit == null) {
                    fail("the crafting table is not in sight");
                    break;
                }
                if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction()) SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND);
                set(State.WAIT_OPEN);
            }
            case WAIT_OPEN -> {
                if (mc.player.containerMenu instanceof CraftingMenu) set(State.CRAFT);
            }
            case CRAFT -> craft();
            case WAIT_GRID -> {
                CraftingMenu handler = handler();
                if (handler == null) fail("the crafting window was closed");
                else if (handler.getSlot(CraftingMenu.RESULT_SLOT).getItem().is(current.result)) set(State.TAKE);
            }
            case TAKE -> {
                CraftingMenu handler = handler();
                if (handler == null) {
                    fail("the crafting window was closed");
                    break;
                }
                resultCountBefore = mc.player.getInventory().countItem(current.result);
                // Shift-click on the result crafts as often as the grid allows
                mc.gameMode.handleContainerInput(handler.containerId, CraftingMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);
                set(State.WAIT_TAKEN);
            }
            case WAIT_TAKEN -> {
                CraftingMenu handler = handler();
                if (handler == null) {
                    fail("the crafting window was closed");
                    break;
                }
                if (handler.getSlot(CraftingMenu.RESULT_SLOT).getItem().isEmpty() && gridEmpty(handler)) {
                    int made = mc.player.getInventory().countItem(current.result) - resultCountBefore;
                    if (made > 0) {
                        if (!crafted.isEmpty()) crafted.append(", ");
                        crafted.append(made).append("x ").append(Names.get(current.result));
                    }
                    rounds++;
                    set(State.CRAFT);
                }
            }
            case CLOSE -> {
                if (mc.player.containerMenu instanceof CraftingMenu) mc.player.closeContainer();
                set(State.AIM_BREAK);
            }
            case WAIT_DROP -> {
                if (tableBack()) finish();
                else if ((tableDrop = findTableDrop()) != null) set(State.PICKUP);
            }
            case PICKUP -> pickUp();
            default -> {
            }
        }

        return status();
    }

    // Steps

    private void craft() {
        CraftingMenu handler = handler();
        if (handler == null) {
            fail("the crafting window was closed");
            return;
        }

        current = rounds < MAX_ROUNDS ? nextCompressible(options) : null;
        if (current == null) {
            set(State.CLOSE);
            return;
        }

        // Like shift-clicking the recipe in the recipe book: the server fills the grid with as many as possible
        recipe = findRecipe(current);
        mc.gameMode.handlePlaceRecipe(handler.containerId, recipe.id(), true);
        set(State.WAIT_GRID);
    }

    private void breakTable() {
        if (!mc.level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
            set(State.WAIT_DROP);
            return;
        }

        BlockHitResult hit = tableHit();
        if (hit == null) {
            fail("the crafting table is not in sight");
            return;
        }
        aim(hit.getLocation());
        if (mc.gameMode.continueDestroyBlock(tablePos, hit.getDirection())) SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND);
    }

    private void pickUp() {
        if (tableDrop.isRemoved() || tableBack()) {
            finish();
            return;
        }

        // Junk took the room meanwhile: throw out one stack, the table can be picked up first (shorter pickup delay)
        if (!hasRoomFor(Items.CRAFTING_TABLE) && options.dropJunk() && pickupThrows < 3) {
            int slot = junkSlot(options);
            if (slot >= 0) {
                pickupThrows++;
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, SlotUtils.indexToId(slot), 1, ContainerInput.THROW, mc.player);
                mc.player.closeContainer();
                nextActionAt = System.currentTimeMillis() + 500;
                return;
            }
        }

        if (!baritone.isWorking()) {
            if (mc.player.getBoundingBox().inflate(0.8, 0.4, 0.8).intersects(tableDrop.getBoundingBox())) return;
            if (pickupRetries++ < 3) baritone.walkTo(tableDrop.blockPosition());
            else fail("could not pick up the crafting table");
        }
    }

    private void finish() {
        baritone.stop();
        state = null;
        if (failure != null) {
            warning.accept("Auto compress: " + failure + ".");
            cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS;
        }
        else if (!crafted.isEmpty()) info.accept("Crafted " + crafted + ".");
    }

    /**
     * Something went wrong. The crafting window is closed and a placed table is still mined and picked up, unless
     * that is what failed.
     */
    private void fail(String reason) {
        if (failure == null) failure = reason;
        if (mc.player.containerMenu instanceof CraftingMenu || mc.gui.screen() instanceof InventoryScreen) mc.player.closeContainer();

        boolean cleaningUp = state == State.AIM_BREAK || state == State.BREAK || state == State.WAIT_DROP || state == State.PICKUP;
        if (tablePlaced && !cleaningUp && mc.level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) set(State.AIM_BREAK);
        else finish();
    }

    private void set(State next) {
        state = next;
        stateTicks = 0;
        int min = Math.min(options.delayMin(), options.delayMax()), max = Math.max(options.delayMin(), options.delayMax());
        nextActionAt = System.currentTimeMillis() + min + (max > min ? random.nextInt(max - min + 1) : 0);
    }

    private static int timeout(State state) {
        return switch (state) {
            case BREAK, PICKUP -> 300;
            default -> 100;
        };
    }

    private String status() {
        if (state == null) return "Compressing: done";
        return switch (state) {
            case AIM_THROW, THROW, SETTLE -> "Compressing: making room";
            case CLOSE_INVENTORY, TO_HOTBAR, SELECT, AIM_PLACE, PLACE, WAIT_PLACED -> "Compressing: placing crafting table";
            case AIM_OPEN, OPEN, WAIT_OPEN -> "Compressing: opening crafting table";
            case CRAFT, WAIT_GRID, TAKE, WAIT_TAKEN, CLOSE -> "Compressing: crafting" + (current != null ? " " + Names.get(current.result) : "");
            case AIM_BREAK, BREAK, WAIT_DROP, PICKUP -> "Compressing: picking up crafting table";
        };
    }

    // Helpers

    /** Turns the head a bit towards the point, like moving the mouse. True once it looks there. */
    private boolean aim(Vec3 target) {
        float yaw = (float) Rotations.getYaw(target), pitch = (float) Rotations.getPitch(target);
        float yawDiff = Mth.wrapDegrees(yaw - mc.player.getYRot()), pitchDiff = pitch - mc.player.getXRot();
        float step = 12 + random.nextFloat() * 14;

        mc.player.setYRot(mc.player.getYRot() + Mth.clamp(yawDiff, -step, step));
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + Mth.clamp(pitchDiff, -step, step), -90, 90));
        return Math.abs(yawDiff) <= step && Math.abs(pitchDiff) <= step;
    }

    /** Next item in the list with enough of it in the inventory and a recipe in the recipe book. */
    private Compressible nextCompressible(Options options) {
        for (Item item : options.items()) {
            Compressible compressible = Compressible.of(item);
            if (compressible == null || warnedNoRecipe.contains(item) || mc.player.getInventory().countItem(item) < compressible.count) continue;

            if (findRecipe(compressible) != null) return compressible;
            if (warnedNoRecipe.add(item)) {
                warning.accept("Auto compress: the recipe for " + Names.get(compressible.result) + " is not in your recipe book yet.");
            }
        }
        return null;
    }

    private RecipeDisplayEntry findRecipe(Compressible compressible) {
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        ItemStack ingredient = new ItemStack(compressible.item);

        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!entry.display().result().resolveForFirstStack(context).is(compressible.result)) continue;

                Optional<List<Ingredient>> requirements = entry.craftingRequirements();
                if (requirements.isEmpty() || requirements.get().size() != compressible.count) continue;
                if (requirements.get().stream().allMatch(i -> i.test(ingredient))) return entry;
            }
        }
        return null;
    }

    /** How many inventory slots crafting the selected items into blocks would free. */
    private int slotsSaved(Options options) {
        int saved = 0;
        for (Item item : options.items()) {
            Compressible c = Compressible.of(item);
            if (c == null || warnedNoRecipe.contains(item)) continue;

            int count = 0, slots = 0, resultRoom = 0;
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.is(c.item)) {
                    count += stack.getCount();
                    slots++;
                }
                else if (stack.is(c.result)) resultRoom += stack.getMaxStackSize() - stack.getCount();
            }

            int results = count / c.count;
            if (results == 0) continue;
            int rest = count - results * c.count;
            int restSlots = (rest + c.item.getDefaultMaxStackSize() - 1) / c.item.getDefaultMaxStackSize();
            int resultSlots = (Math.max(0, results - resultRoom) + c.result.getDefaultMaxStackSize() - 1) / c.result.getDefaultMaxStackSize();
            saved += slots - restSlots - resultSlots;
        }
        return saved;
    }

    private int freeSlots() {
        int free = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        }
        return free;
    }

    /** The item can be picked up: a free slot, or a stack of it with room. */
    private boolean hasRoomFor(Item item) {
        if (freeSlots() > 0) return true;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    /** The biggest junk stack to throw out, keeping one stack of building blocks for Baritone. -1 if there is none. */
    private int junkSlot(Options options) {
        int keep = -1;
        for (Item building : BUILDING_BLOCKS) {
            int best = -1;
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.is(building) && (best < 0 || stack.getCount() > mc.player.getInventory().getItem(best).getCount())) best = i;
            }
            if (best >= 0) {
                keep = best;
                break;
            }
        }

        int slot = -1;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (i == keep || stack.isEmpty() || !options.junk().contains(stack.getItem())) continue;
            if (slot < 0 || stack.getCount() > mc.player.getInventory().getItem(slot).getCount()) slot = i;
        }
        return slot;
    }

    /** Junk lying close enough to be picked up as soon as a slot is free. */
    private boolean junkInReach(Options options) {
        AABB reach = mc.player.getBoundingBox().inflate(2, 1.5, 2);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && options.junk().contains(item.getItem().getItem()) && reach.intersects(item.getBoundingBox())) return true;
        }
        return false;
    }

    /** A point to throw junk at: the direction with the most free room in front, so it isn't picked up again. */
    private Vec3 findThrowTarget() {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 best = null;
        double bestDistance = 0;
        for (int i = 0; i < 16; i++) {
            double yaw = Math.toRadians(i * 22.5);
            Vec3 direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            BlockHitResult hit = raycast(eyes, eyes.add(direction.scale(6)));
            double distance = hit.getType() == HitResult.Type.MISS ? 6 : hit.getLocation().distanceTo(eyes);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = eyes.add(direction.scale(3)).add(0, 0.3, 0);
            }
        }
        return best;
    }

    /** Inventory index of a crafting table (hotbar first), or -1. */
    private int findTable() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.CRAFTING_TABLE)) return i;
        }
        return -1;
    }

    /** A hotbar slot for the table: an empty one, otherwise one without a tool. */
    private int freeHotbarSlot() {
        int noTool = -1;
        for (int i = 8; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) return i;
            if (noTool < 0 && !stack.isDamageableItem()) noTool = i;
        }
        if (noTool >= 0) return noTool;
        for (int i = 8; i >= 0; i--) {
            if (!mc.player.getInventory().getItem(i).is(ItemTags.PICKAXES)) return i;
        }
        return 8;
    }

    /** Top of a solid block next to you, free above, visible and in reach. Closest first. */
    private BlockHitResult findPlaceSpot() {
        Vec3 eyes = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange() - 0.5;
        AABB player = mc.player.getBoundingBox();
        BlockPos feet = mc.player.blockPosition();

        BlockHitResult best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
            BlockState state = mc.level.getBlockState(pos);
            if (!state.canBeReplaced() || !mc.level.getFluidState(pos).isEmpty() || player.intersects(new AABB(pos))) continue;
            if (touchesFluid(pos)) continue;

            BlockPos support = pos.below();
            if (!mc.level.getBlockState(support).isFaceSturdy(mc.level, support, Direction.UP)) continue;

            // Aim just inside the block: a ray that ends exactly on the face doesn't hit it
            Vec3 top = Vec3.atCenterOf(support).add(0, 0.49, 0);
            if (eyes.distanceTo(top) > reach) continue;

            BlockHitResult hit = raycast(eyes, top);
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support) || hit.getDirection() != Direction.UP) continue;

            int dx = pos.getX() - feet.getX(), dy = pos.getY() - feet.getY(), dz = pos.getZ() - feet.getZ();
            double score = dx * dx + dz * dz + dy * dy * 2;
            if (score < bestScore) {
                bestScore = score;
                best = hit;
            }
        }
        return best;
    }

    /** Where a ray from the eyes hits the placed table: its center or the middle of a face. */
    private BlockHitResult tableHit() {
        if (tablePos == null || !mc.level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) return null;

        Vec3 eyes = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange() - 0.25;
        Vec3 center = Vec3.atCenterOf(tablePos);
        if (eyes.distanceTo(center) <= reach) {
            BlockHitResult hit = raycast(eyes, center);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(tablePos)) return hit;
        }
        for (Direction direction : Direction.values()) {
            Vec3 point = center.add(direction.getStepX() * 0.45, direction.getStepY() * 0.45, direction.getStepZ() * 0.45);
            if (eyes.distanceTo(point) > reach) continue;
            BlockHitResult hit = raycast(eyes, point);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(tablePos)) return hit;
        }
        return null;
    }

    private BlockHitResult raycast(Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    private boolean touchesFluid(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!mc.level.getFluidState(pos.relative(direction)).isEmpty()) return true;
        }
        return false;
    }

    private ItemEntity findTableDrop() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity item && !item.isRemoved() && item.getItem().is(Items.CRAFTING_TABLE)
                && item.distanceToSqr(Vec3.atCenterOf(tablePos)) < 4 * 4) return item;
        }
        return null;
    }

    private boolean tableBack() {
        return mc.player.getInventory().countItem(Items.CRAFTING_TABLE) >= tableCount;
    }

    private CraftingMenu handler() {
        return mc.player.containerMenu instanceof CraftingMenu handler ? handler : null;
    }

    private static boolean gridEmpty(CraftingMenu handler) {
        for (Slot slot : handler.getInputGridSlots()) {
            if (slot.hasItem()) return false;
        }
        return true;
    }
}
