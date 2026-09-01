package me.dego.estudio.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class ShopItem {

    private final String key; // nombre de la entrada en el YAML, ej. "Golden Apple"

    private Material material;
    private String customHeadTexture; // para 'basehead-BASE64...'
    private String customItemBase64;  // para 'custom_item: BASE64...' (ítems de mods)
    private int amount = 1;
    private String displayName;
    private List<String> lore = new ArrayList<>();

    private final List<Integer> slots = new ArrayList<>();

    private List<String> leftClickCommands = new ArrayList<>();
    private List<String> rightClickCommands = new ArrayList<>();
    private List<String> shiftLeftClickCommands = new ArrayList<>();
    private List<String> shiftRightClickCommands = new ArrayList<>();

    private Requirement leftClickRequirement;
    private Requirement rightClickRequirement;
    private Requirement shiftLeftClickRequirement;
    private Requirement shiftRightClickRequirement;

    public ShopItem(String key) {
        this.key = key;
    }

    /** Construye un ShopItem a partir de la ConfigurationSection de una entrada del YAML. */
    public static ShopItem fromConfig(String key, ConfigurationSection section, Plugin plugin) {
        ShopItem item = new ShopItem(key);

        String materialRaw = section.getString("material", "STONE");

        if (section.contains("custom_item")) {
            // Ítem de mod capturado con /shop capture: prioridad sobre 'material'
            item.customItemBase64 = section.getString("custom_item");
            item.material = null; // se ignora, se usa el ItemStack deserializado directamente
        } else if (materialRaw.startsWith("basehead-")) {
            item.customHeadTexture = materialRaw.substring("basehead-".length());
            item.material = Material.PLAYER_HEAD;
        } else {
            Material mat = Material.matchMaterial(materialRaw.toUpperCase());
            item.material = (mat != null) ? mat : Material.STONE;
        }

        item.amount = section.getInt("amount", 1);
        item.displayName = section.getString("display_name", key);
        item.lore = section.getStringList("lore");

        // Un ítem puede definir 'slot' (uno) o 'slots' (varios, ej. para rellenos de cristal)
        if (section.isList("slots")) {
            item.slots.addAll(section.getIntegerList("slots"));
        } else if (section.contains("slot")) {
            item.slots.add(section.getInt("slot"));
        }

        item.leftClickCommands = section.getStringList("left_click_commands");
        item.rightClickCommands = section.getStringList("right_click_commands");
        item.shiftLeftClickCommands = section.getStringList("shift_left_click_commands");
        item.shiftRightClickCommands = section.getStringList("shift_right_click_commands");

        item.leftClickRequirement = loadRequirement(section, "left_click_requirement", plugin);
        item.rightClickRequirement = loadRequirement(section, "right_click_requirement", plugin);
        item.shiftLeftClickRequirement = loadRequirement(section, "shift_left_click_requirement", plugin);
        item.shiftRightClickRequirement = loadRequirement(section, "shift_right_click_requirement", plugin);

        return item;
    }

    private static Requirement loadRequirement(ConfigurationSection section, String path, Plugin plugin) {
        ConfigurationSection reqSection = section.getConfigurationSection(path + ".requirements");
        if (reqSection == null) return null;

        // Tu YAML permite varios requisitos bajo 'requirements:' (ej. has_hueco + has_money a la vez).
        // Los combinamos en un requisito compuesto: deben cumplirse TODOS.
        List<Requirement> all = new ArrayList<>();
        for (String reqKey : reqSection.getKeys(false)) {
            ConfigurationSection single = reqSection.getConfigurationSection(reqKey);
            if (single != null) {
                all.add(Requirement.fromConfig(single, plugin));
            }
        }
        if (all.isEmpty()) return null;
        return new CompositeRequirement(all);
    }

    // ============================================================
    //  Getters
    // ============================================================

    public String getKey() { return key; }
    public Material getMaterial() { return material; }
    public String getCustomHeadTexture() { return customHeadTexture; }
    public String getCustomItemBase64() { return customItemBase64; }
    public int getAmount() { return amount; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return lore; }
    public List<Integer> getSlots() { return slots; }

    public List<String> getLeftClickCommands() { return leftClickCommands; }
    public List<String> getRightClickCommands() { return rightClickCommands; }
    public List<String> getShiftLeftClickCommands() { return shiftLeftClickCommands; }
    public List<String> getShiftRightClickCommands() { return shiftRightClickCommands; }

    public Requirement getLeftClickRequirement() { return leftClickRequirement; }
    public Requirement getRightClickRequirement() { return rightClickRequirement; }
    public Requirement getShiftLeftClickRequirement() { return shiftLeftClickRequirement; }
    public Requirement getShiftRightClickRequirement() { return shiftRightClickRequirement; }

    /** Requisito compuesto: deben cumplirse TODOS los requisitos internos para pasar. */
    private static class CompositeRequirement implements Requirement {
        private final List<Requirement> requirements;
        private List<String> lastFailedDenyCommands = new ArrayList<>();

        CompositeRequirement(List<Requirement> requirements) {
            this.requirements = requirements;
        }

        @Override
        public boolean check(org.bukkit.entity.Player player, PlaceholderResolver resolver) {
            for (Requirement r : requirements) {
                if (!r.check(player, resolver)) {
                    lastFailedDenyCommands = r.getDenyCommands();
                    return false;
                }
            }
            return true;
        }

        @Override
        public List<String> getDenyCommands() {
            return lastFailedDenyCommands;
        }
    }
}
