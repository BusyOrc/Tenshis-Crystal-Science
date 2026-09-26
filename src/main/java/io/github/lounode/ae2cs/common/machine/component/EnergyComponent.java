package io.github.lounode.ae2cs.common.machine.component;

import io.github.lounode.ae2cs.api.util.ForgeEnergyAdapterUpgrade;
import io.github.lounode.ae2cs.common.machine.MachineComponentContainer;
import io.github.lounode.ae2cs.common.machine.MachineContext;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;
import appeng.me.energy.StoredEnergyAmount;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class EnergyComponent extends NetworkMachineComponent implements IAEPowerStorage {

    private final StoredEnergyAmount storedEnergy;
    private final boolean isAEPublicPowerStorage;
    private final AccessRestriction accessRestriction;

    /**
     * @param maxEnergy 最大能量容量
     */
    public EnergyComponent(IManagedGridNode node, double maxEnergy, boolean isAEPublicPowerStorage, AccessRestriction accessRestriction) {
        super(node);
        this.storedEnergy = new StoredEnergyAmount(0, maxEnergy, type -> markChanged());
        this.isAEPublicPowerStorage = isAEPublicPowerStorage;
        this.accessRestriction = accessRestriction;

        getMainNode().addService(IAEPowerStorage.class, this);
    }

    @Override
    public void onConstruct(MachineComponentContainer container) {
        super.onConstruct(container);
        container.exposeService(EnergyComponent.class, this);
    }

    @Override
    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeNbt(tag, registries);
        tag.putDouble("stored_energy", this.storedEnergy.getAmount());
    }

    @Override
    public void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        super.readNbt(tag, registries);
        this.storedEnergy.setStored(tag.getDouble("stored_energy"));
    }

    @Override
    public void onServerTick(MachineContext ctx) {
        super.onServerTick(ctx);

        // 公共储能设备不做主动交互
        if (isAEPublicPowerStorage()) return;

        IGrid grid = getMainNode().getGrid();
        if (grid == null) return;
        IEnergyService energyService = grid.getEnergyService();
        if (energyService == null) return;

        AccessRestriction flowDirection = getPowerFlow();
        switch (flowDirection) {
            case NO_ACCESS -> {}
            case READ -> {
                // 从自身提取，向AE网络输入
                double remaining = energyService.injectPower(getAECurrentPower(), Actionable.MODULATE);
                double needExtract = getAECurrentPower() - remaining;
                extractAEPower(needExtract, Actionable.MODULATE, PowerMultiplier.ONE);
            }
            case WRITE, READ_WRITE -> {
                // 从AE网络提取，输入到自身
                double needInsert = getAEMaxPower() - getAECurrentPower();
                double actualCanInsert = energyService.extractAEPower(needInsert, Actionable.MODULATE, PowerMultiplier.ONE);
                injectAEPower(actualCanInsert, Actionable.MODULATE);
            }
        }
    }

    // IAEPowerStorage---------
    @Override
    public final double injectAEPower(double amt, Actionable mode) {
        markChanged();
        return amt - storedEnergy.insert(amt, mode == Actionable.MODULATE);
    }

    @Override
    public final double getAEMaxPower() {
        return this.storedEnergy.getMaximum();
    }

    @Override
    public final double getAECurrentPower() {
        return this.storedEnergy.getAmount();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return this.isAEPublicPowerStorage;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return this.accessRestriction;
    }

    @Override
    public final double extractAEPower(double amt, Actionable mode, PowerMultiplier multiplier) {
        return multiplier.divide(this.extractAEPower(multiplier.multiply(amt), mode));
    }

    public double extractAEPower(double amt, Actionable mode) {
        markChanged();
        return this.storedEnergy.extract(amt, mode == Actionable.MODULATE);
    }

    public ForgeEnergyAdapterUpgrade getForgeEnergyAdapter() {
        return new ForgeEnergyAdapterUpgrade(this, this.accessRestriction);
    }

    /**
     * 把给定的 AE 能量按 AE2 官方换算比（默认 1 AE = 2 FE，可在 AE2 配置里改）主动推给一个 FE 接收方。
     * <p>
     * 这里推的是"还没进过自己缓存"的能量（例如刚发出来的电），因此不会碰自身存储；
     * 先模拟目标能吃多少，再真正喂进去，最后把没能推出去的部分换回 AE 返回给调用方。
     *
     * @param aeAmount 准备推出的 AE 能量
     * @return 没能推出的剩余 AE 能量
     */
    public double pushEnergyTo(IEnergyStorage target, double aeAmount) {
        if (aeAmount <= 0) return Math.max(0, aeAmount);

        int feAmount = (int) Math.floor(PowerUnit.AE.convertTo(PowerUnit.FE, aeAmount));
        if (feAmount <= 0) return aeAmount;

        int accepted = target.receiveEnergy(feAmount, true);
        if (accepted <= 0) return aeAmount;

        int given = target.receiveEnergy(accepted, false);
        if (given <= 0) return aeAmount;

        return Math.max(0, aeAmount - PowerUnit.FE.convertTo(PowerUnit.AE, given));
    }
}
