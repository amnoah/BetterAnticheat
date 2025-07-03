package better.anticheat.velocity;

import better.anticheat.core.BetterAnticheat;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "betteranticheat",
    name = "BetterAnticheat",
    version = "1.0.0",
    description = "An auxiliary anticheat designed with stability as its focus.",
    authors = {"am noah"}
)
public class BetterAnticheatVelocity {

    private static final int B_STATS_ID = 00000;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    private BetterAnticheat core;
    private Metrics metrics;

    @Inject
    public BetterAnticheatVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        this.core = new BetterAnticheat(
            new VelocityDataBridge(this, server, logger),
            dataDirectory
        );

        this.core.enable();

        this.metrics = metricsFactory.make(this, B_STATS_ID);
    }

    @Subscribe
    public void onProxyReload(final ProxyReloadEvent event) {
        this.core.load();
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        if (this.core != null) {
            this.core.disable();
        }
        if (this.metrics != null) {
            this.metrics.shutdown();
        }
    }
}
