package burp.Paseto;

import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Burp Suite Montoya HTTP handler that detects PASETO tokens in outbound requests and
 * pops up a small Swing dialog showing the decoded metadata (version, purpose, payload, footer).
 * The dialog is informational only and can simply be clicked away.
 */
public class HttpHandlerPaseto implements HttpHandler {

    /*
     * Very small RegEx that matches both local and public tokens (v1–v4)
     * It purposely does **not** attempt to validate the token cryptographically –
     * that is not required here. We just want to pattern‑match and split the pieces.
     */
    private static final Pattern PASETO_PATTERN =
            Pattern.compile("v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");

    public HttpHandlerPaseto() {
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        // Keep the sample header‑injection the user already had.
        HttpRequest modifiedRequest = httpRequestToBeSent.withAddedHeader("test", "1");

        // Look for a PASETO token in headers or body
        String pasetoToken = findPasetoToken(modifiedRequest);
        if (pasetoToken != null) {
            PasetoInfo info = parsePaseto(pasetoToken);

            // Ensure GUI updates happen on the EDT
            SwingUtilities.invokeLater(() -> showPasetoDialog(info, pasetoToken));
        }

        // Let Burp continue with the (potentially) modified request
        return RequestToBeSentAction.continueWith(modifiedRequest);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        // We don’t need to do anything with the response for this extension.
        return null;
    }

    /**
     * Iterates over all headers (value part only) and then the request body to
     * find the first string that looks like a PASETO token.
     */
    private String findPasetoToken(HttpRequest request) {
        // 1) Headers (e.g. Authorization: Bearer <token>)
        for (HttpHeader header : request.headers()) {
            Matcher m = PASETO_PATTERN.matcher(header.value());
            if (m.find()) {
                return m.group();
            }
        }

        // 2) Body (JSON, form‑encoded, etc.)
        String body = request.bodyToString();
        Matcher m = PASETO_PATTERN.matcher(body);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    /**
     * Splits a PASETO token into its basic components. Does not attempt any cryptographic validation.
     */
    private PasetoInfo parsePaseto(String token) {
        String[] parts = token.split("\\.");
        String version = parts.length > 0 ? parts[0] : "";
        String purpose = parts.length > 1 ? parts[1] : "";
        String payload = parts.length > 2 ? parts[2] : "";
        String footer = parts.length > 3 ? parts[3] : null;
        return new PasetoInfo(version, purpose, payload, footer);
    }

    /**
     * Very small Swing dialog that shows the parsed token details.
     */
    private void showPasetoDialog(PasetoInfo info, String rawToken) {
        StringBuilder sb = new StringBuilder();
        sb.append("PASETO token detected:\n\n");
        sb.append("Version:  ").append(info.version).append('\n');
        sb.append("Purpose:  ").append(info.purpose).append('\n');
        sb.append("Payload (Base64Url):  ").append(info.payload).append('\n');
        if (info.footer != null) {
            sb.append("Footer (Base64Url):  ").append(info.footer).append('\n');
        }
        sb.append("\nRaw token:\n").append(rawToken);

        JOptionPane.showMessageDialog(null, sb.toString(), "PASETO Details", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Simple value object.
     */
    private static class PasetoInfo {
        final String version;
        final String purpose;
        final String payload;
        final String footer;

        PasetoInfo(String version, String purpose, String payload, String footer) {
            this.version = version;
            this.purpose = purpose;
            this.payload = payload;
            this.footer = footer;
        }
    }
}


