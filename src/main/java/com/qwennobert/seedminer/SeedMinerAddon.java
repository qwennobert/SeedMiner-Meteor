package com.qwennobert.seedminer;

import com.mojang.logging.LogUtils;
import com.qwennobert.seedminer.commands.SeedMinerCommand;
import com.qwennobert.seedminer.hud.SeedMinerHud;
import com.qwennobert.seedminer.modules.SeedMiner;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public class SeedMinerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Seed Miner", Items.DIAMOND_PICKAXE::getDefaultInstance);
    public static final HudGroup HUD_GROUP = new HudGroup("Seed Miner");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Seed Miner");

        Modules.get().add(new SeedMiner());
        Commands.add(new SeedMinerCommand());
        Hud.get().register(SeedMinerHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.qwennobert.seedminer";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("qwennobert", "SeedMiner-Meteor");
    }
}
