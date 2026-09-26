package io.github.lounode.ae2cs.client.gui.widgets;

import io.github.lounode.ae2cs.api.localization.AECSTexts;
import io.github.lounode.ae2cs.client.gui.icon.AECSIcon;
import io.github.lounode.ae2cs.common.machine.component.SidePolicy;
import io.github.lounode.ae2cs.common.menu.submenu.SideConfigMenu;

import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.blockentity.networking.CableBusBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

/**
 * 仅用于 {@link io.github.lounode.ae2cs.client.gui.subGUI.SideConfigGUI} 的面配置按钮
 */
public class AECSSideConfigToggleButton extends AECSBackgroundToggleButton<SidePolicy> {

    private final SideConfigMenu menu;
    private final Direction dir;
    private final @Nullable RelativeSide relativeSide;

    public AECSSideConfigToggleButton(@NotNull SideConfigMenu menu, @NotNull Direction dir, @Nullable RelativeSide relativeSide) {
        super(SidePolicy.class, SidePolicy.values()[0]);
        this.menu = menu;
        this.dir = dir;
        this.relativeSide = relativeSide;

        for (SidePolicy policy : SidePolicy.values()) {
            // 背景贴图映射（每个状态一套三态背景）
            mapBackground(policy, backgroundForPolicy(policy));

            // tooltip映射
            if (relativeSide != null) {
                mapTooltip(policy, tooltipFor(relativeSide, policy));
            } else {
                mapTooltip(policy, tooltipFor(dir, policy));
            }
        }

        // 点击后发给服务端
        setOnValueChanged(next -> menu.sendChangeSideDirPolicy(new SideConfigMenu.SideConfigChoice(dir, next)));
    }

    /**
     * 由GUI手动调用
     */
    public void syncFromMenu() {
        var field = menu.sidePolicies;
        if (field == null) return;

        EnumMap<Direction, SidePolicy> map = field.sidePolicies();
        if (map == null) return;

        SidePolicy policy = map.get(dir);
        if (policy != null) {
            this.value = policy;
        }
    }

    @Override
    protected @Nullable Item getItemOverlay() {
        BlockEntity be = menu.getBlockEntity();
        if (be == null) return null;

        Level level = be.getLevel();
        if (level == null) return null;

        BlockPos pos = be.getBlockPos().relative(dir);

        // AE2 线缆总线：方块本体只是"总线"，真正摆在这个面上的其实是子部件（或伪装板），
        // 因此不能直接用它对应的物品做预览
        if (level.getBlockEntity(pos) instanceof CableBusBlockEntity cableBus) {
            Item partItem = getPartItemOnFace(cableBus);
            if (partItem != null) return partItem;
        }

        var state = level.getBlockState(pos);
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? null : item;
    }

    /**
     * 取相邻线缆总线上"正对本机器这一面"的子部件对应的物品。
     * <p>
     * 顺序与 AE2 自身的选取方块逻辑（{@code CableBusBlock#getCloneItemStack}）保持一致：先子部件，后伪装板。
     *
     * @return 该面上的子部件/伪装板物品，两者都没有时返回 null
     */
    private @Nullable Item getPartItemOnFace(CableBusBlockEntity cableBus) {
        // 从相邻方块指向本机器的方向，即为部件所依附的那一面
        Direction side = this.dir.getOpposite();
        var cableBusContainer = cableBus.getCableBus();

        IPart part = cableBusContainer.getPart(side);
        if (part != null) {
            IPartItem<?> partItem = part.getPartItem();
            if (partItem != null && partItem.asItem() != Items.AIR) {
                return partItem.asItem();
            }
        }

        var facade = cableBusContainer.getFacadeContainer().getFacade(side);
        if (facade != null) {
            ItemStack facadeStack = facade.getItemStack();
            if (!facadeStack.isEmpty()) {
                return facadeStack.getItem();
            }
        }

        return null;
    }

    /**
     * 根据 SidePolicy 返回一套背景贴图（normal/focus/hover）。
     */
    private static BackgroundSet backgroundForPolicy(SidePolicy policy) {
        return switch (policy) {
            case NONE -> new BackgroundSet(
                    AECSIcon.BUTTON_ORIGINAL_DARK,
                    AECSIcon.BUTTON_ORIGINAL_DARK,
                    AECSIcon.BUTTON_ORIGINAL_DARK_HOVER);
            case INSERT -> new BackgroundSet(
                    AECSIcon.BUTTON_RED_DARK,
                    AECSIcon.BUTTON_RED_DARK,
                    AECSIcon.BUTTON_RED_DARK_HOVER);
            case EXTRACT -> new BackgroundSet(
                    AECSIcon.BUTTON_BLUE_DARK,
                    AECSIcon.BUTTON_BLUE_DARK,
                    AECSIcon.BUTTON_BLUE_DARK_HOVER);
            case ALL -> new BackgroundSet(
                    AECSIcon.BUTTON_PURPLE_DARK,
                    AECSIcon.BUTTON_PURPLE_DARK,
                    AECSIcon.BUTTON_PURPLE_DARK_HOVER);
        };
    }

    private static Component tooltipFor(Direction dir, SidePolicy policy) {
        Component dirText = AECSTexts.directionName(dir);
        Component policyText = AECSTexts.sidePolicyName(policy);
        return Component.translatable("ae2cs.button.side_config.tooltip", dirText, policyText);
    }

    private static Component tooltipFor(RelativeSide side, SidePolicy policy) {
        Component sideText = AECSTexts.relativeSideName(side);
        Component policyText = AECSTexts.sidePolicyName(policy);
        return Component.translatable("ae2cs.button.side_config.tooltip", sideText, policyText);
    }
}
