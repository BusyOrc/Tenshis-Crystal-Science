package io.github.lounode.ae2cs.common.block.entity;

import io.github.lounode.ae2cs.api.cap.ProvideCaps;
import io.github.lounode.ae2cs.api.submenu.CustomReturnableSubMenuHost;
import io.github.lounode.ae2cs.common.init.AECSBlockEntities;
import io.github.lounode.ae2cs.common.init.AECSBlockProperties;
import io.github.lounode.ae2cs.common.init.AECSBlocks;
import io.github.lounode.ae2cs.common.init.AECSItems;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;
import io.github.lounode.ae2cs.common.machine.component.AppEngInvComponent;
import io.github.lounode.ae2cs.common.machine.component.InvPort;
import io.github.lounode.ae2cs.common.machine.component.RecipeLockComponent;
import io.github.lounode.ae2cs.common.machine.component.SideConfigComponent;
import io.github.lounode.ae2cs.common.recipe.circuit_etcher.CircuitEtcherRecipe;
import io.github.lounode.ae2cs.common.recipe.input.ThreeItemStackRecipeInput;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.inventories.InternalInventory;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@ProvideCaps(IItemHandler.class)
public class CircuitEtcherBlockEntity extends AENetworkedSelfPoweredBlockEntity implements IUpgradeableObject,
                                      CustomReturnableSubMenuHost {

    /**
     * 基础能量消耗，每tick 200AE，每多一个加速卡，则此数值翻倍，同时机器运行速率也翻倍。
     * <p>
     * 目前最大四张加速卡，则最大速率为16倍，同时最大每tick消耗也为16倍，即3200AE每tick
     */
    private static final double BASIC_ENERGY_COST_PER_TICK = 200;

    /**
     * 升级仓
     */
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AECSBlocks.CIRCUIT_ETCHER_BLOCK,
            4, this::onUpgradesChanged);

    /**
     * 配方锁定组件：记录并（可选择）锁定本机器执行过的配方
     */
    private final RecipeLockComponent recipeLock = new RecipeLockComponent();

    /**
     * 当前执行的配方
     */
    private int speedMultiplier = 1;
    private int overclockCards = 0;

    @Nullable
    private RecipeHolder<CircuitEtcherRecipe> activeRecipe;

    /**
     * 当前执行配方的id，在重新加载时保证机器运行进展不会因为配方检查被刷新掉
     */
    @Nullable
    private ResourceLocation activeRecipeId;

    /**
     * activeRecipe 对应的槽位映射（required[i] 使用哪个输入槽 0/1/2）
     */
    private int @Nullable [] activeMatch;

    /**
     * 该配方需要的总能量
     */
    private int activeRecipeEnergyCost = 0;

    /**
     * 当前配方进度
     */
    private int recipeProgress = 0;

    /**
     * 是否需要更新配方状态
     */
    private boolean needRefreshRecipeState = true;

    public CircuitEtcherBlockEntity(BlockPos pos, BlockState blockState) {
        super(AECSBlockEntities.CIRCUIT_ETCHER_BLOCK_ENTITY.get(), pos, blockState,
                80000, false, AccessRestriction.WRITE);

        getMainNode().setIdlePowerUsage(0);

        AppEngInternalInventory inputInv = new AppEngInternalInventory(3) {

            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                needRefreshRecipeState = true;
                setChanged();
            }
        };
        // 配方锁定后，只有锁定配方所需的物品可以进入输入仓
        inputInv.setFilter(new IAEItemFilter() {

            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return isInputAllowedByRecipeLock(inv, stack);
            }
        });
        AppEngInternalInventory outputInv = new AppEngInternalInventory(1) {

            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                setChanged();
            }
        };

        AppEngInvComponent invComponent = new AppEngInvComponent();
        invComponent.addPort(InvPort.INPUT, inputInv);
        invComponent.addPort(InvPort.WORK, inputInv);
        invComponent.addPort(InvPort.OUTPUT, outputInv);
        getMachineComponents().add(invComponent);
        getMachineComponents().add(new SideConfigComponent());
        getMachineComponents().add(recipeLock);
    }

    public RecipeLockComponent getRecipeLock() {
        return this.recipeLock;
    }

    public boolean isRecipeLockEnabled() {
        return this.recipeLock.isEnabled();
    }

    public void setRecipeLockEnabled(boolean enabled) {
        if (this.recipeLock.isEnabled() == enabled) {
            return;
        }

        this.recipeLock.setEnabled(enabled);
        // 开关改变后立刻重新评估配方：可能停下当前配方，或切换到已锁定的配方
        this.needRefreshRecipeState = true;
        setChanged();
    }

    /**
     * 查询当前锁定的配方（锁定配方不存在时返回 null）
     */
    @Nullable
    private CircuitEtcherRecipe getLockedRecipe() {
        var level = getLevel();
        var lockedId = this.recipeLock.getLockedRecipeId();
        if (level == null || lockedId == null) {
            return null;
        }

        var holder = level.getRecipeManager().byKey(lockedId);
        if (holder.isPresent() && holder.get().value() instanceof CircuitEtcherRecipe recipe) {
            return recipe;
        }
        return null;
    }

    /**
     * 输入过滤：未锁定（或还未记录配方、锁定配方已失效）时不限制；
     * 锁定后只放行锁定配方所需的物品，且单个物品的存量不超过一组
     * （一组的数量取物品自身的堆叠上限，因此 64/16/4/1 叠物品都能正确工作；
     * 若配方本身对同一物品的需求超过一组，则以配方需求为上限，避免原料永远凑不齐）。
     */
    private boolean isInputAllowedByRecipeLock(InternalInventory inv, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (!this.recipeLock.hasLockedRecipe()) {
            return true;
        }

        CircuitEtcherRecipe lockedRecipe = getLockedRecipe();
        if (lockedRecipe == null) {
            return true;
        }

        // 该物品是否属于锁定配方的原料（ingredient().test 天然兼容标签与组件匹配）
        boolean matched = false;
        int required = 0;
        for (SizedIngredient ingredient : lockedRecipe.required()) {
            if (ingredient.ingredient().test(stack)) {
                matched = true;
                required += ingredient.count();
            }
        }
        if (!matched) {
            return false;
        }

        int cap = Math.max(stack.getMaxStackSize(), required);
        int existing = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack inSlot = inv.getStackInSlot(i);
            if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, stack)) {
                existing += inSlot.getCount();
            }
        }

        return existing + stack.getCount() <= cap;
    }

    public AppEngInternalInventory getInputInv() {
        return getMachineComponents().getService(AppEngInvComponent.class).port(InvPort.INPUT);
    }

    public AppEngInternalInventory getOutputInv() {
        return getMachineComponents().getService(AppEngInvComponent.class).port(InvPort.OUTPUT);
    }

    public int getRecipeProgress() {
        return recipeProgress;
    }

    public int getActiveRecipeEnergyCost() {
        return activeRecipeEnergyCost;
    }

    public void checkActive(boolean active) {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.hasProperty(AECSBlockProperties.ACTIVE) && state.getValue(AECSBlockProperties.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(AECSBlockProperties.ACTIVE, active), 2);
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private void onUpgradesChanged() {
        this.overclockCards = Math.min(2, upgrades.getInstalledUpgrades(AECSItems.OVERLOAD_CARD));
        this.speedMultiplier = overclockCards > 0 ? 1 : 1 << Math.min(4, upgrades.getInstalledUpgrades(AEItems.SPEED_CARD));
        saveChanges();
    }

    @Override
    public void serverTick() {
        super.serverTick();

        if (getLevel() == null || getLevel().isClientSide()) return;

        checkActive(getAECurrentPower() > 0);

        // 1) 更新/确认活动配方
        if (needRefreshRecipeState) {
            updateActiveRecipe();
            needRefreshRecipeState = false;
        }
        if (activeRecipe == null || activeMatch == null) {
            recipeProgress = 0;
            return;
        }

        Level level = getLevel();
        CircuitEtcherRecipe recipe = activeRecipe.value();

        // 2) 若未完成：推进进度 + 扣能量
        if (recipeProgress < activeRecipeEnergyCost) {
            if (getAECurrentPower() <= 0) return;

            double neededEnergy = getEnergyPerTick();
            neededEnergy = Math.min(neededEnergy, activeRecipeEnergyCost - recipeProgress);
            double actualCost = extractAEPower(neededEnergy, Actionable.MODULATE);
            recipeProgress = Math.min(recipeProgress + (int) actualCost, activeRecipeEnergyCost);
            setChanged();
        }

        // 3) 已经完成：消耗资源并产出
        if (recipeProgress >= activeRecipeEnergyCost) {
            ThreeItemStackRecipeInput input = ThreeItemStackRecipeInput.of(
                    getInputInv().getStackInSlot(0),
                    getInputInv().getStackInSlot(1),
                    getInputInv().getStackInSlot(2));
            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (result.isEmpty()) // 如果我们拿不到输出，说明配方可能有问题，此时清空状态
            {
                recipeProgress = 0;
                activeRecipe = null;
                activeMatch = null;
                activeRecipeEnergyCost = 0;
                return;
            }

            // 如果输出放不下，则将recipeProgress钳制在最大配方时间
            if (!getOutputInv().insertItem(0, result, true).isEmpty()) {
                recipeProgress = activeRecipeEnergyCost;
                return;
            }

            if (!consumeInputs(recipe, activeMatch)) {
                // 输入不够：清缓存和状态，等待刷新
                recipeProgress = 0;
                activeRecipe = null;
                activeMatch = null;
                activeRecipeEnergyCost = 0;
                return;
            }

            getOutputInv().insertItem(0, result, false);
            recipeProgress = 0;
            setChanged();
        }
    }

    // 计算能量消耗
    private double getEnergyPerTick() {
        double normalEnergy = BASIC_ENERGY_COST_PER_TICK * speedMultiplier;
        if (overclockCards == 0 || activeRecipeEnergyCost <= 0) {
            return normalEnergy;
        }

        int targetTicks = overclockCards == 1 ? 4 : 1;
        return Math.max(normalEnergy, Math.ceil((double) activeRecipeEnergyCost / targetTicks));
    }

    /**
     * 更新配方状态
     */
    private void updateActiveRecipe() {
        if (getLevel() == null || getLevel().isClientSide()) return;

        var level = getLevel();
        var input = ThreeItemStackRecipeInput.of(
                getInputInv().getStackInSlot(0),
                getInputInv().getStackInSlot(1),
                getInputInv().getStackInSlot(2));

        RecipeHolder<CircuitEtcherRecipe> holder = null;
        int[] match = null;

        if (this.recipeLock.hasLockedRecipe()) {
            // 已锁定配方：只允许执行已记录的那个配方，即使存在其它可执行配方也不切换
            var lockedId = this.recipeLock.getLockedRecipeId();
            var locked = level.getRecipeManager().byKey(lockedId);
            if (locked.isPresent() && locked.get().value() instanceof CircuitEtcherRecipe lockedRecipe) {
                match = lockedRecipe.findMatch(input);
                if (match != null) {
                    @SuppressWarnings("unchecked")
                    var lockedHolder = (RecipeHolder<CircuitEtcherRecipe>) locked.get();
                    holder = lockedHolder;
                }
            }
        } else {
            var opt = level.getRecipeManager().getRecipeFor(
                    AECSRecipeTypes.CIRCUIT_ETCHER.get(),
                    input,
                    level);
            if (opt.isPresent()) {
                holder = opt.get();
                match = holder.value().findMatch(input);
            }
        }

        // 没有任何匹配配方：清空状态
        if (holder == null || match == null) {
            activeRecipe = null;
            activeMatch = null;
            activeRecipeEnergyCost = 0;
            recipeProgress = 0;
            return;
        }

        var recipe = holder.value();

        // 记录本次执行的配方（未开启锁定时也会记录；连续执行同一配方不会重复写盘）
        this.recipeLock.recordExecutedRecipe(holder.id());

        // 配方未变：保持进度，仅刷新 match/time
        if (activeRecipe != null && activeRecipe.id().equals(holder.id())) {
            activeMatch = match;
            activeRecipeEnergyCost = recipe.energyCost();
            return;
        }

        // 配方变了：切换配方，重置进度
        activeRecipe = holder;
        activeMatch = match;
        activeRecipeEnergyCost = recipe.energyCost();
        recipeProgress = 0;
    }

    /**
     * 尝试从输入槽中按照match提供的索引表来抽取当前配方所需资源，如果都能成功则返回true
     */
    private boolean consumeInputs(CircuitEtcherRecipe recipe, int[] match) {
        List<SizedIngredient> required = recipe.required();
        // 先进行模拟抽取
        for (int i = 0; i < required.size(); i++) {
            int slot = match[i];
            int amount = required.get(i).count();

            ItemStack extracted = getInputInv().extractItem(slot, amount, true);
            if (extracted.isEmpty() || extracted.getCount() < amount) return false;
        }

        // 执行扣除
        for (int i = 0; i < required.size(); i++) {
            int slot = match[i];
            int amount = required.get(i).count();

            getInputInv().extractItem(slot, amount, false);
        }
        return true;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        upgrades.writeToNBT(data, "upgrades", registries);
        data.putInt("recipe_progress", recipeProgress);
        if (activeRecipe != null) {
            data.putString("active_recipe_id", activeRecipe.id().toString());
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        upgrades.readFromNBT(data, "upgrades", registries);
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
            Optional<RecipeHolder<?>> opt = level.getRecipeManager().byKey(activeRecipeId);
            opt.ifPresent(recipeHolder -> activeRecipe = (RecipeHolder<CircuitEtcherRecipe>) recipeHolder);
        }
        updateActiveRecipe();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (ItemStack stack : upgrades) {
            drops.add(stack);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getItemFromBlockEntity());
    }
}
