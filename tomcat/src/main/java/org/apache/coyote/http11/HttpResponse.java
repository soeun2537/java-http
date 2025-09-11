package org.apache.coyote.http11;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse {

    private final StringBuilder response = new StringBuilder();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body = new byte[0];

    public void setStatusLine(String httpVersion, int statusCode, String reasonPhrase) {
        response.append(httpVersion)
                .append(" ")
                .append(statusCode)
                .append(" ")
                .append(reasonPhrase)
                .append("\r\n");
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(byte[] body) {
        this.body = body;
        addHeader("Content-Length", String.valueOf(body.length));
    }

    public void write(OutputStream outputStream) throws IOException {
        StringBuilder headerBuilder = new StringBuilder(response);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerBuilder.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\r\n");
        }
        headerBuilder.append("\r\n");

        outputStream.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }
}
