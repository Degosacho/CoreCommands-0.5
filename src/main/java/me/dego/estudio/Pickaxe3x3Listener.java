package me.dego.estudio;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class Pickaxe3x3Listener implements Listener {

    private final NamespacedKey pico3x3;


    // Constructor del Pico 3x3
    public Pickaxe3x3Listener(org.bukkit.plugin.Plugin plugin) {
        this.pico3x3 = new NamespacedKey(plugin, "pico3x3");
    }

    // Marca el pico para que funcione el 3x3
    public static void markAsSpecialPickaxe(ItemStack item, org.bukkit.plugin.Plugin plugin){
        ItemMeta meta = item.getItemMeta();
        if(meta == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "pico3x3");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    // Comprueba si el pico tiene la Key
    private boolean isSpecialPickaxe(ItemStack item){
        if(item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(pico3x3, PersistentDataType.BYTE);
    }


    // Le da prioridad ALTA al evento del pico
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event){
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();


        // Solo funciona si tiene el item en la mano
        if(!isSpecialPickaxe(tool)){ return; }

        // Bloque que minamos
        Block originBlock = event.getBlock();

        // Dirección a la que miramos cuando lo minamos
        BlockFace face = getTargetFace(player);

        // Determina la orientación en el plano para el minado 3x3
        List<Block> extraBlock = get3x3Plane(originBlock,face);

        for(Block b : extraBlock){
            if(b.equals(originBlock)) continue;;
            if(b.getType().isAir()) continue;;

            boolean broken = b.breakNaturally(tool);

            if(broken){
                applyToolDamage(player, tool);
            }
        }

    }

    // Dirección a la que miramos cuando lo minamos
    private BlockFace getTargetFace(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch >= 60) return BlockFace.DOWN;
        if (pitch <= -60) return BlockFace.UP;

        float yaw = (player.getLocation().getYaw() + 360) % 360;
        if (yaw >= 45 && yaw < 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return BlockFace.NORTH;
        if (yaw >= 225 && yaw < 315) return BlockFace.EAST;
        return BlockFace.SOUTH;
    }

    // Calcula el plano 3x3 a minar
    private List<Block> get3x3Plane(Block origin, BlockFace face){

        List<Block> blocks = new ArrayList<>();
        Location loc = origin.getLocation();

        int[][] offsets;

        // Si el minado es Arriba/Abajo, el plano es horizontal X/Z
        if(face == BlockFace.UP || face == BlockFace.DOWN){
            offsets = new int[][]{
                    {-1,0,-1},{0,0,-1},{1,0,-1},
                    {-1,0,0},{0,0,0},{1,0,0},
                    {-1,0,1},{0,0,1},{1,0,1}
            };
        }

        // Si el minado es Norte/Sur X/Y
        else if(face == BlockFace.NORTH || face == BlockFace.SOUTH){
            offsets = new int[][]{
                    {-1,-1,0},{0,-1,0},{1,-1,0},
                    {-1,0,0},{0,0,0},{1,0,0},
                    {-1,1,0},{0,1,0},{1,1,0}
            };
        }

        // Si el minado es Este/Oeste Z/Y
        else {
            offsets = new int[][]{
                    {0,-1,-1},{0,-1,0},{0,-1,1},
                    {0,0,-1},{0,0,0},{0,0,1},
                    {0,1,-1},{0,1,0},{0,1,1}
            };
        }

        // Comprueba si el bloque se puede minar con pico y lo rompe
        for(int[] offset : offsets){

            Location targetLoc = loc.clone().add(offset[0], offset[1], offset[2]);
            Block targetBlock = targetLoc.getBlock();

            if(Tag.MINEABLE_PICKAXE.isTagged(targetBlock.getType())) {
                blocks.add(targetBlock);
            }
        }
        return blocks;
    }

    // Aplica el daño a la herramienta según lo usado
    private void applyToolDamage(Player player, ItemStack tool){
        if(tool.getItemMeta() == null) return;
        if(tool.getItemMeta().isUnbreakable()) return;

        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        // Probabilidad de no gastar durabilidad según el nivel de unbreaking
        if(unbreakingLevel > 0 && Math.random() < (1.0 / (unbreakingLevel + 1))){ return; }

        if(tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable){
            damageable.setDamage(damageable.getDamage() + 1);
            ItemMeta meta = (ItemMeta) damageable;
            tool.setItemMeta(meta);

            if(damageable.getDamage() >= tool.getType().getMaxDurability()){
                player.getInventory().setItemInMainHand(null); // se rompe el pico
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }

    }


}
