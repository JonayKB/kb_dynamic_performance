package es.jonaykb.kb_dynamic_performance;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod(KbDynPerf.MODID)
public class KbDynPerf {

    public static final String MODID = "kb_dynamic_performance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("kb-dynamic-performance.json");
    }

    public KbDynPerf() {
        // Mod solo de servidor: los clientes pueden entrar sin tenerlo instalado
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        DynPerfConfig config;
        try {
            config = DynPerfConfig.loadOrCreate(configPath());
        } catch (Exception e) {
            LOGGER.error("[KB DynPerf] Config inválida, usando valores por defecto: {}", e.getMessage());
            config = DynPerfConfig.defaults();
        }
        PerformanceManager.init(config, event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PerformanceManager.onTick();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DynPerfCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        PerformanceManager.shutdown();
    }
}
