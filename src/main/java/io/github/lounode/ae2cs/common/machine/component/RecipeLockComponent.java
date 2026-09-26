package io.github.lounode.ae2cs.common.machine.component;

import io.github.lounode.ae2cs.api.networking.RecipeLockField;
import io.github.lounode.ae2cs.common.init.AECSDataComponents;
import io.github.lounode.ae2cs.common.machine.MachineComponentContainer;
import io.github.lounode.ae2cs.common.machine.MachineContext;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * 配方锁定：
 * <ul>
 * <li>每台机器单独保存"最近一次执行的配方"；</li>
 * <li>开启锁定后，不属于该配方的输入（物品/流体）无法进入机器，且机器只会执行该配方；</li>
 * <li>尚未记录过配方时，下一次执行配方即记录并锁定它；</li>
 * <li>未开启锁定时同样记录配方，但连续执行同一配方不会重复写盘。</li>
 * </ul>
 * 具体"这个配方需要哪些原料"由各机器自行解释，本组件只负责状态与记录。
 */
public class RecipeLockComponent extends BaseMachineComponent {

    private static final String TAG_ENABLED = "recipe_lock_enabled";
    private static final String TAG_LOCKED_RECIPE = "recipe_lock_recipe_id";

    private boolean enabled = false;

    @Nullable
    private ResourceLocation lockedRecipeId = null;

    @Override
    public void onConstruct(MachineComponentContainer container) {
        super.onConstruct(container);
        container.exposeService(RecipeLockComponent.class, this);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        markChanged();
    }

    public @Nullable ResourceLocation getLockedRecipeId() {
        return lockedRecipeId;
    }

    /**
     * 是否处于"已锁定某个具体配方"的状态：开启锁定且已经记录过配方。
     * 尚未记录配方时（例如刚开启锁定或从未执行过配方）不做任何限制，等待第一次执行时记录。
     */
    public boolean hasLockedRecipe() {
        return enabled && lockedRecipeId != null;
    }

    /**
     * 判断某个配方是否允许执行：未锁定则任意配方都可执行，锁定后只允许已记录的配方。
     */
    public boolean isRecipeAllowed(@Nullable ResourceLocation recipeId) {
        if (!hasLockedRecipe()) {
            return true;
        }
        return lockedRecipeId.equals(recipeId);
    }

    /**
     * 记录本次执行的配方；仅当与已记录的不同时才写盘（连续执行同一配方不重复记录）。
     */
    public void recordExecutedRecipe(@Nullable ResourceLocation recipeId) {
        if (recipeId == null || recipeId.equals(lockedRecipeId)) {
            return;
        }

        this.lockedRecipeId = recipeId;
        markChanged();
    }

    @Override
    public void writeNbt(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_ENABLED, this.enabled);
        if (this.lockedRecipeId != null) {
            tag.putString(TAG_LOCKED_RECIPE, this.lockedRecipeId.toString());
        }
    }

    @Override
    public void readNbt(CompoundTag tag, HolderLookup.Provider registries) {
        this.enabled = tag.getBoolean(TAG_ENABLED);
        if (tag.contains(TAG_LOCKED_RECIPE)) {
            this.lockedRecipeId = ResourceLocation.tryParse(tag.getString(TAG_LOCKED_RECIPE));
        }
    }

    @Override
    public void importSettings(MachineContext ctx, DataComponentMap input, @Nullable Player player) {
        super.importSettings(ctx, input, player);

        RecipeLockField field = input.get(AECSDataComponents.RECIPE_LOCK_FOR_MEMORY_CARD.get());
        if (field == null) {
            return;
        }

        this.enabled = field.enabled();
        this.lockedRecipeId = field.recipeId();
        ctx.host().markChanged();
    }

    @Override
    public void exportSettings(MachineContext ctx, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(ctx, builder, player);
        builder.set(AECSDataComponents.RECIPE_LOCK_FOR_MEMORY_CARD.get(), new RecipeLockField(this.enabled, this.lockedRecipeId));
    }
}
