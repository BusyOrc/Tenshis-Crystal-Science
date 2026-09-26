package io.github.lounode.ae2cs.common.recipe.pulse_centrifuge;

import io.github.lounode.ae2cs.api.settings.PulseCentrifugeMode;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 离心机执行一次加工所需的全部信息。
 * <p>
 * 既可以是本模组自己的离心配方，也可以是从机械动力鼓风机（缠魂 / 洗涤）读取来的兼容配方，
 * 让机器的主循环不必关心配方来源。
 *
 * @param requiresWater 是否强制要求输入罐中有 1B 水（机械动力的洗涤兼容配方为 true）
 */
public record PulseCentrifugeProcess(ResourceLocation id,
                                     PulseCentrifugeMode mode,
                                     SizedIngredient input,
                                     List<PulseCentrifugeOutput> results,
                                     @Nullable SizedFluidIngredient fluidInput,
                                     FluidStack fluidOutput,
                                     int energyCost,
                                     boolean requiresWater) {

    /** 输出槽只有 4 个，超过的产物会被丢弃 */
    public static final int MAX_RESULTS = 4;

    public PulseCentrifugeProcess {
        results = List.copyOf(results);
    }

    /**
     * 把超过 4 个的产物截断，只保留前 4 个
     */
    public static List<PulseCentrifugeOutput> limitResults(List<PulseCentrifugeOutput> results) {
        if (results.size() <= MAX_RESULTS) {
            return List.copyOf(results);
        }
        return List.copyOf(results.subList(0, MAX_RESULTS));
    }

    /**
     * 按概率 roll 出本次加工的产物（概率产物可能返回空列表）
     */
    public List<ItemStack> rollResults(RandomSource random) {
        List<ItemStack> rolled = new ArrayList<>(results.size());
        for (PulseCentrifugeOutput result : results) {
            ItemStack stack = result.roll(random);
            if (!stack.isEmpty()) {
                rolled.add(stack);
            }
        }
        return rolled;
    }
}
