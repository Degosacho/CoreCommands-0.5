package me.dego.estudio.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

import java.util.List;

public interface Requirement {

    boolean check(Player player, PlaceholderResolver resolver);

    List<String> getDenyCommands();

    /**
     * Construye un Requirement a partir de una ConfigurationSection del tipo:
     *   type: has money
     *   amount: 200
     *   deny_commands: [...]
     */
    static Requirement fromConfig(ConfigurationSection section, Plugin plugin) {
        String type = section.getString("type", "").toLowerCase();
        List<String> denyCommands = section.getStringList("deny_commands");

        return switch (type) {
            case "has money" -> new HasMoneyRequirement(section.get("amount"), denyCommands, plugin);
            case "has item" -> new HasItemRequirement(
                    section.getString("material"), section.getString("custom_item"),
                    section.get("amount"), denyCommands);
            case "has permission" -> new PermissionRequirement(
                    section.getString("permission"), denyCommands);
            case "javascript" -> new ExpressionRequirement(
                    section.getString("expression"), denyCommands);
            default -> new AlwaysTrueRequirement(denyCommands);
        };
    }

    // ============================================================

    class AlwaysTrueRequirement implements Requirement {
        private final List<String> denyCommands;
        public AlwaysTrueRequirement(List<String> denyCommands) { this.denyCommands = denyCommands; }
        @Override public boolean check(Player player, PlaceholderResolver resolver) { return true; }
        @Override public List<String> getDenyCommands() { return denyCommands; }
    }

    class HasMoneyRequirement implements Requirement {
        private final Object rawAmount; // puede ser número fijo o string con placeholder %math_...%
        private final List<String> denyCommands;
        private final Plugin plugin;

        public HasMoneyRequirement(Object rawAmount, List<String> denyCommands, Plugin plugin) {
            this.rawAmount = rawAmount;
            this.denyCommands = denyCommands;
            this.plugin = plugin;
        }

        @Override
        public boolean check(Player player, PlaceholderResolver resolver) {
            double amount = resolver.resolveNumber(String.valueOf(rawAmount), player, null);

            RegisteredServiceProvider<Economy> rsp =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) return false;

            Economy economy = rsp.getProvider();
            return economy.getBalance(player) >= amount;
        }

        @Override public List<String> getDenyCommands() { return denyCommands; }
    }

    class HasItemRequirement implements Requirement {
        private final String materialName;
        private final String customItemBase64; // para ítems de mods capturados con /shop capture
        private final Object rawAmount;
        private final List<String> denyCommands;


        public HasItemRequirement(String materialName, String customItemBase64, Object rawAmount, List<String> denyCommands) {
            this.materialName = materialName;
            this.customItemBase64 = customItemBase64;
            this.rawAmount = rawAmount;
            this.denyCommands = denyCommands;
        }

        @Override
        public boolean check(Player player, PlaceholderResolver resolver) {
            int amount = (int) resolver.resolveNumber(String.valueOf(rawAmount), player, null);

            if (customItemBase64 != null) {
                ItemStack template = ItemSerializer.deserialize(customItemBase64);
                if (template == null) return false;

                int owned = 0;
                for (ItemStack stack : player.getInventory().getContents()) {
                    if (stack != null && stack.isSimilar(template)) {
                        owned += stack.getAmount();
                    }
                }
                return owned >= amount;
            }

            Material material = Material.matchMaterial(materialName);
            if (material == null) return false;

            int owned = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    owned += item.getAmount();
                }
            }
            return owned >= amount;
        }

        @Override public List<String> getDenyCommands() { return denyCommands; }
    }

    class PermissionRequirement implements Requirement {
        private final String permission;
        private final List<String> denyCommands;

        public PermissionRequirement(String permission, List<String> denyCommands) {
            this.permission = permission;
            this.denyCommands = denyCommands;
        }

        @Override
        public boolean check(Player player, PlaceholderResolver resolver) {
            return player.hasPermission(permission);
        }

        @Override public List<String> getDenyCommands() { return denyCommands; }
    }

    /**
     * Soporta expresiones simples tipo "%player_empty_slots% > 6" DESPUÉS de
     * resolver los placeholders. No es un motor JS real (evita añadir Nashorn/
     * dependencias externas); solo entiende comparaciones numéricas simples:
     * >, <, >=, <=, ==, !=
     */
    class ExpressionRequirement implements Requirement {
        private final String rawExpression;
        private final List<String> denyCommands;

        public ExpressionRequirement(String rawExpression, List<String> denyCommands) {
            this.rawExpression = rawExpression;
            this.denyCommands = denyCommands;
        }

        @Override
        public boolean check(Player player, PlaceholderResolver resolver) {
            String resolved = resolver.resolve(rawExpression, player, null).trim();

            String[] operators = {">=", "<=", "==", "!=", ">", "<"};
            for (String op : operators) {
                int idx = resolved.indexOf(op);
                if (idx == -1) continue;

                double left = Double.parseDouble(resolved.substring(0, idx).trim());
                double right = Double.parseDouble(resolved.substring(idx + op.length()).trim());

                return switch (op) {
                    case ">=" -> left >= right;
                    case "<=" -> left <= right;
                    case "==" -> left == right;
                    case "!=" -> left != right;
                    case ">" -> left > right;
                    case "<" -> left < right;
                    default -> false;
                };
            }
            return false;
        }

        @Override public List<String> getDenyCommands() { return denyCommands; }
    }
}
