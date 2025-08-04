package burp.Paseto;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HttpHandlerPaseto implements HttpHandler {
    private boolean dirty;
    private HttpRequest pasetoRequest;

    public HttpHandlerPaseto(){

    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        // Example header injection kept from the original sample
        HttpRequest request=httpRequestToBeSent;
        if(dirty){
            request=this.pasetoRequest;
        }
        // Continue with the (possibly) modified request
        dirty=false;
        Annotations annotations = Annotations.annotations(null, HighlightColor.GREEN);

        return RequestToBeSentAction.continueWith(request, annotations);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        return null; // No response processing needed.
    }

    void setDirty(boolean val){
        this.dirty=val;
    }
    void setPassetoRequest(HttpRequest request){
        this.pasetoRequest=request;
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
