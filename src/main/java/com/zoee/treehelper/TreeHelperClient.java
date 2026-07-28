package com.zoee.treehelper;

import com.zoee.treehelper.command.TreeCommand;
import com.zoee.treehelper.config.TreeConfig;
import com.zoee.treehelper.hotm.HelperPanel;
import com.zoee.treehelper.hotm.HotmOverlay;
import com.zoee.treehelper.hotm.HotmScanner;
import com.zoee.treehelper.hotm.PowderTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreeHelperClient implements ClientModInitializer {

    public static final String MOD_ID = "treehelper";
    public static final Logger LOGGER = LoggerFactory.getLogger("Tree Helper");

    private static TreeConfig config;
    private static HotmScanner scanner;
    private int tabPollTicks;

    @Override
    public void onInitializeClient() {
        config = TreeConfig.load();
        PowderTracker tracker = new PowderTracker(config);
        scanner = new HotmScanner(config, tracker);

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> TreeCommand.register(dispatcher));

        HotmOverlay.register(config, scanner);
        HelperPanel.register(config, tracker, scanner);

        // Live powder tracking while mining: the tab list shows current balances
        // ("Mithril: 286,166"), so poll it every 2s for the affordability notifications.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            scanner.guideTick(mc); // guided Get Data scan (no-op unless one is running)
            if (++tabPollTicks >= 40) {
                tabPollTicks = 0;
                tracker.scanTabList(mc);
            }
        });

        LOGGER.info("HOTM Tree Helper ready — active path: {}",
                config.activePath == null ? "none" : config.activePath.displayName);
    }

    public static TreeConfig config() {
        return config;
    }

    public static HotmScanner scanner() {
        return scanner;
    }
}
