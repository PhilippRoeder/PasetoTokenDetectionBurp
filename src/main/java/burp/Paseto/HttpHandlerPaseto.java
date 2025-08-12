package burp.Paseto;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;



public class HttpHandlerPaseto implements HttpHandler {
    private boolean dirty;
    private HttpRequest pasetoRequest;
    private int hash_id;
    private boolean markRequests;

    public HttpHandlerPaseto(boolean markRequests){
        this.markRequests=markRequests;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        // Example header injection kept from the original sample
        Annotations annotations = Annotations.annotations(null, null);
        HttpRequest request=httpRequestToBeSent;
        if(dirty&&(httpRequestToBeSent.hashCode()==hash_id)){
            request=this.pasetoRequest;
            if(markRequests){
                annotations = Annotations.annotations(null, HighlightColor.GREEN);
            }
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
        this.pasetoRequest=request;
    }


    public void setId(int hash_id) {
        this.hash_id = hash_id;
    }


}
