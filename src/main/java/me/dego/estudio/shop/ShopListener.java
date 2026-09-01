package me.dego.estudio.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ShopListener implements Listener {

    private final ShopManager shopManager;

    public ShopListener(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopMenu.ShopMenuInstance instance)) return;

        event.setCancelled(true); // nunca se pueden sacar ítems de la tienda

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        ShopItem shopItem = findShopItemBySlot(instance.getMenu(), event.getSlot());
        if (shopItem == null) return; // relleno de cristal u otro slot sin acción

        ClickType clickType = event.getClick();

        List<String> commands;
        Requirement requirement;

        switch (clickType) {
            case SHIFT_LEFT -> {
                commands = shopItem.getShiftLeftClickCommands();
                requirement = shopItem.getShiftLeftClickRequirement();
            }
            case SHIFT_RIGHT -> {
                commands = shopItem.getShiftRightClickCommands();
                requirement = shopItem.getShiftRightClickRequirement();
            }
            case RIGHT -> {
                commands = shopItem.getRightClickCommands();
                requirement = shopItem.getRightClickRequirement();
            }
            case LEFT -> {
                commands = shopItem.getLeftClickCommands();
                requirement = shopItem.getLeftClickRequirement();
            }
            default -> {
                return; // otros tipos de click (middle, drop...) se ignoran
            }
        }

        if (commands == null || commands.isEmpty()) return;

        if (requirement != null && !requirement.check(player, shopManager.getResolver())) {
            shopManager.getActionExecutor().executeAll(requirement.getDenyCommands(), player, instance.getMenu().getItems());
            return;
        }

        shopManager.getActionExecutor().executeAll(commands, player, instance.getMenu().getItems());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopMenu.ShopMenuInstance)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        shopManager.onPlayerCloseInventory(player.getUniqueId());
    }

    private ShopItem findShopItemBySlot(ShopMenu menu, int slot) {
        for (ShopItem item : menu.getItems().values()) {
            if (item.getSlots().contains(slot)) {
                return item;
            }
        }
        return null;
    }
}
