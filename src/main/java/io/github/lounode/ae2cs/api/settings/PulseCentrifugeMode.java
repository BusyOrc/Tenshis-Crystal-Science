package io.github.lounode.ae2cs.api.settings;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 脉冲离心机的机器模式。
 * <p>
 * 除本模组自有的离心配方外，{@link #HAUNTING} 与 {@link #SPLASHING} 还会执行机械动力鼓风机的
 * 缠魂（haunting）与洗涤（splashing）配方。
 */
public enum PulseCentrifugeMode implements StringRepresentable {

    /** 离心：只执行 ae2cs 自己的离心配方 */
    CENTRIFUGE,
    /** 缠魂：执行机械动力的 haunting 配方 */
    HAUNTING,
    /** 洗涤：执行机械动力的 splashing 配方（额外强制消耗 1B 水） */
    SPLASHING;

    public static final Codec<PulseCentrifugeMode> CODEC = StringRepresentable.fromEnum(PulseCentrifugeMode::values);

    public static final StreamCodec<ByteBuf, PulseCentrifugeMode> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    @Override
    public @NotNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public @NotNull String getTranslationKey() {
        return "ae2cs.machine_settings.pulse_centrifuge_mode." + getSerializedName();
    }

    public @NotNull Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
