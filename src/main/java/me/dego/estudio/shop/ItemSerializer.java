package me.dego.estudio.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;

public class ItemSerializer {

    public static String serialize(ItemStack item) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return Base64.getEncoder().encodeToString(config.saveToString().getBytes());
    }

    public static ItemStack deserialize(String base64) {
        try {
            String yaml = new String(Base64.getDecoder().decode(base64));
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(yaml);
            return config.getItemStack("item");
        } catch (Exception e) {
            return null;
        }
    }
}
