package es.jonaykb.kb_dynamic_performance.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bug de vanilla 1.20.1: al reaparecer (muerte o salida del End) se crea un ServerPlayer nuevo y
 * restoreFrom no copia allowsListing, que solo se rellena con el paquete de opciones del cliente
 * (al entrar o al cambiar ajustes). Hasta que vuelve a entrar, el jugador sale como
 * "Anonymous Player" en la lista del ping del servidor.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerListingMixin {

    @Shadow
    private boolean allowsListing;

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void kb_dynamic_performance$keepAllowsListing(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        this.allowsListing = oldPlayer.allowsListing();
    }
}
