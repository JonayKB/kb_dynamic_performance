package es.jonaykb.kb_dynamic_performance.mixin;

import es.jonaykb.kb_dynamic_performance.PerformanceManager;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mob cap local por jugador (LocalMobCapCalculator.MobCounts es package-private,
 * por eso se apunta por nombre).
 */
@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public abstract class MobCountsMixin {

    @Redirect(
            method = "canSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I")
    )
    private int kb_dynamic_performance$scaleLocalMobCap(MobCategory category) {
        return PerformanceManager.scaleMobCap(category.getMaxInstancesPerChunk());
    }
}
