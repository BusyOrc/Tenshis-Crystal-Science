package io.github.lounode.ae2cs.common.recipe.pulse_centrifuge;

import io.github.lounode.ae2cs.api.settings.PulseCentrifugeMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class PulseCentrifugeRecipeSerializer implements RecipeSerializer<PulseCentrifugeRecipe> {

    private static final Codec<List<PulseCentrifugeOutput>> RESULTS_CODEC = PulseCentrifugeOutput.CODEC.listOf().validate(results -> {
        if (results.isEmpty() || results.size() > PulseCentrifugeProcess.MAX_RESULTS) {
            return DataResult.error(() -> "Pulse centrifuge recipes require 1-4 results");
        }
        return DataResult.success(results);
    });

    private static final StreamCodec<RegistryFriendlyByteBuf, List<PulseCentrifugeOutput>> RESULTS_STREAM_CODEC = PulseCentrifugeOutput.STREAM_CODEC
            .apply(ByteBufCodecs.list(PulseCentrifugeProcess.MAX_RESULTS));

    public static final MapCodec<PulseCentrifugeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SizedIngredient.FLAT_CODEC.fieldOf("input").forGetter(PulseCentrifugeRecipe::input),
            RESULTS_CODEC.fieldOf("results").forGetter(PulseCentrifugeRecipe::results),
            SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("fluid_input").forGetter(recipe -> Optional.ofNullable(recipe.fluidInput())),
            FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid_output").forGetter(recipe -> recipe.fluidOutput().isEmpty() ? Optional.empty() : Optional.of(recipe.fluidOutput())),
            PulseCentrifugeMode.CODEC.optionalFieldOf("mode", PulseCentrifugeMode.CENTRIFUGE).forGetter(PulseCentrifugeRecipe::mode),
            Codec.INT.optionalFieldOf("energy_cost", 200).forGetter(PulseCentrifugeRecipe::energyCost))
            .apply(instance, (input, results, fluidInput, fluidOutput, mode, energyCost) -> new PulseCentrifugeRecipe(
                    input, results, fluidInput.orElse(null), fluidOutput.orElse(FluidStack.EMPTY), mode, energyCost)));

    public static final StreamCodec<RegistryFriendlyByteBuf, PulseCentrifugeRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC, PulseCentrifugeRecipe::input,
            RESULTS_STREAM_CODEC, PulseCentrifugeRecipe::results,
            ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC), recipe -> Optional.ofNullable(recipe.fluidInput()),
            FluidStack.OPTIONAL_STREAM_CODEC, PulseCentrifugeRecipe::fluidOutput,
            PulseCentrifugeMode.STREAM_CODEC, PulseCentrifugeRecipe::mode,
            ByteBufCodecs.VAR_INT, PulseCentrifugeRecipe::energyCost,
            (input, results, fluidInput, fluidOutput, mode, energyCost) -> new PulseCentrifugeRecipe(
                    input, results, fluidInput.orElse(null), fluidOutput, mode, energyCost));

    @Override
    public @NotNull MapCodec<PulseCentrifugeRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, PulseCentrifugeRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
