package dev.rajce.ketchupZombies.shop.gui;

import dev.lupino1.config.Naming;
import dev.lupino1.config.NamingStrategy;
import dev.lupino1.config.YamlConfig;

import java.util.LinkedHashMap;
import java.util.Map;

@Naming(NamingStrategy.KEBAB)
public final class MenuGuiConfig extends YamlConfig {

    public String title = "&8Menu";
    public int rows = 3;
    public Map<String, GuiItemConfig> items = new LinkedHashMap<>();
}
