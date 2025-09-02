package burp.Paseto;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.io.InputStream;
import java.util.Properties;

public class EditorTab implements BurpExtension {
    private SettingsPanelWithData settings;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("PasetoToken");

        logHeader(api);
        setupSettings(api);
        registerHandlers(api);
    }

    /**
     * Logs the extension metadata in a clean, formatted header.
     */
    private void logHeader(MontoyaApi api) {
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Project Information");
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Author       : Philipp Röder");
        api.logging().logToOutput(" Contributors : Sebastian Vetter, Kartik Rastogi");
        api.logging().logToOutput(" Version      : " + loadVersion(api));
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Further logging below on found token...");
        api.logging().logToOutput("====================================================");
    }

    /**
     * Builds and registers the settings panel for the extension.
     */
    private void setupSettings(MontoyaApi api) {
        settings = SettingsPanelBuilder.settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                .withTitle("Paseto Token Settings")
                .withDescription("Toggle request marking.")
                .withSettings(
                        SettingsPanelSetting.booleanSetting("markRequests", false)
                )
                .build();

        api.userInterface().registerSettingsPanel(settings);
    }

    /**
     * Registers HTTP, Proxy, and Context Menu handlers.
     */
    private void registerHandlers(MontoyaApi api) {
        HttpHandlerPaseto handler = new HttpHandlerPaseto(settings, api);

        api.proxy().registerRequestHandler(new PasetoProxyHandler(settings, api));
        api.userInterface().registerContextMenuItemsProvider(new PasetoContextMenu(api, handler));
        api.http().registerHttpHandler(handler);
    }

    /**
     * Loads the extension version from version.properties.
     * Falls back to "Unknown" if not found or invalid.
     */
    private String loadVersion(MontoyaApi api) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                return props.getProperty("version", "Unknown");
            } else {
                api.logging().logToOutput("[!] version.properties not found – using fallback.");
                return "Unknown";
            }
        } catch (Exception e) {
            api.logging().logToOutput("[!] Failed to load version.properties: " + e.getMessage());
            return "Unknown";
        }
    }
}
