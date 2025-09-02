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

        // Pretty log header
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Project Information");
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Author       : Philipp Röder");
        api.logging().logToOutput(" Contributors : Sebastian Vetter, Kartik Rastogi");
        api.logging().logToOutput(" Version      : " + loadVersion(api));
        api.logging().logToOutput("====================================================");
        api.logging().logToOutput(" Further logging below on found token...");
        api.logging().logToOutput("====================================================");

        settings = SettingsPanelBuilder.settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS) // or PROJECT_SETTINGS
                .withTitle("Paseto Token Settings")
                .withDescription("Toggle request marking.")
                .withSettings(
                        SettingsPanelSetting.booleanSetting("markRequests", false)
                )
                .build();

        api.userInterface().registerSettingsPanel(settings);

        boolean markRequests = settings.getBoolean("markRequests");

        HttpHandlerPaseto handler = new HttpHandlerPaseto(settings, api);

        api.proxy().registerRequestHandler(new PasetoProxyHandler(settings));
        api.userInterface().registerContextMenuItemsProvider(new PasetoContextMenu(api, handler));
        api.http().registerHttpHandler(handler);
    }

    private String loadVersion(MontoyaApi api) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            Properties props = new Properties();
            if (input != null) {
                props.load(input);
                return props.getProperty("version", "Unknown");
            } else {
            if (input == null) {
                return "Not Found";
            }
        } catch (Exception e) {
            Properties props = new Properties();
            props.load(input);
            return props.getProperty("version", "Unknown");
        } catch (IOException e) {
            api.logging().logToError("Failed to load version: " + e.getMessage());
            return "Error";
        }
    }
}
