package better.anticheat.velocity;

import better.anticheat.core.DataBridge;
import com.github.retrooper.packetevents.protocol.player.User;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import revxrsal.commands.exception.CommandExceptionHandler;
import revxrsal.commands.parameter.ParameterTypes;
import revxrsal.commands.velocity.VelocityLamp;
import revxrsal.commands.velocity.actor.VelocityCommandActor;

import java.io.Closeable;
import java.time.Duration;
import java.util.function.Consumer;

public class VelocityDataBridge implements DataBridge<VelocityCommandActor> {

    private final BetterAnticheatVelocity plugin;
    private final ProxyServer server;
    private final Logger logger;

    public VelocityDataBridge(BetterAnticheatVelocity plugin, ProxyServer server, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
    }

    @Override
    public boolean hasPermission(User user, String... permission) {
        if (user.getUUID() == null) return false;
        
        final var optionalPlayer = server.getPlayer(user.getUUID());
        if (optionalPlayer.isEmpty()) return false;
        
        final var player = optionalPlayer.get();
        
        for (final var perm : permission) {
            if (player.hasPermission(perm)) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void logInfo(String message) {
        logger.info(message);
    }

    @Override
    public void logWarning(String message) {
        logger.warn(message);
    }

    @Override
    public void sendCommand(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public @Nullable Closeable registerTickListener(User user, Runnable runnable) {
        // Velocity doesn't have a tick system like Bukkit/Sponge, so we'll use a repeating task
        // Running every 50ms (1 tick = 50ms in Minecraft)
        final var task = server.getScheduler()
            .buildTask(plugin, runnable)
            .repeat(Duration.ofMillis(50))
            .schedule();
        
        return task::cancel;
    }

    @Override
    public @Nullable Closeable runTaskLater(User user, Runnable runnable, int delayTicks) {
        // Convert ticks to milliseconds (1 tick = 50ms)
        final var delayMs = delayTicks * 50L;
        
        final var task = server.getScheduler()
            .buildTask(plugin, runnable)
            .delay(Duration.ofMillis(delayMs))
            .schedule();
        
        return task::cancel;
    }

    @Override
    public void registerCommands(@Nullable CommandExceptionHandler<VelocityCommandActor> commandExceptionHandler, @Nullable Consumer<ParameterTypes.Builder<VelocityCommandActor>> parameterBuilder, Object... commands) {
        var lampBuilder = VelocityLamp.builder(plugin, server);

        if (commandExceptionHandler != null) lampBuilder = lampBuilder.exceptionHandler(commandExceptionHandler);
        if (parameterBuilder != null) lampBuilder = lampBuilder.parameterTypes(parameterBuilder);

        final var lamp = lampBuilder.build();
        lamp.register(commands);
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
