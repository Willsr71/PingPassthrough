package sr.will.pingpassthrough;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Plugin(id = PluginInfo.ID, name = PluginInfo.NAME, version = PluginInfo.VERSION, description = PluginInfo.DESCRIPTION)
public class PingPassthrough {
    @Inject
    private ProxyServer proxy;

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        // We can't get a server if the client doesn't provide a host
        if (!event.getConnection().getVirtualHost().isPresent()) return;

        String clientHost = event.getConnection().getVirtualHost().get().getHostName();
        List<String> forcedServers = proxy.getConfiguration().getForcedHosts().get(clientHost);

        // Ignore if the client host is not in the forced host config
        if (forcedServers == null || forcedServers.size() == 0) return;

        try {
            // Go through the "try" section of the hosts until we get a response
            for (String forcedServer : forcedServers) {
                ServerPing serverPing = proxy.getServer(forcedServer).get().ping().get();
                // No valid result, skip
                if (serverPing == null) continue;

                // Found a result, use that
                event.setPing(serverPing);
                break;
            }
        } catch (InterruptedException | ExecutionException e) {
            // Velocity doesn't let us refuse the connection or set the ping to null, so we have to timeout
            try {
                // Default timeout is 30 seconds, so wait 31 for good measure
                Thread.sleep(31 * 1000);
            } catch (InterruptedException f) {
                f.printStackTrace();
            }

            // Send a result anyway in case the client has a longer than default timeout value
            event.setPing(ServerPing.builder()
                    .description(TextComponent.of("Server is offline", TextColor.RED))
                    .build());
        }
    }
}
