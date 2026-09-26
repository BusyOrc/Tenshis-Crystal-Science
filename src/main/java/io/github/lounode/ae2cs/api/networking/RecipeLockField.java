package io.github.lounode.ae2cs.api.networking;

import appeng.menu.guisync.PacketWritable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 给内存卡记录机器的配方锁定状态与已锁定的配方
 */
public record RecipeLockField(boolean enabled, @Nullable ResourceLocation recipeId) implements PacketWritable {

    public static final Codec<RecipeLockField> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(RecipeLockField::enabled),
            ResourceLocation.CODEC.optionalFieldOf("recipe_id").forGetter(field -> Optional.ofNullable(field.recipeId())))
            .apply(instance, (enabled, recipeId) -> new RecipeLockField(enabled, recipeId.orElse(null))));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeLockField> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public RecipeLockField(RegistryFriendlyByteBuf buf) {
        this(decode(buf));
    }

    /**
     * 用于转发委托（同时为上面的网络构造提供重载消歧义）
     */
    private RecipeLockField(RecipeLockField field) {
        this(field.enabled, field.recipeId);
    }

    private static RecipeLockField decode(RegistryFriendlyByteBuf buf) {
        return STREAM_CODEC.decode(buf);
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buf) {
        STREAM_CODEC.encode(buf, this);
    }
}
