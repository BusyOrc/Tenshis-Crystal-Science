package io.github.lounode.ae2cs.integration.emi;

import io.github.lounode.ae2cs.AE2CrystalScience;
import io.github.lounode.ae2cs.client.gui.widgets.AdvancedProgressBar;
import io.github.lounode.ae2cs.common.init.AECSBlocks;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeOutput;
import io.github.lounode.ae2cs.common.recipe.pulse_centrifuge.PulseCentrifugeRecipe;
import io.github.lounode.ae2cs.integration.RecipeViewerFluids;

import appeng.client.gui.style.Blitter;
import appeng.menu.interfaces.IProgressProvider;

import net.minecraft.Util;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;

import java.util.List;

public class PulseCentrifugeRecipeCategory extends BasicEmiRecipe {

    public static final EmiRecipeCategory RECIPE_TYPE = new EmiRecipeCategory(
            AE2CrystalScience.makeId("pulse_centrifuge"), EmiStack.of(AECSBlocks.PULSE_CENTRIFUGE_BLOCK)) {

        @Override
        public Component getName() {
            return Component.translatable("ae2cs.integration.jei.recipe_category.pulse_centrifuge");
        }
    };

    private static final ResourceLocation BACKGROUND = AE2CrystalScience.makeId("textures/gui/recipe/pulse_centrifuge_emi.png");
    private static final ResourceLocation MENU_TEXTURE = AE2CrystalScience.makeId("textures/gui/pulse_centrifuge_menu.png");
    private static final Rect2i ENERGY_TOOLTIP_AREA = new Rect2i(128, 21, 6, 18);
    private static final int ANIMATION_DURATION_MS = 3_000;
    private static final int FLUID_DISPLAY_CAPACITY = 1_000;
    private static final int MODE_LABEL_X = 39;
    private static final int MODE_LABEL_Y = 48;
    private static final int[][] OUTPUT_POSITIONS = { { 91, 13 }, { 109, 13 }, { 91, 31 }, { 109, 31 } };

    private final PulseCentrifugeRecipe recipe;
    private final EmiStack fluidInput;
    private final EmiStack fluidOutput;
    private final AdvancedProgressBar workingProgressBar;
    private long animationStart = -1;

    public PulseCentrifugeRecipeCategory(RecipeHolder<PulseCentrifugeRecipe> holder) {
        super(RECIPE_TYPE, holder.id(), 162, 62);
        recipe = holder.value();
        inputs.add(EmiIngredient.of(recipe.input().ingredient(), recipe.input().count()));
        for (PulseCentrifugeOutput result : recipe.results()) {
            outputs.add(EmiStack.of(result.stack()));
        }

        SizedFluidIngredient requiredFluid = recipe.fluidInput();
        FluidStack[] requiredStacks = requiredFluid == null ? new FluidStack[0] : requiredFluid.getFluids();
        fluidInput = requiredStacks.length == 0 ? EmiStack.EMPTY
                : EmiStack.of(RecipeViewerFluids.getDisplayFluid(requiredStacks[0].getFluid()),
                        requiredStacks[0].getAmount());

        FluidStack recipeFluidOutput = recipe.fluidOutput();
        fluidOutput = recipeFluidOutput.isEmpty() ? EmiStack.EMPTY
                : EmiStack.of(RecipeViewerFluids.getDisplayFluid(recipeFluidOutput.getFluid()),
                        recipeFluidOutput.getAmount());

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
    public void addWidgets(WidgetHolder widgets) {
        widgets.addTexture(BACKGROUND, 0, 0, 162, 62, 0, 0, 162, 62, 162, 62);
        if (!fluidInput.isEmpty()) {
            widgets.addTank(fluidInput, 1, 1, 18, 60, FLUID_DISPLAY_CAPACITY).drawBack(false);
        }
        widgets.addSlot(inputs.getFirst(), 39, 22).drawBack(false);

        List<PulseCentrifugeOutput> results = recipe.results();
        for (int i = 0; i < outputs.size(); i++) {
            widgets.addSlot(outputs.get(i), OUTPUT_POSITIONS[i][0] - 1, OUTPUT_POSITIONS[i][1] - 1)
                    .recipeContext(this)
                    .drawBack(false);

            // 概率产物额外标注百分比
            PulseCentrifugeOutput result = results.get(i);
            if (!result.isGuaranteed()) {
                widgets.addText(Component.literal(Math.round(result.chance() * 100F) + "%"),
                        OUTPUT_POSITIONS[i][0] + 17, OUTPUT_POSITIONS[i][1] + 4, 0xFF808080, true);
            }
        }

        if (!fluidOutput.isEmpty()) {
            widgets.addTank(fluidOutput, 143, 1, 18, 60, FLUID_DISPLAY_CAPACITY).recipeContext(this).drawBack(false);
        }
        widgets.addTexture(MENU_TEXTURE, 128, 21, 6, 18, 176, 34, 6, 18, 256, 256);
        widgets.addDrawable(0, 0, 0, 0, workingProgressBar::renderWidget);

        // 额外标注机器模式：离心 / 缠魂 / 洗涤
        widgets.addText(recipe.mode().getDisplayName(), MODE_LABEL_X, MODE_LABEL_Y, 0xFF808080, false);

        widgets.addTooltipText(List.of(Component.translatable(
                "ae2cs.integration.jei.recipe_category.energy_cost.tooltip", recipe.energyCost())),
                ENERGY_TOOLTIP_AREA.getX(), ENERGY_TOOLTIP_AREA.getY(),
                ENERGY_TOOLTIP_AREA.getWidth(), ENERGY_TOOLTIP_AREA.getHeight());
    }

    private int getAnimationProgress() {
        long now = Util.getMillis();
        if (animationStart < 0) animationStart = now;
        return (int) ((now - animationStart) % ANIMATION_DURATION_MS);
    }
}
