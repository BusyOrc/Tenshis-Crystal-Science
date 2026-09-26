package io.github.lounode.ae2cs.common.block.entity;

import io.github.lounode.ae2cs.api.cap.ProvideCaps;
import io.github.lounode.ae2cs.api.ids.AECSConstants;
import io.github.lounode.ae2cs.api.settings.AECSSettings;
import io.github.lounode.ae2cs.api.settings.PulseCentrifugeMode;
import io.github.lounode.ae2cs.api.submenu.CustomReturnableSubMenuHost;
import io.github.lounode.ae2cs.common.init.AECSBlockEntities;
import io.github.lounode.ae2cs.common.init.AECSBlockProperties;
import io.github.lounode.ae2cs.common.init.AECSBlocks;
import io.github.lounode.ae2cs.common.init.AECSItems;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.machine.MachineFluidHost;
import io.github.lounode.ae2cs.common.machine.MachineFluidTanks;
import io.github.lounode.ae2cs.common.machine.component.AppEngInvComponent;
import io.github.lounode.ae2cs.common.machine.component.InvPort;
import io.github.lounode.ae2cs.common.machine.component.SideConfigComponent;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeCreateCompat;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeOutput;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeProcess;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeRecipe;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ProvideCaps(IItemHandler.class)
@ProvideCaps(IFluidHandler.class)
public class PulseCentrifugeBlockEntity extends AENetworkedSelfPoweredBlockEntity implements IUpgradeableObject,
                                        CustomReturnableSubMenuHost,
                                        MachineFluidHost,
                                        IConfigurableObject {

    private static final double BASIC_ENERGY_COST_PER_TICK = 200;
    private static final int FLUID_TANK_CAPACITY = 16000;
    /** 洗涤模式的鼓风机兼容配方强制消耗的水量 */
    private static final int FLUID_PER_OPERATION = PulseCentrifugeCreateCompat.SPLASHING_WATER_COST;

    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AECSBlocks.PULSE_CENTRIFUGE_BLOCK,
            4, this::onUpgradesChanged);
    private final MachineFluidTanks fluidTanks = new MachineFluidTanks(FLUID_TANK_CAPACITY, this::setChanged,
            this::setChanged);
    private final IConfigManager configManager = IConfigManager.builder(this::onConfigChanged)
            .registerSetting(AECSSettings.PULSE_CENTRIFUGE_MODE, PulseCentrifugeMode.CENTRIFUGE)
            .build();

    private int speedMultiplier = 1;
    private int overloadCards;
    private int activeRecipeEnergyCost;
    private int recipeProgress;
    private boolean needRefreshRecipeState = true;
    private boolean processing;

    @Nullable
    private PulseCentrifugeProcess activeRecipe;
    @Nullable
    private ResourceLocation activeRecipeId;

    public PulseCentrifugeBlockEntity(BlockPos pos, BlockState blockState) {
        super(AECSBlockEntities.PULSE_CENTRIFUGE_BLOCK_ENTITY.get(), pos, blockState,
                80000, false, AccessRestriction.WRITE);
        getMainNode().setIdlePowerUsage(0);

        AppEngInternalInventory input = new AppEngInternalInventory(1) {

            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                needRefreshRecipeState = true;
                setChanged();
            }
        };
        AppEngInternalInventory output = new AppEngInternalInventory(4) {

            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                setChanged();
            }
        };

        AppEngInvComponent inventory = new AppEngInvComponent();
        inventory.addPort(InvPort.INPUT, input);
        inventory.addPort(InvPort.WORK, input);
        inventory.addPort(InvPort.OUTPUT, output);
        getMachineComponents().add(inventory);
        getMachineComponents().add(new SideConfigComponent());
    }

    public AppEngInternalInventory getInputInv() {
        return getMachineComponents().getService(AppEngInvComponent.class).port(InvPort.INPUT);
    }

    public AppEngInternalInventory getOutputInv() {
        return getMachineComponents().getService(AppEngInvComponent.class).port(InvPort.OUTPUT);
    }

    public MachineFluidTanks getFluidTanks() {
        return fluidTanks;
    }

    @Override
    public IFluidHandler getFluidHandler() {
        return fluidTanks;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 当前机器模式（离心 / 缠魂 / 洗涤）
     */
    public PulseCentrifugeMode getMode() {
        PulseCentrifugeMode mode = configManager.getSetting(AECSSettings.PULSE_CENTRIFUGE_MODE);
        return mode == null ? PulseCentrifugeMode.CENTRIFUGE : mode;
    }

    private void onConfigChanged() {
        // 模式改变后立刻重新评估配方
        needRefreshRecipeState = true;
        saveChanges();
    }

    public int getRecipeProgress() {
        return recipeProgress;
    }

    public int getActiveRecipeEnergyCost() {
        return activeRecipeEnergyCost;
    }

    public boolean isProcessing() {
        return processing;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private void onUpgradesChanged() {
        overloadCards = Math.min(2, upgrades.getInstalledUpgrades(AECSItems.OVERLOAD_CARD));
        speedMultiplier = overloadCards > 0 ? 1 : 1 << Math.min(4, upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        saveChanges();
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (level == null || level.isClientSide()) return;

        setProcessing(processRecipeTick());
    }

    private boolean processRecipeTick() {
        // 每秒重算一次配方（输入变化、模式变化、数据包重载都能及时反映）
        if (needRefreshRecipeState || level.getGameTime() % 20 == 0) {
            updateActiveRecipe();
            needRefreshRecipeState = false;
        }
        if (activeRecipe == null) {
            recipeProgress = 0;
            return false;
        }

        boolean consumedEnergy = false;
        if (recipeProgress < activeRecipeEnergyCost) {
            if (getAECurrentPower() <= 0) return false;

            double neededEnergy = Math.min(getEnergyPerTick(), activeRecipeEnergyCost - recipeProgress);
            int availableEnergy = (int) Math.floor(extractAEPower(neededEnergy, Actionable.SIMULATE));
            if (availableEnergy <= 0) return false;
            int actualCost = (int) Math.floor(extractAEPower(availableEnergy, Actionable.MODULATE));
            if (actualCost <= 0) return false;

            recipeProgress = Math.min(recipeProgress + actualCost, activeRecipeEnergyCost);
            consumedEnergy = true;
            setChanged();
        }
        if (recipeProgress < activeRecipeEnergyCost) return consumedEnergy;

        // 进度跑满：此时才按概率 roll 产物（概率产物可能一个都不出）
        PulseCentrifugeProcess recipe = activeRecipe;
        List<ItemStack> rolledResults = recipe.rollResults(level.getRandom());
        List<ItemStack> outputPlan = planOutputInsertion(getOutputInv(), rolledResults);
        if (outputPlan == null) return consumedEnergy;

        FluidStack fluidResult = recipe.fluidOutput();
        if (!hasRequiredFluids(recipe)) return consumedEnergy;
        if (!fluidResult.isEmpty() && fluidTanks.output().fill(fluidResult, IFluidHandler.FluidAction.SIMULATE) < fluidResult.getAmount()) {
            return consumedEnergy;
        }

        if (!consumeInput(recipe)) {
            clearRecipeState();
            return consumedEnergy;
        }

        commitOutputPlan(outputPlan);
        consumeFluids(recipe);
        if (!fluidResult.isEmpty()) fluidTanks.output().fill(fluidResult, IFluidHandler.FluidAction.EXECUTE);
        recipeProgress = 0;
        setChanged();
        return consumedEnergy;
    }

    /**
     * 输入流体检查：
     * - 洗涤模式的鼓风机兼容配方强制要求 1B 水；
     * - 配方自身声明的输入流体也必须满足。
     */
    private boolean hasRequiredFluids(PulseCentrifugeProcess recipe) {
        FluidStack stored = fluidTanks.input().getFluid();
        if (recipe.requiresWater()) {
            if (!stored.is(Fluids.WATER) || stored.getAmount() < FLUID_PER_OPERATION) {
                return false;
            }
        }
        return recipe.fluidInput() == null || recipe.fluidInput().test(stored);
    }

    private void consumeFluids(PulseCentrifugeProcess recipe) {
        if (recipe.requiresWater()) {
            fluidTanks.input().drain(FLUID_PER_OPERATION, IFluidHandler.FluidAction.EXECUTE);
        }
        if (recipe.fluidInput() != null) {
            fluidTanks.input().drain(recipe.fluidInput().amount(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private double getEnergyPerTick() {
        double normalEnergy = BASIC_ENERGY_COST_PER_TICK * speedMultiplier;
        if (overloadCards == 0 || activeRecipeEnergyCost <= 0) return normalEnergy;

        int targetTicks = overloadCards == 1 ? 4 : 1;
        return Math.max(normalEnergy, Math.ceil((double) activeRecipeEnergyCost / targetTicks));
    }

    /**
     * 按照当前模式挑选配方：先找 ae2cs 自己的离心配方，其次找机械动力鼓风机的缠魂 / 洗涤配方
     */
    private void updateActiveRecipe() {
        if (level == null || level.isClientSide()) return;

        PulseCentrifugeProcess process = findOwnProcess();
        if (process == null) {
            process = findCreateProcess();
        }

        if (process == null) {
            clearRecipeState();
            return;
        }

        if (activeRecipe == null || !activeRecipe.id().equals(process.id())) {
            recipeProgress = 0;
        }
        activeRecipe = process;
        activeRecipeEnergyCost = process.energyCost();
    }

    @Nullable
    private PulseCentrifugeProcess findOwnProcess() {
        SingleRecipeInput input = new SingleRecipeInput(getInputInv().getStackInSlot(0));
        PulseCentrifugeMode mode = getMode();

        // 优先沿用配方管理器的默认挑选结果（与加入模式功能之前的行为保持一致）
        var match = level.getRecipeManager().getRecipeFor(AECSRecipeTypes.PULSE_CENTRIFUGE.get(), input, level);
        if (match.isPresent() && match.get().value().mode() == mode) {
            return toProcess(match.get());
        }

        // 默认结果模式不符（例如同一物品另有一条别的模式的配方），再按 id 顺序找当前模式的配方
        return level.getRecipeManager().byType(AECSRecipeTypes.PULSE_CENTRIFUGE.get()).stream()
                .filter(holder -> holder.value().mode() == mode && holder.value().matches(input, level))
                .sorted(java.util.Comparator.comparing(holder -> holder.id().toString()))
                .findFirst()
                .map(this::toProcess)
                .orElse(null);
    }

    private PulseCentrifugeProcess toProcess(RecipeHolder<PulseCentrifugeRecipe> holder) {
        PulseCentrifugeRecipe recipe = holder.value();
        return new PulseCentrifugeProcess(holder.id(), recipe.mode(), recipe.input(),
                PulseCentrifugeProcess.limitResults(recipe.results()), recipe.fluidInput(), recipe.fluidOutput(),
                recipe.energyCost(), false);
    }

    /**
     * 机械动力的缠魂 / 洗涤配方（Create 未安装或模式为离心时返回 null）
     */
    @Nullable
    private PulseCentrifugeProcess findCreateProcess() {
        PulseCentrifugeMode mode = getMode();
        if (mode == PulseCentrifugeMode.CENTRIFUGE) {
            return null;
        }
        // 注意：Create 是可选前置，只有确认已加载才会加载兼容类
        if (!ModList.get().isLoaded(AECSConstants.CREATE_ID)) {
            return null;
        }
        return PulseCentrifugeCreateCompat.findProcess(level, mode, getInputInv().getStackInSlot(0));
    }

    @Nullable
    static List<ItemStack> planOutputInsertion(AppEngInternalInventory output, List<ItemStack> results) {
        AppEngInternalInventory simulated = new AppEngInternalInventory(4);
        for (int slot = 0; slot < simulated.size(); slot++) {
            simulated.setItemDirect(slot, output.getStackInSlot(slot).copy());
        }
        for (ItemStack result : results) {
            if (!simulated.addItems(result.copy(), false).isEmpty()) return null;
        }

        List<ItemStack> finalSlots = new ArrayList<>(simulated.size());
        for (int slot = 0; slot < simulated.size(); slot++) {
            finalSlots.add(simulated.getStackInSlot(slot).copy());
        }
        return List.copyOf(finalSlots);
    }

    private void commitOutputPlan(List<ItemStack> plan) {
        for (int slot = 0; slot < plan.size(); slot++) {
            getOutputInv().setItemDirect(slot, plan.get(slot).copy());
        }
    }

    private boolean consumeInput(PulseCentrifugeProcess recipe) {
        var required = recipe.input();
        ItemStack extracted = getInputInv().extractItem(0, required.count(), true);
        if (extracted.getCount() < required.count() || !required.test(extracted)) return false;

        getInputInv().extractItem(0, required.count(), false);
        return true;
    }

    private void clearRecipeState() {
        activeRecipe = null;
        activeRecipeEnergyCost = 0;
        recipeProgress = 0;
    }

    private void setProcessing(boolean processing) {
        this.processing = processing;
        BlockState state = getBlockState();
        if (state.hasProperty(AECSBlockProperties.ACTIVE) && state.getValue(AECSBlockProperties.ACTIVE) != processing) {
            level.setBlock(worldPosition, state.setValue(AECSBlockProperties.ACTIVE, processing), 2);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        upgrades.writeToNBT(data, "upgrades", registries);
        fluidTanks.writeToNbt(data, registries);
        this.configManager.writeToNBT(data, registries);
        data.putInt("recipe_progress", recipeProgress);
        if (activeRecipe != null) {
            data.putString("active_recipe_id", activeRecipe.id().toString());
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        upgrades.readFromNBT(data, "upgrades", registries);
        fluidTanks.readFromNbt(data, registries);
        this.configManager.readFromNBT(data, registries);
        recipeProgress = data.getInt("recipe_progress");
        if (data.contains("active_recipe_id")) {
            activeRecipeId = ResourceLocation.parse(data.getString("active_recipe_id"));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        onUpgradesChanged();
        if (activeRecipeId != null && level != null) {
            activeRecipe = resolveProcess(activeRecipeId);
        }
        updateActiveRecipe();
    }

    /**
     * 按 id 还原配方（既可能是本模组的离心配方，也可能是机械动力的鼓风机配方）
     */
    @Nullable
    private PulseCentrifugeProcess resolveProcess(ResourceLocation id) {
        if (level == null) {
            return null;
        }

        var holder = level.getRecipeManager().byKey(id);
        if (holder.isPresent() && holder.get().value() instanceof PulseCentrifugeRecipe) {
            @SuppressWarnings("unchecked")
            var recipeHolder = (RecipeHolder<PulseCentrifugeRecipe>) holder.get();
            return toProcess(recipeHolder);
        }

        if (ModList.get().isLoaded(AECSConstants.CREATE_ID)) {
            return PulseCentrifugeCreateCompat.resolveById(level, id);
        }
        return null;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
        fluidTanks.clear();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getItemFromBlockEntity());
    }
}
