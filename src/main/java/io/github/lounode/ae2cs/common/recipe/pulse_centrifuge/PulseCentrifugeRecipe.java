package io.github.lounode.ae2cs.common.recipe.pulse_centrifuge;

import io.github.lounode.ae2cs.api.settings.PulseCentrifugeMode;
import io.github.lounode.ae2cs.common.init.AECSRecipeSerializers;
import io.github.lounode.ae2cs.common.init.AECSRecipeTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PulseCentrifugeRecipe implements Recipe<SingleRecipeInput> {

    private final SizedIngredient input;
    private final List<PulseCentrifugeOutput> results;
    private final @Nullable SizedFluidIngredient fluidInput;
    private final FluidStack fluidOutput;
    private final PulseCentrifugeMode mode;
    private final int energyCost;

    public PulseCentrifugeRecipe(SizedIngredient input, List<PulseCentrifugeOutput> results, int energyCost) {
        this(input, results, null, FluidStack.EMPTY, PulseCentrifugeMode.CENTRIFUGE, energyCost);
    }

    public PulseCentrifugeRecipe(SizedIngredient input, List<PulseCentrifugeOutput> results,
                                 @Nullable SizedFluidIngredient fluidInput, FluidStack fluidOutput,
                                 PulseCentrifugeMode mode, int energyCost) {
        if (input.ingredient().isEmpty() || input.count() <= 0) {
            throw new IllegalArgumentException("Pulse centrifuge input cannot be empty");
        }
        if (results.isEmpty() || results.size() > PulseCentrifugeProcess.MAX_RESULTS || results.stream().anyMatch(result -> result.stack().isEmpty())) {
            throw new IllegalArgumentException("Pulse centrifuge recipes require 1-4 non-empty results");
        }
        if (energyCost <= 0) {
            throw new IllegalArgumentException("Energy cost must be positive");
        }

        this.input = input;
        this.results = List.copyOf(results);
        this.fluidInput = fluidInput;
        this.fluidOutput = fluidOutput == null ? FluidStack.EMPTY : fluidOutput.copy();
        this.mode = mode == null ? PulseCentrifugeMode.CENTRIFUGE : mode;
        this.energyCost = energyCost;
    }

    public SizedIngredient input() {
        return input;
    }

    public List<PulseCentrifugeOutput> results() {
        return results;
    }

    /**
     * 配方所属的机器模式，未在数据包中填写时默认为离心
     */
    public PulseCentrifugeMode mode() {
        return mode;
    }

    /**
     * 配方要求的输入流体，可以为空
     */
    public @Nullable SizedFluidIngredient fluidInput() {
        return fluidInput;
    }

    public FluidStack fluidOutput() {
        return fluidOutput.copy();
    }

    public int energyCost() {
        return energyCost;
    }

    @Override
    public boolean matches(@NotNull SingleRecipeInput input, @NotNull Level level) {
        return this.input.test(input.item());
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull SingleRecipeInput input,
                                       HolderLookup.@NotNull Provider registries) {
        return results.getFirst().stack().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return results.getFirst().stack().copy();
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return AECSRecipeSerializers.PULSE_CENTRIFUGE.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return AECSRecipeTypes.PULSE_CENTRIFUGE.get();
    }
}
