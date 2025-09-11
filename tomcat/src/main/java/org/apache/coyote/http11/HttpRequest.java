package org.apache.coyote.http11;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {

    private final RequestLine requestLine;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final String body;

    public HttpRequest(
            RequestLine requestLine,
            Map<String, String> headers,
            Map<String, String> queryParams,
            String body
    ) {
        this.requestLine = requestLine;
        this.headers = new HashMap<>(headers);
        this.queryParams = new HashMap<>(queryParams);
        this.body = body;
    }

    public RequestLine getRequestLine() {
        return requestLine;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String getBody() {
        return body;
    }

    static Map<String, String> parseQueryParams(String fullPath) {
        Map<String, String> queryParams = new HashMap<>();
        int index = fullPath.indexOf("?");
        if (index == -1) {
            return queryParams;
        }

        String queryString = fullPath.substring(index + 1);
        for (String pairs : queryString.split("&")) {
            String[] pair = pairs.split("=", 2);
            if (pair.length == 2) {
                queryParams.put(pair[0], pair[1]);
            }
        }
        return queryParams;
    }

    static Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pairs : body.split("&")) {
            String[] pair = pairs.split("=", 2);
            if (pair.length == 2) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }
}
