package io.github.lounode.ae2cs.common.recipe.pulse_centrifuge;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * 离心机配方的一条产物，支持概率。
 * <p>
 * 兼容两种 JSON 写法：
 * <ul>
 * <li>旧写法（等价于 100% 概率）：<code>{"id": "minecraft:stone", "count": 2}</code></li>
 * <li>新写法：<code>{"id": "minecraft:stone", "count": 2, "chance": 0.5}</code></li>
 * </ul>
 * 概率按 count 逐个判定，与机械动力鼓风机配方的语义一致：例如 count=2、chance=0.5 时可能roll出 0/1/2 个。
 */
public record PulseCentrifugeOutput(ItemStack stack, float chance) {

    /** 新写法：扁平结构 + 可选 chance */
    private static final Codec<PulseCentrifugeOutput> CHANCED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(output -> output.stack().getItem()),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(output -> output.stack().getCount()),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(output -> output.stack().getComponentsPatch()),
            ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("chance", 1F).forGetter(PulseCentrifugeOutput::chance))
            .apply(instance, (item, count, patch, chance) -> {
                ItemStack stack = new ItemStack(item, count);
                if (!patch.isEmpty()) {
                    stack.applyComponents(patch);
                }
                return new PulseCentrifugeOutput(stack, chance);
            }));

    public static final Codec<PulseCentrifugeOutput> CODEC = Codec.withAlternative(
            CHANCED_CODEC,
            ItemStack.CODEC.xmap(stack -> new PulseCentrifugeOutput(stack, 1F), PulseCentrifugeOutput::stack));

    public static final StreamCodec<RegistryFriendlyByteBuf, PulseCentrifugeOutput> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, PulseCentrifugeOutput::stack,
            ByteBufCodecs.FLOAT, PulseCentrifugeOutput::chance,
            PulseCentrifugeOutput::new);

    public PulseCentrifugeOutput(ItemStack stack, float chance) {
        this.stack = stack.copy();
        this.chance = Math.clamp(chance, 0F, 1F);
    }

    /**
     * 对外始终返回副本，避免调用方改动配方内容
     */
    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public static PulseCentrifugeOutput of(@NotNull ItemStack stack) {
        return new PulseCentrifugeOutput(stack, 1F);
    }

    public static PulseCentrifugeOutput of(@NotNull ItemStack stack, float chance) {
        return new PulseCentrifugeOutput(stack, chance);
    }

    /**
     * 必得产物（100%）与不写 chance 的旧格式都走这里
     */
    public boolean isGuaranteed() {
        return chance >= 1F;
    }

    /**
     * 按概率 roll 一次产出：与机械动力一致，对 count 逐个判定
     */
    public ItemStack roll(RandomSource random) {
        ItemStack result = stack.copy();
        if (isGuaranteed()) {
            return result;
        }

        int count = 0;
        for (int roll = 0; roll < stack.getCount(); roll++) {
            if (random.nextFloat() < chance) {
                count++;
            }
        }
        if (count <= 0) {
            return ItemStack.EMPTY;
        }

        result.setCount(count);
        return result;
    }
}
