package org.apache.coyote.http11;

import com.techcourse.exception.UncheckedServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import org.apache.coyote.Processor;
import org.apache.coyote.http11.controller.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);

    private final Socket connection;
    private final RequestMapping requestMapping = new RequestMapping();

    public Http11Processor(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.info("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(Socket connection) {
        try (var inputStream = connection.getInputStream();
                var outputStream = connection.getOutputStream();
                var reader = new BufferedReader(new InputStreamReader(inputStream))) {

            // RequestLine 파싱
            RequestLine requestLine = RequestLine.parse(reader.readLine());

            // 헤더 읽기
            Map<String, String> headers = new HashMap<>();
            int contentLength = 0;
            String headerLine;
            while (!(headerLine = reader.readLine()).isEmpty()) {
                int index = headerLine.indexOf(":");
                if (index != -1) {
                    String key = headerLine.substring(0, index).trim();
                    String value = headerLine.substring(index + 1).trim();
                    headers.put(key, value);
                    if ("Content-Length".equalsIgnoreCase(key)) {
                        contentLength = Integer.parseInt(value);
                    }
                }
            }

            // 쿼리 파라미터 / 폼 데이터 파싱
            Map<String, String> queryParams = HttpRequest.parseQueryParams(requestLine.getFullPath());
            String body = null;
            if ("POST".equalsIgnoreCase(requestLine.getMethod()) && contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                reader.read(bodyChars);
                body = new String(bodyChars);
                queryParams = HttpRequest.parseFormData(body);
            }

            // HttpRequest / HttpResponse 파싱
            HttpRequest request = new HttpRequest(requestLine, headers, queryParams, body);
            HttpResponse response = new HttpResponse();

            // 컨트롤러 매핑 후 실행
            Controller controller = requestMapping.getController(request);
            controller.service(request, response);

            // 응답 전송
            response.write(outputStream);

        } catch (IOException | UncheckedServletException e) {
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.error("컨트롤러 실행 중 예외가 발생했습니다.", e);
        }
    }
}
