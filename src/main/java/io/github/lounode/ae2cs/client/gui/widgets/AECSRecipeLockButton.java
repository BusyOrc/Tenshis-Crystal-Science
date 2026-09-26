package io.github.lounode.ae2cs.client.gui.widgets;

import io.github.lounode.ae2cs.client.gui.icon.AECSIcon;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 机器主界面左侧工具栏的"配方锁定"开关按钮。
 * <p>
 * 未锁定使用 {@link AECSIcon#RECIPE_LOCK_UNLOCKED}，锁定使用 {@link AECSIcon#RECIPE_LOCK_LOCKED}，
 * 按钮底层贴图沿用 {@link AECSIconButton} 的默认样式（与面配置按钮一致）。
 */
public class AECSRecipeLockButton extends AECSToggleButton {

    public AECSRecipeLockButton(Consumer<Boolean> onChanged) {
        super(AECSIcon.RECIPE_LOCK_LOCKED, AECSIcon.RECIPE_LOCK_UNLOCKED, onChanged::accept);

        Component tooltip = Component.translatable("ae2cs.menu.recipe_lock.button");
        setTooltipOn(List.of(tooltip));
        setTooltipOff(List.of(tooltip));
    }

    public void syncFromMenu(boolean locked) {
        setState(locked);
    }
}
