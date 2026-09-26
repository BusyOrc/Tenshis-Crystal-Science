package io.github.lounode.ae2cs.integration;

import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * 配方查看器（JEI / EMI）展示流体时用的小工具。
 * <p>
 * JEI 的流体原料列表只收录「源流体」——{@code FluidStackListFactory} 会用
 * {@code fluid.isSource(fluid.defaultFluidState())} 过滤，所以像 {@code minecraft:flowing_water}
 * 这样的流动变体并不在 JEI 的原料表里，直接塞进配方槽位就显示不出来
 * （AE2 自带的 {@code ae2:entropy/cool/flowing_water_snowball}、{@code ae2:entropy/heat/snow_water}
 * 就是这种写法）。
 * <p>
 * 因此展示前统一把流动流体换算成它对应的源流体：视觉上本来就是同一种流体，换算后 JEI / EMI 都能正常渲染。
 */
public final class RecipeViewerFluids {

    private RecipeViewerFluids() {}

    /**
     * 把流动流体换成对应的源流体，其余情况原样返回
     */
    public static Fluid getDisplayFluid(Fluid fluid) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return Fluids.EMPTY;
        }
        if (fluid instanceof FlowingFluid flowingFluid) {
            Fluid source = flowingFluid.getSource();
            if (source != null && source != Fluids.EMPTY) {
                return source;
            }
        }
        return fluid;
    }
}
