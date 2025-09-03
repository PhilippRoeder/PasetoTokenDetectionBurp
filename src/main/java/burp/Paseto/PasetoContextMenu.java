package burp.Paseto;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;

/**
 * Context-menu entry that finds a PASETO token in the selected request, lets
 * the tester edit it, and then either (1) replaces the request inside the
 * active message editor tab, or (2) re-issues the modified request directly.
 * We also ensure the resulting request/response is **explicitly inserted into
 * the Proxy/Logger history**, so you will always see the edited token there.
 */
public class PasetoContextMenu implements ContextMenuItemsProvider {

    /** vX.local|public.<payload>[.<footer>] */
    private static final Pattern PASETO_PATTERN =
            Pattern.compile("v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");

    private final MontoyaApi api;
    private HttpHandlerPaseto handler;

    public PasetoContextMenu(MontoyaApi api, HttpHandlerPaseto handler) {
        this.api = api;
        this.handler = handler;
    }

    //------------------------------------------------------------------
    // Context-menu integration
    //------------------------------------------------------------------
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent menuEvent) {
        if (!menuEvent.isFromTool(ToolType.PROXY)) return Collections.emptyList();
        List<Component> items = new ArrayList<>();

        JMenuItem editPaseto = new JMenuItem("Edit PASETO token");
        editPaseto.addActionListener(e -> {
            if (menuEvent.selectedRequestResponses().isEmpty()) {
                return;
            }

            // 1. Base request that the user right-clicked on
            HttpRequestResponse baseReqResp = menuEvent.selectedRequestResponses().get(0);
            HttpRequest baseRequest = baseReqResp.request();

            // 2. Locate the first PASETO token – bail out if none is present
            String token = findPasetoToken(baseRequest);
            if (token == null) {
                JOptionPane.showMessageDialog(null,
                        "No PASETO token found in the selected request.",
                        "Edit PASETO",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 3. Let the user edit the token (modal dialog)
            String editedToken = showEditablePasetoDialog(parsePaseto(token), token);
            if (editedToken.equals(token)) {
                return; // user cancelled or made no changes
            }

            // 4. Build the modified request
            HttpRequest modifiedRequest = replaceTokenInRequest(baseRequest, token, editedToken);

            this.handler.setPassetoRequest(modifiedRequest);
            this.handler.setId(token);
        });

        items.add(editPaseto);
        return items;
    }

    //------------------------------------------------------------------
    // Helper methods (token replace / find / dialog etc.)
    //------------------------------------------------------------------

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

    private String findPasetoToken(HttpRequest request) {
        // 1) Check headers
        for (HttpHeader header : request.headers()) {
            Matcher m = PASETO_PATTERN.matcher(header.value());
            if (m.find()) {
                return m.group();
            }
        }

        // 2) Check body
        Matcher m = PASETO_PATTERN.matcher(request.bodyToString());
        return m.find() ? m.group() : null;
    }

    private PasetoInfo parsePaseto(String token) {
        String[] parts = token.split("\\.");
        String version  = parts.length > 0 ? parts[0] : "";
        String purpose  = parts.length > 1 ? parts[1] : "";
        String payload  = parts.length > 2 ? parts[2] : "";
        String footer   = parts.length > 3 ? parts[3] : "";
        return new PasetoInfo(version, purpose, payload, footer);
    }

    private String showEditablePasetoDialog(PasetoInfo info, String rawToken) {
        final String[] result = { rawToken };
        JDialog dlg = new JDialog((Frame) null, "Edit PASETO token before sending", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout(5,5));

        // Raw token display (read-only)
        JTextArea rawArea = new JTextArea(rawToken, 3, 60);
        rawArea.setLineWrap(true);
        rawArea.setWrapStyleWord(true);
        rawArea.setEditable(false);
        rawArea.setBorder(BorderFactory.createTitledBorder("Original raw token"));
        dlg.add(new JScrollPane(rawArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.NORTH);

        // Full‑token edit panel
        JPanel wholePanel = new JPanel(new BorderLayout(5,0));
        wholePanel.setBorder(BorderFactory.createTitledBorder("Edit entire token"));
        JTextArea wholeField = new JTextArea(rawToken, 3, 60);
        wholeField.setLineWrap(true);
        wholeField.setWrapStyleWord(true);
        wholePanel.add(new JScrollPane(wholeField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        JButton submitWhole = new JButton("Save");
        wholePanel.add(submitWhole, BorderLayout.EAST);

        // Part‑wise edit panel
        JPanel partsPanel = new JPanel(new GridBagLayout());
        partsPanel.setBorder(BorderFactory.createTitledBorder("Edit token parts"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2,2,2,2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; partsPanel.add(new JLabel("Version:"), gbc);
        JTextField versionField = new JTextField(info.version, 24);
        gbc.gridx = 1; partsPanel.add(versionField, gbc);

        gbc.gridx = 0; gbc.gridy++; partsPanel.add(new JLabel("Purpose:"), gbc);
        JTextField purposeField = new JTextField(info.purpose, 24);
        gbc.gridx = 1; partsPanel.add(purposeField, gbc);

        // Payload row + decode button
        gbc.gridx = 0; gbc.gridy++; partsPanel.add(new JLabel("Payload:"), gbc);
        JTextField payloadField = new JTextField(info.payload, 24);
        gbc.gridx = 1; partsPanel.add(payloadField, gbc);
        JButton decodeButton = new JButton("Try Decode (base64)");
        gbc.gridx = 2; partsPanel.add(decodeButton, gbc);

        // Decoded payload row + encode button
        gbc.gridx = 1; gbc.gridy++;
        gbc.gridwidth = 1;
        JTextArea decodedPayloadArea = new JTextArea(3, 24);
        decodedPayloadArea.setEditable(true);
        decodedPayloadArea.setLineWrap(true);
        decodedPayloadArea.setWrapStyleWord(true);
        decodedPayloadArea.setBorder(BorderFactory.createTitledBorder("Decoded Payload (editable)"));
        partsPanel.add(new JScrollPane(decodedPayloadArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), gbc);

        JButton encodeButton = new JButton("Encode (base64)");
        gbc.gridx = 2; partsPanel.add(encodeButton, gbc);

        gbc.gridx = 0; gbc.gridy++; partsPanel.add(new JLabel("Footer:"), gbc);
        JTextField footerField = new JTextField(info.footer, 24);
        gbc.gridx = 1; partsPanel.add(footerField, gbc);

        JButton submitParts = new JButton("Save");
        gbc.gridx = 1; gbc.gridy++; gbc.anchor = GridBagConstraints.EAST; partsPanel.add(submitParts, gbc);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(wholePanel);
        center.add(Box.createVerticalStrut(8));
        center.add(partsPanel);
        dlg.add(center, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(ev -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(cancel);
        dlg.add(south, BorderLayout.SOUTH);

        // Button actions
        submitWhole.addActionListener((ActionEvent e) -> {
            String tok = wholeField.getText().trim();
            if (!tok.isEmpty()) result[0] = tok;
            dlg.dispose();
        });

        submitParts.addActionListener((ActionEvent e) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(versionField.getText().trim())
                    .append('.')
                    .append(purposeField.getText().trim())
                    .append('.')
                    .append(payloadField.getText().trim());
            String f = footerField.getText().trim();
            if (!f.isEmpty()) sb.append('.').append(f);
            result[0] = sb.toString();
            dlg.dispose();
        });

        // Decode button action
        decodeButton.addActionListener(ev -> {
            String payloadText = payloadField.getText().trim();
            try {
                Base64.Decoder urlDecoder = Base64.getUrlDecoder();
                byte[] decoded = urlDecoder.decode(payloadText);
                String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                decodedPayloadArea.setText(decodedStr);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dlg,
                        "Invalid Base64 payload:\n" + ex.getMessage(),
                        "Decode error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // Encode button action
        encodeButton.addActionListener(ev -> {
            String decodedText = decodedPayloadArea.getText().trim();
            try {
                Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
                String encoded = urlEncoder.encodeToString(decodedText.getBytes(StandardCharsets.UTF_8));
                payloadField.setText(encoded);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg,
                        "Encoding failed:\n" + ex.getMessage(),
                        "Encode error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        dlg.pack();
        dlg.setSize(new Dimension(720, 520));
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
        return result[0];
    }

    /** Simple record-like holder for the token parts. */
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
