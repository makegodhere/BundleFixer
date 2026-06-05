package ru.yourname.bundlefixer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class BundleFixerPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("BundleFixer enabled – bundles will be fixed on inventory open.");
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        fixAllBundles(event.getInventory(), player);
    }

    private void fixAllBundles(Inventory inventory, Player viewer) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.BUNDLE) {
                ItemStack fixed = fixBundle(item, inventory, viewer);
                inventory.setItem(slot, fixed);
            }
        }
        viewer.updateInventory();
    }

    private ItemStack fixBundle(ItemStack bundle, Inventory parentInventory, Player viewer) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(bundle);
        BundleContents contents = nms.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.isEmpty()) return bundle;

        List<net.minecraft.world.item.ItemStack> items = new ArrayList<>(contents.items());
        boolean changed = false;

        for (net.minecraft.world.item.ItemStack stack : items) {
            if (stack.isDamaged() && stack.getMaxStackSize() > 1) {
                stack.remove(DataComponents.MAX_STACK_SIZE);
                stack.setCount(1);
                changed = true;
            }
        }

        if (!changed) return bundle;

        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        mutable.clear();

        int totalWeight = 0;
        List<net.minecraft.world.item.ItemStack> overflow = new ArrayList<>();

        for (net.minecraft.world.item.ItemStack stack : items) {
            int weight = 64 / stack.getMaxStackSize();
            if (totalWeight + weight <= 64) {
                mutable.tryInsert(stack);
                totalWeight += weight;
            } else {
                overflow.add(stack);
            }
        }

        net.minecraft.world.item.ItemStack fixedNms = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.BUNDLE
        );
        fixedNms.applyComponents(nms.getComponents());
        fixedNms.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());

        ItemStack fixedBukkit = CraftItemStack.asBukkitCopy(fixedNms);

        for (net.minecraft.world.item.ItemStack overStack : overflow) {
            ItemStack overBukkit = CraftItemStack.asBukkitCopy(overStack);
            var leftovers = parentInventory.addItem(overBukkit);
            if (!leftovers.isEmpty()) {
                for (ItemStack left : leftovers.values()) {
                    viewer.getWorld().dropItemNaturally(viewer.getLocation(), left);
                }
            }
        }

        return fixedBukkit;
    }
}