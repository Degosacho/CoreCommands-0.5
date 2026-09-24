package me.dego.estudio.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopMenu {

    private final String openCommand;
    private final String title;
    private final int size;
    private final String openPermission; // puede ser null si no hay open_requirement
    private final List<String> openDenyCommands;
    private final int updateIntervalTicks; // -1 si no hay auto-refresco
    private final List<String> openCommands; // ej. '[sound] ui.button.click', se ejecuta al ABRIR el menú
    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    public ShopMenu(String openCommand, String title, int size, String openPermission,
                    List<String> openDenyCommands, int updateIntervalTicks, List<String> openCommands) {
        this.openCommand = openCommand;
        this.title = title;
        this.size = size;
        this.openPermission = openPermission;
        this.openDenyCommands = openDenyCommands;
        this.updateIntervalTicks = updateIntervalTicks;
        this.openCommands = openCommands;
    }

    public List<String> getOpenCommands() { return openCommands; }

    public static ShopMenu fromConfig(ConfigurationSection root, Plugin plugin) {
        String openCommand = root.getString("open_command");
        String title = root.getString("menu_title", root.getString("title", "Tienda"));
        int size = root.getInt("size", 27);
        int updateInterval = root.getInt("update_interval", -1);

        String permission = null;
        List<String> denyCommands = new ArrayList<>();
        ConfigurationSection permSection = root.getConfigurationSection("open_requirement.requirements.permission");
        if (permSection != null) {
            permission = permSection.getString("permission");
            denyCommands = permSection.getStringList("deny_commands");
        }

        List<String> openCommands = root.getStringList("open_commands");

        ShopMenu menu = new ShopMenu(openCommand, title, size, permission, denyCommands, updateInterval, openCommands);

        ConfigurationSection itemsSection = root.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) continue;
                menu.items.put(key, ShopItem.fromConfig(key, itemSection, plugin));
            }
        }

        return menu;
    }

    public String getOpenCommand() { return openCommand; }
    public String getTitle() { return title; }
    public String getOpenPermission() { return openPermission; }
    public List<String> getOpenDenyCommands() { return openDenyCommands; }
    public int getUpdateIntervalTicks() { return updateIntervalTicks; }
    public Map<String, ShopItem> getItems() { return items; }

    /**
     * Construye el Inventory real para un jugador concreto, resolviendo
     * placeholders y colores en ese momento (por eso el refresco funciona:
     * basta con volver a llamar a este método).
     */
    public ShopMenuInstance build(Player player, PlaceholderResolver resolver, Map<String, ShopItem> menuItems) {
        ShopMenuInstance instance = new ShopMenuInstance(this);
        Inventory inventory = Bukkit.createInventory(instance, size, ColorUtil.colorize(resolver.resolve(title, player, menuItems)));

        for (ShopItem shopItem : items.values()) {
            ItemStack stack = buildItemStack(shopItem, player, resolver);
            for (int slot : shopItem.getSlots()) {
                if (slot >= 0 && slot < size) {
                    inventory.setItem(slot, stack);
                }
            }
        }

        instance.setInventory(inventory);
        return instance;
    }

    public ItemStack buildItemStack(ShopItem shopItem, Player player, PlaceholderResolver resolver) {
        ItemStack stack;

        if (shopItem.getCustomItemBase64() != null) {
            ItemStack template = ItemSerializer.deserialize(shopItem.getCustomItemBase64());
            if (template == null) {
                // Si la deserialización falla (ej. el mod que aportaba el ítem ya no está
                // instalado), caemos a un ítem vacío en vez de crashear el menú entero.
                stack = new ItemStack(Material.BARRIER);
                ItemMeta fallbackMeta = stack.getItemMeta();
                if (fallbackMeta != null) {
                    fallbackMeta.setDisplayName("§cÍtem no disponible");
                    stack.setItemMeta(fallbackMeta);
                }
                return stack;
            }
            stack = template.clone();
            stack.setAmount(Math.max(1, shopItem.getAmount()));
        } else {
            stack = new ItemStack(shopItem.getMaterial(), Math.max(1, shopItem.getAmount()));
        }

        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            String rawName = shopItem.getDisplayName();
            if (rawName != null) {
                meta.setDisplayName(ColorUtil.colorize(resolver.resolveWithMenu(rawName, player, items)));
            }

            List<String> lore = new ArrayList<>();
            for (String line : shopItem.getLore()) {
                lore.add(ColorUtil.colorize(resolver.resolveWithMenu(line, player, items)));
            }
            meta.setLore(lore);

            // Cabezas custom por textura base64 (basehead-XXXX en tu YAML)
            if (shopItem.getCustomHeadTexture() != null && meta instanceof SkullMeta skullMeta) {
                SkullTextureApplier.apply(skullMeta, shopItem.getCustomHeadTexture());
            }

            for(org.bukkit.inventory.ItemFlag flag : org.bukkit.inventory.ItemFlag.values()){
                meta.addItemFlags(flag);
            }

            stack.setItemMeta(meta);
        }

        return stack;
    }

    /**
     * Instancia concreta de un menú abierto (un Inventory real + referencia
     * al ShopMenu del que proviene). Implementa InventoryHolder para poder
     * identificar en el listener de clicks a qué ShopMenu pertenece.
     */
    public static class ShopMenuInstance implements InventoryHolder {
        private final ShopMenu menu;
        private Inventory inventory;

        public ShopMenuInstance(ShopMenu menu) {
            this.menu = menu;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public ShopMenu getMenu() { return menu; }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
