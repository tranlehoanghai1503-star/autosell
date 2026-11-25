package com.hai.selldonutsmp;

import com.hai.selldonutsmp.modules.AutoPayModule;
import com.hai.selldonutsmp.modules.AutoSellModule;
import com.hai.selldonutsmp.modules.SellAxeModule;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class SellDonutSMPAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("SellDonutSMP by Hai Tran");

    @Override
    public void onInitialize() {
        Modules.get().add(new SellAxeModule());
        Modules.get().add(new AutoPayModule());
        Modules.get().add(new AutoSellModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.hai.selldonutsmp";
    }

    @Override
    public GithubRepo getRepo() {
        return null;
    }
}
