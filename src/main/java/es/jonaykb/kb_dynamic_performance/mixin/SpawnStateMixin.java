package es.jonaykb.kb_dynamic_performance.mixin;

import es.jonaykb.kb_dynamic_performance.PerformanceManager;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mob cap global: canSpawnForCategory calcula
 * maxInstancesPerChunk * chunksSpawneables / 289 -> escalamos maxInstancesPerChunk.
 */
@Mixin(NaturalSpawner.SpawnState.class)
public abstract class SpawnStateMixin {

    @Redirect(
            method = "canSpawnForCategory",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I")
    )
    private int kb_dynamic_performance$scaleGlobalMobCap(MobCategory category) {
        return PerformanceManager.scaleMobCap(category.getMaxInstancesPerChunk());
    }
}
