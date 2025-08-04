package burp.Paseto;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;


import java.io.InputStream;
import java.util.Properties;



public class EditorTab implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("PasetoToken");
        api.logging().logToOutput("Author: Philipp Röder");

        String version = loadVersion();
        api.logging().logToOutput("Version: " + version);
        HttpHandlerPaseto handler = new HttpHandlerPaseto();


        api.proxy().registerRequestHandler(new PasetoProxyHandler());
        api.userInterface().registerContextMenuItemsProvider(new PasetoContextMenu(api, handler));
        api.http().registerHttpHandler(handler);



    }

    private String loadVersion() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("version.properties")) {
            Properties props = new Properties();
            if (input != null) {
                props.load(input);
                return props.getProperty("version", "Unknown");
            } else {
                return "Not Found";
            }
        } catch (Exception e) {
            return "Error";
        }
    }
}
