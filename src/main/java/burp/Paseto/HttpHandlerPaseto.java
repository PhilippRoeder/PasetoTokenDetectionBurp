package burp.Paseto;

import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Burp Suite Montoya HTTP handler that detects PASETO tokens in outbound requests and
 * lets the user change the individual token parts (version, purpose, payload, footer)
 * in a small Swing dialog *before* the request is sent.
 */
public class HttpHandlerPaseto implements HttpHandler {

    /**
     * Simple RegEx that matches both local and public PASETO tokens (v1–v4).
     * No cryptographic validation is performed – we only need to locate and split it.
     */
    private static final Pattern PASETO_PATTERN =
            Pattern.compile("v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        // Example header injection kept from the original sample
        HttpRequest request = httpRequestToBeSent.withAddedHeader("test", "1");

        // Look for a PASETO token in headers or body
        String pasetoToken = findPasetoToken(request);
        if (pasetoToken != null) {
            PasetoInfo info = parsePaseto(pasetoToken);

            // Present editable dialog (modal – blocks until the user decides)
            String editedToken = showEditablePasetoDialog(info, pasetoToken);
            if (!editedToken.equals(pasetoToken)) {
                // Replace the token everywhere it occurs (header/body first hit).
                request = replaceTokenInRequest(request, pasetoToken, editedToken);
            }
        }

        // Continue with the (possibly) modified request
        return RequestToBeSentAction.continueWith(request);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        return null; // No response processing needed.
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
        Matcher m = PASETO_PATTERN.matcher(request.bodyToString());
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    /** Splits the token into its dot‑separated components. */
    private PasetoInfo parsePaseto(String token) {
        String[] parts = token.split("\\.");
        String version = parts.length > 0 ? parts[0] : "";
        String purpose = parts.length > 1 ? parts[1] : "";
        String payload = parts.length > 2 ? parts[2] : "";
        String footer  = parts.length > 3 ? parts[3] : "";
        return new PasetoInfo(version, purpose, payload, footer);
    }

    /**
     * Builds a modal dialog with text fields so the user can tweak each part.
     * Returns the (possibly edited) token when the user clicks OK; otherwise the
     * original token unchanged when Cancel/Close is chosen.
     */
    private String showEditablePasetoDialog(PasetoInfo info, String rawToken) {
        JTextField versionField  = new JTextField(info.version, 24);
        JTextField purposeField  = new JTextField(info.purpose, 24);
        JTextField payloadField  = new JTextField(info.payload, 24);
        JTextField footerField   = new JTextField(info.footer, 24);

        JTextArea tokenArea = new JTextArea(rawToken, 3, 48);
        tokenArea.setLineWrap(true);
        tokenArea.setWrapStyleWord(true);
        tokenArea.setEditable(false);
        tokenArea.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JPanel fieldPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = 0; fieldPanel.add(new JLabel("Version:"), gbc);
        gbc.gridx = 1; fieldPanel.add(versionField, gbc);
        gbc.gridx = 0; gbc.gridy++; fieldPanel.add(new JLabel("Purpose:"), gbc);
        gbc.gridx = 1; fieldPanel.add(purposeField, gbc);
        gbc.gridx = 0; gbc.gridy++; fieldPanel.add(new JLabel("Payload:"), gbc);
        gbc.gridx = 1; fieldPanel.add(payloadField, gbc);
        gbc.gridx = 0; gbc.gridy++; fieldPanel.add(new JLabel("Footer:"), gbc);
        gbc.gridx = 1; fieldPanel.add(footerField, gbc);

        JPanel mainPanel = new JPanel(new BorderLayout(5,5));
        mainPanel.add(fieldPanel, BorderLayout.NORTH);
        mainPanel.add(new JLabel("Raw token:"), BorderLayout.CENTER);
        mainPanel.add(new JScrollPane(tokenArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.SOUTH);
        mainPanel.setPreferredSize(new Dimension(600, 300));

        int result = JOptionPane.showConfirmDialog(null, mainPanel,
                "Edit PASETO token before sending", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            // Build token from user‑supplied text – omit footer if left blank
            StringBuilder sb = new StringBuilder();
            sb.append(versionField.getText().trim())
                    .append('.')
                    .append(purposeField.getText().trim())
                    .append('.')
                    .append(payloadField.getText().trim());
            String footerText = footerField.getText().trim();
            if (!footerText.isEmpty()) {
                sb.append('.').append(footerText);
            }
            return sb.toString();
        }
        // No change
        return String.join(".", info.version, info.purpose, info.payload, info.footer).replaceAll("\\.$", "");
    }

    /**
     * Replaces the first occurrence of oldToken with newToken in both headers and body.
     * Uses the Montoya API's withUpdatedHeaders method for bulk header replacement.
     */
    private HttpRequest replaceTokenInRequest(HttpRequest original, String oldToken, String newToken) {
        HttpRequest updated = original;

        // --- Headers ---
        List<HttpHeader> modifiedHeaders = new ArrayList<>();
        boolean headerChanged = false;
        for (HttpHeader h : original.headers()) {
            String newValue = h.value().replaceFirst(Pattern.quote(oldToken), Matcher.quoteReplacement(newToken));
            if (!newValue.equals(h.value())) {
                headerChanged = true;
                modifiedHeaders.add(HttpHeader.httpHeader(h.name(), newValue));
            } else {
                modifiedHeaders.add(h);
            }
        }
        if (headerChanged) {
            updated = updated.withUpdatedHeaders(modifiedHeaders);
        }

        // --- Body (first occurrence) ---
        String body = updated.bodyToString();
        if (body.contains(oldToken)) {
            updated = updated.withBody(body.replaceFirst(Pattern.quote(oldToken), Matcher.quoteReplacement(newToken)));
        }

        return updated;
    }

    /** Holds the four high‑level token parts. */
    private static class PasetoInfo {
        final String version;
        final String purpose;
        final String payload;
        final String footer;

        PasetoInfo(String version, String purpose, String payload, String footer) {
            this.version = version;
            this.purpose = purpose;
            this.payload = payload;
            this.footer  = footer;
        }
    }
}
