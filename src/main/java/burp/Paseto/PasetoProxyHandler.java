package burp.Paseto;


import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasetoProxyHandler implements ProxyRequestHandler {
    private static final Pattern PASETO_PATTERN =
            Pattern.compile("v[0-9]\\.(local|public)\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?");

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        // Example header injection kept from the original sample
        Annotations annotations = null;
        // Look for a PASETO token in headers or body
        boolean pasetoToken = this.findPasetoToken(interceptedRequest);
        if (pasetoToken) {

            annotations = Annotations.annotations(null, HighlightColor.GREEN);
        }

            // Continue with the (possibly) modified request
            return annotations == null
                    ?
                    ProxyRequestReceivedAction.continueWith(interceptedRequest)
                    :
                    ProxyRequestReceivedAction.continueWith(interceptedRequest, annotations);
        }

        @Override
        public ProxyRequestToBeSentAction handleRequestToBeSent (InterceptedRequest interceptedRequest){
            return null;
        }

        private boolean findPasetoToken (HttpRequest request){
            // 1) Headers (e.g. Authorization: Bearer <token>)
            for (HttpHeader header : request.headers()) {
                Matcher m = PASETO_PATTERN.matcher(header.value());
                if (m.find()) {
                    return true;
                }
            }

            // 2) Body (JSON, form‑encoded, etc.)
            Matcher m = PASETO_PATTERN.matcher(request.bodyToString());
            return m.find();
        }


    }