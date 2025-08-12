package burp.Paseto;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.settings.SettingsPanelWithData;

import java.util.ArrayList;
import java.util.List;


public class HttpHandlerPaseto implements HttpHandler {
    private boolean dirty;
    private List<HttpRequest> pasetoRequest= new ArrayList<>();;
    //private String hash_id;
    private List<String> hash_id = new ArrayList<>();
    private boolean markRequests;
    private SettingsPanelWithData settings;

    public HttpHandlerPaseto(SettingsPanelWithData settings){
        this.settings=settings;
    }

    public boolean markRequests(){
        return settings.getBoolean("markRequests");
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        // Example header injection kept from the original sample
        String id = httpRequestToBeSent.headerValue("X-Paseto-Edit-Id");
        Annotations annotations = Annotations.annotations(null, null);
        HttpRequest request=httpRequestToBeSent;
        if(dirty&&(this.hash_id.contains(id))){
            int request_index=this.hash_id.indexOf(id);
            request=this.pasetoRequest.get(request_index).withRemovedHeader("X-Paseto-Edit-Id");
            if(markRequests()){
                annotations = Annotations.annotations(null, HighlightColor.GREEN);
            }
            this.hash_id.remove(request_index);
            this.pasetoRequest.remove(request_index);
        }
        // Continue with the (possibly) modified request


        dirty=false;
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
        this.pasetoRequest.add(request);
    }


    public void setId(String hash_id) {
        this.hash_id.add(hash_id);
    }


}
