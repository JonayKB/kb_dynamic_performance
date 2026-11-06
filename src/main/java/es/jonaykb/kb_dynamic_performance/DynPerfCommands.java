package es.jonaykb.kb_dynamic_performance;

import es.jonaykb.kb_dynamic_performance.DynPerfConfig.Level;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class DynPerfCommands {

    private DynPerfCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dynamicperformance")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(DynPerfCommands::status))
                .then(Commands.literal("config").executes(DynPerfCommands::config))
                .then(Commands.literal("reload").executes(DynPerfCommands::reload));

        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("dp").requires(src -> src.hasPermission(2)).redirect(node));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!PerformanceManager.isRunning()) {
            src.sendFailure(Component.literal("KB DynPerf no está activo."));
            return 0;
        }

        double mspt = PerformanceManager.averageMspt();
        double tps = mspt <= 0 ? 20.0 : Math.min(20.0, 1000.0 / mspt);
        DynPerfConfig cfg = PerformanceManager.config();

        ChatFormatting msptColor = mspt >= cfg.lagThreshold ? ChatFormatting.RED
                : mspt > cfg.recoveryThreshold ? ChatFormatting.YELLOW
                : ChatFormatting.GREEN;

        MutableComponent msg = Component.literal("— KB Dynamic Performance —").withStyle(ChatFormatting.GOLD)
                .append(line("Estado", stateText(PerformanceManager.state())))
                .append(line("MSPT", Component.literal(String.format("%.1f ms", mspt)).withStyle(msptColor)))
                .append(line("TPS", Component.literal(String.format("%.1f", tps)).withStyle(msptColor)))
                .append(line("View distance", value(PerformanceManager.viewDistance())))
                .append(line("Simulation distance", value(PerformanceManager.simulationDistance())))
                .append(line("Mob cap", value(PerformanceManager.mobCapPercent() + "%")))
                .append(line("Próxima comprobación", value(PerformanceManager.secondsToNextCheck() + " s")));

        src.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int config(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        DynPerfConfig cfg = PerformanceManager.config();
        if (cfg == null) {
            src.sendFailure(Component.literal("KB DynPerf no está activo."));
            return 0;
        }

        MutableComponent msg = Component.literal("— Config activa —").withStyle(ChatFormatting.GOLD)
                .append(line("interval", value(cfg.interval + " s")))
                .append(line("lag_threshold", value(cfg.lagThreshold + " ms")))
                .append(line("recovery_threshold", value(cfg.recoveryThreshold + " ms")))
                .append(Component.literal("\nNiveles (bajada ↓, subida ↑):").withStyle(ChatFormatting.GRAY));

        int i = 1;
        for (Level l : cfg.levels) {
            msg.append(Component.literal("\n " + i++ + ". ").withStyle(ChatFormatting.DARK_GRAY))
               .append(Component.literal(l.toString()).withStyle(ChatFormatting.WHITE));
        }

        src.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            DynPerfConfig cfg = DynPerfConfig.load(KbDynPerf.configPath());
            PerformanceManager.reload(cfg);
            src.sendSuccess(() -> Component.literal("Config recargada.").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Config inválida, se mantiene la anterior: " + e.getMessage()));
            return 0;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static MutableComponent line(String label, Component value) {
        return Component.literal("\n" + label + ": ").withStyle(ChatFormatting.GRAY).append(value);
    }

    private static MutableComponent value(Object v) {
        return Component.literal(String.valueOf(v)).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent stateText(PerformanceManager.State state) {
        return switch (state) {
            case OPTIMAL -> Component.literal("Óptimo").withStyle(ChatFormatting.GREEN);
            case STABLE -> Component.literal("Estable").withStyle(ChatFormatting.AQUA);
            case SCALING_UP -> Component.literal("Recuperando").withStyle(ChatFormatting.YELLOW);
            case SCALING_DOWN -> Component.literal("Reduciendo").withStyle(ChatFormatting.GOLD);
            case LAGGING -> Component.literal("Con lag (todo al mínimo)").withStyle(ChatFormatting.RED);
        };
    }
}
