package io.github.lounode.ae2cs.integration.jei;

import io.github.lounode.ae2cs.AE2CrystalScience;
import io.github.lounode.ae2cs.client.gui.widgets.AdvancedProgressBar;
import io.github.lounode.ae2cs.common.init.AECSBlocks;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeOutput;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeRecipe;
import io.github.lounode.ae2cs.integration.RecipeViewerFluids;

import appeng.client.gui.style.Blitter;
import appeng.menu.interfaces.IProgressProvider;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class PulseCentrifugeRecipeCategory implements IRecipeCategory<RecipeHolder<PulseCentrifugeRecipe>> {

    public static final RecipeType<RecipeHolder<PulseCentrifugeRecipe>> RECIPE_TYPE = RecipeType.createRecipeHolderType(AE2CrystalScience.makeId("pulse_centrifuge"));

    private static final ResourceLocation MENU_TEXTURE = AE2CrystalScience.makeId("textures/gui/pulse_centrifuge_menu.png");
    private static final Rect2i ENERGY_TOOLTIP_AREA = new Rect2i(128, 21, 6, 18);
    private static final int ANIMATION_DURATION_MS = 3_000;
    private static final int FLUID_DISPLAY_CAPACITY = 1_000;
    /** 模式文字显示位置（输入槽正下方） */
    private static final int MODE_LABEL_X = 39;
    private static final int MODE_LABEL_Y = 48;
    private static final int[][] OUTPUT_POSITIONS = { { 91, 13 }, { 109, 13 }, { 91, 31 }, { 109, 31 } };

    private final IDrawableStatic background;
    private final IDrawable icon;
    private final IDrawableStatic energyIndicator;
    private final AdvancedProgressBar workingProgressBar;
    private long animationStart = -1;

    public PulseCentrifugeRecipeCategory(IJeiHelpers helpers) {
        var guiHelper = helpers.getGuiHelper();
        background = guiHelper.drawableBuilder(
                AE2CrystalScience.makeId("textures/gui/recipe/pulse_centrifuge_emi.png"), 0, 0, 162, 62)
                .setTextureSize(162, 62)
                .build();
        icon = guiHelper.createDrawableItemLike(AECSBlocks.PULSE_CENTRIFUGE_BLOCK);
        energyIndicator = guiHelper.createDrawable(MENU_TEXTURE, 176, 34, 6, 18);
        IProgressProvider animation = new IProgressProvider() {

            @Override
            public int getCurrentProgress() {
                return getAnimationProgress();
            }

            @Override
            public int getMaxProgress() {
                return ANIMATION_DURATION_MS;
            }
        };
        workingProgressBar = new AdvancedProgressBar(animation,
                Blitter.texture(MENU_TEXTURE, 256, 256).src(198, 0, 22, 33),
                AdvancedProgressBar.FillMode.LEFT_TO_RIGHT);
        workingProgressBar.setX(66);
        workingProgressBar.setY(19);
    }

    @Override
    public @NotNull RecipeType<RecipeHolder<PulseCentrifugeRecipe>> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public int getWidth() {
        return background.getWidth();
    }

    @Override
    public int getHeight() {
        return background.getHeight();
    }

    @Override
    public void draw(@NotNull RecipeHolder<PulseCentrifugeRecipe> recipe, @NotNull IRecipeSlotsView slots,
                     @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        IRecipeCategory.super.draw(recipe, slots, graphics, mouseX, mouseY);
        background.draw(graphics);
        energyIndicator.draw(graphics, 128, 21);
        workingProgressBar.renderWidget(graphics, (int) mouseX, (int) mouseY, 0);

        // 额外标注该配方属于哪种机器模式：离心 / 缠魂 / 洗涤
        Component modeLabel = recipe.value().mode().getDisplayName();
        graphics.drawString(Minecraft.getInstance().font, modeLabel, MODE_LABEL_X, MODE_LABEL_Y, 0xFF808080, false);
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("ae2cs.integration.jei.recipe_category.pulse_centrifuge");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return icon;
    }

    @Override
    public void getTooltip(@NotNull ITooltipBuilder tooltip, @NotNull RecipeHolder<PulseCentrifugeRecipe> recipe,
                           @NotNull IRecipeSlotsView slots, double mouseX, double mouseY) {
        IRecipeCategory.super.getTooltip(tooltip, recipe, slots, mouseX, mouseY);
        if (ENERGY_TOOLTIP_AREA.contains((int) mouseX, (int) mouseY)) {
            tooltip.add(Component.translatable("ae2cs.integration.jei.recipe_category.energy_cost.tooltip",
                    recipe.value().energyCost()));
        }
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder,
                          @NotNull RecipeHolder<PulseCentrifugeRecipe> holder,
                          @NotNull IFocusGroup focuses) {
        PulseCentrifugeRecipe recipe = holder.value();

        // 输入流体只在配方自己声明时才显示（离心配方不再强制要求 1B 水）
        SizedFluidIngredient fluidInput = recipe.fluidInput();
        if (fluidInput != null) {
            var fluidSlot = builder.addInputSlot(1, 1).setFluidRenderer(FLUID_DISPLAY_CAPACITY, true, 18, 60);
            for (FluidStack stack : fluidInput.getFluids()) {
                Fluid displayFluid = RecipeViewerFluids.getDisplayFluid(stack.getFluid());
                if (displayFluid != Fluids.EMPTY) {
                    fluidSlot.addFluidStack(displayFluid, stack.getAmount());
                }
            }
        }

        builder.addInputSlot(39, 22).addItemStacks(Arrays.asList(recipe.input().getItems()));

        List<PulseCentrifugeOutput> results = recipe.results();
        for (int i = 0; i < results.size(); i++) {
            PulseCentrifugeOutput result = results.get(i);
            var outputSlot = builder.addOutputSlot(OUTPUT_POSITIONS[i][0], OUTPUT_POSITIONS[i][1])
                    .addItemStack(result.stack());
            if (!result.isGuaranteed()) {
                outputSlot.addRichTooltipCallback((slotView, richTooltip) -> richTooltip.add(Component.translatable(
                        "ae2cs.integration.jei.recipe_category.chance.tooltip", Math.round(result.chance() * 100F))));
            }
        }

        FluidStack fluidOutput = recipe.fluidOutput();
        if (!fluidOutput.isEmpty()) {
            Fluid displayFluid = RecipeViewerFluids.getDisplayFluid(fluidOutput.getFluid());
            if (displayFluid != Fluids.EMPTY) {
                builder.addOutputSlot(143, 1).setFluidRenderer(FLUID_DISPLAY_CAPACITY, true, 18, 60)
                        .addFluidStack(displayFluid, fluidOutput.getAmount());
            }
        }
    }

    private int getAnimationProgress() {
        long now = Util.getMillis();
        if (animationStart < 0) animationStart = now;
        return (int) ((now - animationStart) % ANIMATION_DURATION_MS);
    }
}
