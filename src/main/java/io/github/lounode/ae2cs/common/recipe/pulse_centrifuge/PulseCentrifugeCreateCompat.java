package io.github.lounode.ae2cs.common.recipe.pulse_centrifuge;

import io.github.lounode.ae2cs.api.ids.AECSConstants;
import io.github.lounode.ae2cs.api.settings.PulseCentrifugeMode;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 机械动力鼓风机配方（缠魂 haunting / 洗涤 splashing）兼容层。
 * <p>
 * <b>注意：本类直接引用 Create 的类，因此只能在确认 Create 已加载后再调用</b>
 * （Create 在 mods.toml 中是可选依赖）。调用方请先用 {@link #isLoaded()} 判断。
 */
public final class PulseCentrifugeCreateCompat {

    /**
     * 鼓风机兼容配方固定 10 刻完成（基础速率下），可用加速卡 / 陨石超频卡继续加速
     */
    public static final int COMPAT_PROCESSING_TICKS = 10;

    /**
     * 机器每刻的基础能耗，与离心机本体一致
     */
    public static final int ENERGY_PER_TICK = 200;

    public static final int COMPAT_ENERGY_COST = COMPAT_PROCESSING_TICKS * ENERGY_PER_TICK;

    /**
     * 洗涤兼容配方强制消耗的水量
     */
    public static final int SPLASHING_WATER_COST = 1000;

    private PulseCentrifugeCreateCompat() {}

    /**
     * Create 是否已加载（不引用任何 Create 类，可安全随时调用）
     */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(AECSConstants.CREATE_ID);
    }

    /**
     * 按当前模式查找可执行的鼓风机配方
     */
    @Nullable
    public static PulseCentrifugeProcess findProcess(Level level, PulseCentrifugeMode mode, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }

        SingleRecipeInput recipeInput = new SingleRecipeInput(input);

        if (mode == PulseCentrifugeMode.HAUNTING) {
            RecipeType<HauntingRecipe> type = AllRecipeTypes.HAUNTING.getType();
            return level.getRecipeManager().getRecipeFor(type, recipeInput, level)
                    .map(holder -> toProcess(holder, PulseCentrifugeMode.HAUNTING))
                    .orElse(null);
        }

        if (mode == PulseCentrifugeMode.SPLASHING) {
            RecipeType<SplashingRecipe> type = AllRecipeTypes.SPLASHING.getType();
            return level.getRecipeManager().getRecipeFor(type, recipeInput, level)
                    .map(holder -> toProcess(holder, PulseCentrifugeMode.SPLASHING))
                    .orElse(null);
        }

        return null;
    }

    /**
     * 按配方 id 还原一个鼓风机兼容配方（用于机器重载后恢复进度）
     */
    @Nullable
    public static PulseCentrifugeProcess resolveById(Level level, ResourceLocation id) {
        RecipeHolder<?> holder = level.getRecipeManager().byKey(id).orElse(null);
        if (holder == null || !(holder.value() instanceof ProcessingRecipe<?, ?>)) {
            return null;
        }

        PulseCentrifugeMode mode = null;
        if (holder.value() instanceof HauntingRecipe) {
            mode = PulseCentrifugeMode.HAUNTING;
        } else if (holder.value() instanceof SplashingRecipe) {
            mode = PulseCentrifugeMode.SPLASHING;
        }
        if (mode == null) {
            return null;
        }

        return toProcess(holder, mode);
    }

    /**
     * 收集所有可被离心机执行的鼓风机配方，转换为 {@link PulseCentrifugeRecipe} 以便在 JEI / EMI 中展示
     * （这些配方只用于显示，不会注册进配方管理器）
     */
    public static List<RecipeHolder<PulseCentrifugeRecipe>> collectDisplayRecipes(Level level) {
        List<RecipeHolder<PulseCentrifugeRecipe>> displayRecipes = new ArrayList<>();

        RecipeType<HauntingRecipe> hauntingType = AllRecipeTypes.HAUNTING.getType();
        RecipeType<SplashingRecipe> splashingType = AllRecipeTypes.SPLASHING.getType();

        for (RecipeHolder<HauntingRecipe> holder : level.getRecipeManager().byType(hauntingType)) {
            addDisplayRecipe(displayRecipes, holder, PulseCentrifugeMode.HAUNTING);
        }
        for (RecipeHolder<SplashingRecipe> holder : level.getRecipeManager().byType(splashingType)) {
            addDisplayRecipe(displayRecipes, holder, PulseCentrifugeMode.SPLASHING);
        }

        return displayRecipes;
    }

    private static void addDisplayRecipe(List<RecipeHolder<PulseCentrifugeRecipe>> out, RecipeHolder<?> holder,
                                         PulseCentrifugeMode mode) {
        if (!(holder.value() instanceof ProcessingRecipe<?, ?> processingRecipe)) {
            return;
        }
        if (processingRecipe.getIngredients().isEmpty()) {
            return;
        }

        List<PulseCentrifugeOutput> results = new ArrayList<>(PulseCentrifugeProcess.MAX_RESULTS);
        for (ProcessingOutput output : processingRecipe.getRollableResults()) {
            if (results.size() >= PulseCentrifugeProcess.MAX_RESULTS) {
                break;
            }
            ItemStack stack = output.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            results.add(PulseCentrifugeOutput.of(stack, output.getChance()));
        }
        if (results.isEmpty()) {
            return;
        }

        // 洗涤模式实际还会强制消耗 1B 水，这里同步展示出来
        SizedFluidIngredient fluidInput = mode == PulseCentrifugeMode.SPLASHING
                ? SizedFluidIngredient.of(Fluids.WATER, SPLASHING_WATER_COST)
                : null;
        FluidStack fluidOutput = processingRecipe.getFluidResults().isEmpty() ? FluidStack.EMPTY
                : processingRecipe.getFluidResults().getFirst().copy();

        out.add(new RecipeHolder<>(holder.id(), new PulseCentrifugeRecipe(
                new SizedIngredient(processingRecipe.getIngredients().getFirst(), 1),
                results, fluidInput, fluidOutput, mode, COMPAT_ENERGY_COST)));
    }

    @Nullable
    private static PulseCentrifugeProcess toProcess(RecipeHolder<?> holder, PulseCentrifugeMode mode) {
        if (!(holder.value() instanceof ProcessingRecipe<?, ?> processingRecipe)) {
            return null;
        }

        // 输入：鼓风机配方固定只有 1 个物品输入
        List<Ingredient> ingredients = processingRecipe.getIngredients();
        if (ingredients.isEmpty()) {
            return null;
        }
        SizedIngredient input = new SizedIngredient(ingredients.getFirst(), 1);

        // 产物：超过 4 个的只保留前 4 个
        List<PulseCentrifugeOutput> results = new ArrayList<>(PulseCentrifugeProcess.MAX_RESULTS);
        for (ProcessingOutput output : processingRecipe.getRollableResults()) {
            if (results.size() >= PulseCentrifugeProcess.MAX_RESULTS) {
                break;
            }
            ItemStack stack = output.getStack();
            if (stack.isEmpty()) {
                continue;
            }
            results.add(PulseCentrifugeOutput.of(stack, output.getChance()));
        }
        if (results.isEmpty()) {
            return null;
        }

        // 流体：鼓风机配方通常没有，这里保持一致地读取以兼容附属模组
        SizedFluidIngredient fluidInput = processingRecipe.getFluidIngredients().isEmpty() ? null
                : processingRecipe.getFluidIngredients().getFirst();
        FluidStack fluidOutput = processingRecipe.getFluidResults().isEmpty() ? FluidStack.EMPTY
                : processingRecipe.getFluidResults().getFirst().copy();

        return new PulseCentrifugeProcess(holder.id(), mode, input, results, fluidInput, fluidOutput,
                COMPAT_ENERGY_COST, mode == PulseCentrifugeMode.SPLASHING);
    }
}
