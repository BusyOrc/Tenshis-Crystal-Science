package io.github.lounode.ae2cs.common.menu;

import io.github.lounode.ae2cs.common.block.entity.CircuitEtcherBlockEntity;
import io.github.lounode.ae2cs.common.init.AECSMenus;

import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class CircuitEtcherMenu extends UpgradeableMenu<CircuitEtcherBlockEntity> {

    private static final String SET_RECIPE_LOCK_ACTION = "set_recipe_lock";

    @GuiSync(10)
    public int recipeProgress;

    @GuiSync(11)
    public int recipeNeedTicks;

    @GuiSync(12)
    public double currentEnergy;

    @GuiSync(13)
    public double maxEnergy;

    /**
     * 配方锁定开关状态（仅用于客户端按钮显示）
     */
    @GuiSync(14)
    public boolean recipeLock;

    public CircuitEtcherMenu(int id, Inventory ip, CircuitEtcherBlockEntity host) {
        super(AECSMenus.CIRCUIT_ETCHER_MENU.get(), id, ip, host);
        registerClientAction(SET_RECIPE_LOCK_ACTION, Boolean.class, this::setRecipeLock);

        AppEngInternalInventory inputInv = getHost().getInputInv();
        AppEngInternalInventory outputInv = getHost().getOutputInv();
        for (int i = 0; i < inputInv.size(); i++) {
            AppEngSlot inputSlot = new AppEngSlot(inputInv, i);
            this.addSlot(inputSlot, SlotSemantics.MACHINE_INPUT);
        }
        for (int i = 0; i < outputInv.size(); i++) {
            AppEngSlot outputSlot = new AppEngSlot(outputInv, i) {

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            };
            this.addSlot(outputSlot, SlotSemantics.MACHINE_OUTPUT);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {}

    public void sendSetRecipeLock(boolean enabled) {
        sendClientAction(SET_RECIPE_LOCK_ACTION, enabled);
    }

    private void setRecipeLock(boolean enabled) {
        getHost().setRecipeLockEnabled(enabled);
    }

    @Override
    public void broadcastChanges() {
        recipeNeedTicks = getHost().getActiveRecipeEnergyCost();
        recipeProgress = getHost().getRecipeProgress();
        maxEnergy = getHost().getAEMaxPower();
        currentEnergy = getHost().getAECurrentPower();
        recipeLock = getHost().isRecipeLockEnabled();

        super.broadcastChanges();
    }
}
