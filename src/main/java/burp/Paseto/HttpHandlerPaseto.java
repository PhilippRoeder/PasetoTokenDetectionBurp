package burp.Paseto;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HttpHandlerPaseto implements HttpHandler {

    private static final Pattern PASETO_PATTERN =
            Pattern.compile("v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");


    private List<HttpRequest> pasetoRequest= new ArrayList<>();;
    //private String hash_id;
    private List<String> hash_id = new ArrayList<>();
    private boolean markRequests;
    private SettingsPanelWithData settings;
    private final MontoyaApi api;

    public HttpHandlerPaseto(SettingsPanelWithData settings,  MontoyaApi api){
        this.api=api;
        this.settings=settings;
    }

    public boolean markRequests(){
        return settings.getBoolean("markRequests");
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        // Example header injection kept from the original sample
        String id = findPasetoToken(httpRequestToBeSent);
        Annotations annotations = Annotations.annotations(null, null);
        HttpRequest request=httpRequestToBeSent;

        api.logging().logToOutput(id);

        if(this.hash_id.contains(id)){

            int request_index=this.hash_id.indexOf(id);
            api.logging().logToOutput(request_index);
            request=this.pasetoRequest.get(request_index).withRemovedHeader("X-Paseto-Edit-Id");
            if(markRequests()){
                annotations = Annotations.annotations(null, HighlightColor.GREEN);
            }
            this.hash_id.remove(request_index);
            this.pasetoRequest.remove(request_index);
        }
        // Continue with the (possibly) modified request



        return RequestToBeSentAction.continueWith(request, annotations);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        return null; // No response processing needed.
    }


    void setPassetoRequest(HttpRequest request){
        this.pasetoRequest.add(request);
    }


    public void setId(String hash_id) {
        this.hash_id.add(hash_id);
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

}
