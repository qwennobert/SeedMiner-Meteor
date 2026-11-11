package com.qwennobert.seedminer.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.process.ICustomGoalProcess;
import net.minecraft.core.BlockPos;

/**
 * Thin wrapper around the Baritone API. Must only be used when {@code BaritoneUtils.IS_AVAILABLE} is true,
 * otherwise the Baritone classes don't exist.
 */
public final class BaritoneControl {
    private Goal goal;

    private Boolean prevAllowBreak, prevAllowPlace, prevAllowParkour, prevExternalAutoTool;

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    /**
     * Walks to the block so that the player's feet or head end up inside it, which means Baritone has to break it.
     */
    public void mine(BlockPos pos) {
        goal = new GoalTwoBlocks(pos);
        baritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    /** Walks into the block, e.g. to pick up an item lying there. Standing next to it can be just out of pickup range. */
    public void walkTo(BlockPos pos) {
        goal = new GoalBlock(pos);
        baritone().getCustomGoalProcess().setGoalAndPath(goal);
    }

    /** True while Baritone is still working on the goal that was set by {@link #mine(BlockPos)} or {@link #walkTo(BlockPos)}. */
    public boolean isWorking() {
        if (goal == null) return false;

        ICustomGoalProcess process = baritone().getCustomGoalProcess();
        return process.isActive() && process.getGoal() == goal;
    }

    /** True if Baritone has been taken over by something else, e.g. the player typed a Baritone command. */
    public boolean wasHijacked() {
        if (goal == null) return false;

        IBaritone baritone = baritone();
        Goal current = baritone.getCustomGoalProcess().getGoal();
        if (current != null && current != goal) return true;

        return baritone.getMineProcess().isActive()
            || baritone.getBuilderProcess().isActive()
            || baritone.getFollowProcess().isActive()
            || baritone.getGetToBlockProcess().isActive()
            || baritone.getExploreProcess().isActive()
            || baritone.getFarmProcess().isActive();
    }

    public void stop() {
        if (goal != null) {
            goal = null;
            baritone().getPathingBehavior().cancelEverything();
        }
    }

    public void configure(boolean allowPlace, boolean allowParkour) {
        Settings settings = BaritoneAPI.getSettings();
        if (prevAllowBreak == null) {
            prevAllowBreak = settings.allowBreak.value;
            prevAllowPlace = settings.allowPlace.value;
            prevAllowParkour = settings.allowParkour.value;
        }

        settings.allowBreak.value = true;
        settings.allowPlace.value = allowPlace;
        settings.allowParkour.value = allowParkour;
    }

    public void restore() {
        if (prevAllowBreak == null) return;

        Settings settings = BaritoneAPI.getSettings();
        settings.allowBreak.value = prevAllowBreak;
        settings.allowPlace.value = prevAllowPlace;
        settings.allowParkour.value = prevAllowParkour;
        prevAllowBreak = prevAllowPlace = prevAllowParkour = null;
    }

    /**
     * Stops Baritone from switching tools itself while still planning paths as if the best tool is used,
     * because the module selects the tools instead.
     */
    public void setExternalAutoTool(boolean enabled) {
        Settings settings = BaritoneAPI.getSettings();

        if (enabled) {
            if (prevExternalAutoTool == null) prevExternalAutoTool = settings.assumeExternalAutoTool.value;
            settings.assumeExternalAutoTool.value = true;
        }
        else if (prevExternalAutoTool != null) {
            settings.assumeExternalAutoTool.value = prevExternalAutoTool;
            prevExternalAutoTool = null;
        }
    }
}
