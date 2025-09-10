package org.apache.coyote.http11;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.exception.UncheckedServletException;
import com.techcourse.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.coyote.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);

    private final Socket connection;

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
                var reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            // 요청 라인
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                sendResponse(400, "Bad Request", "text/plain;charset=utf-8", new byte[0], outputStream, null);
                return;
            }

            String[] tokens = requestLine.trim().split("\\s+");
            if (tokens.length < 3) {
                sendResponse(400, "Bad Request", "text/plain;charset=utf-8", new byte[0], outputStream,null);
                return;
            }
            String method = tokens[0];
            String fullPath = tokens[1];
            String requestPath = extractPath(fullPath);

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

            // 쿼리/폼 파라미터 처리 (GET은 html, POST는 form만 있다는 전제)
            Map<String, String> queryParams = extractQueryParams(fullPath);
            if ("GET".equalsIgnoreCase(method)) {
                queryParams = extractQueryParams(fullPath);
            } else if ("POST".equalsIgnoreCase(method) && contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                reader.read(bodyChars);
                String body = new String(bodyChars);
                queryParams = parseFormData(body);
            }

            // 라우팅
            switch (requestPath) {
                case "/" -> {
                    byte[] body = "Hello world!".getBytes(StandardCharsets.UTF_8);
                    sendResponse(200, "OK", "text/html;charset=utf-8", body, outputStream, null);
                }
                case "/login" -> {
                    if ("GET".equalsIgnoreCase(method)) {
                        handleStaticResource("/login.html", outputStream);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        handleLogin(queryParams, outputStream);
                    }
                }
                case "/register" -> {
                    if ("GET".equalsIgnoreCase(method)) {
                        handleStaticResource("/register.html", outputStream);
                    } else if ("POST".equalsIgnoreCase(method)) {
                        handleRegister(queryParams, outputStream);
                    }
                }
                default -> handleStaticResource(requestPath, outputStream);
            }

        } catch (IOException | UncheckedServletException e) {
            log.error(e.getMessage(), e);
        }
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new HashMap<>();
        for (String pairs : body.split("&")) {
            String[] pair = pairs.split("=", 2);
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }

    private String extractPath(String fullPath) {
        int index = fullPath.indexOf("?");

        if (index != -1) {
            return fullPath.substring(0, index);
        }
        return fullPath;
    }

    private Map<String, String> extractQueryParams(String fullPath) {
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

    private String determineContentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css;charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript;charset=utf-8";
        }
        if (path.endsWith(".html")) {
            return "text/html;charset=utf-8";
        }
        return "application/octet-stream";
    }

    private void handleRegister(Map<String, String> queryParams, OutputStream outputStream) throws IOException {
        String account = queryParams.get("account");
        String password = queryParams.get("password");
        String email = queryParams.get("email");

        User user = new User(account, password, email);
        InMemoryUserRepository.save(user);

        sendRedirect("/index.html", outputStream);
    }

    private void handleLogin(Map<String, String> queryParams, OutputStream outputStream) throws IOException {
        String account = queryParams.get("account");
        String password = queryParams.get("password");

        boolean success = InMemoryUserRepository.findByAccount(account)
                .map(user -> user.checkPassword(password))
                .orElse(false);

        if (success) {
            User user = InMemoryUserRepository.findByAccount(account).get();
            Session session = SessionManager.createSession();
            session.setAttribute("user", user);
            sendRedirectWithCookie("/index.html", "JSESSIONID=" + session.getId(), outputStream);
        } else {
            sendRedirect("/401.html", outputStream);
        }
    }

    private void handleStaticResource(String requestPath, OutputStream outputStream)
            throws IOException {
        URL resourceUrl = ClassLoader.getSystemResource("static" + requestPath);
        if (resourceUrl == null) {
            sendResponse(404, "Not Found", "text/html;charset=utf-8", new byte[0], outputStream, null);
            return;
        }

        byte[] fileBytes = Files.readAllBytes(Paths.get(resourceUrl.getPath()));
        String contentType = determineContentType(requestPath);

        sendResponse(200, "OK", contentType, fileBytes, outputStream, null);
    }

    private void sendResponse(int statusCode,
                              String statusMessage,
                              String contentType,
                              byte[] body,
                              OutputStream outputStream,
                              String cookie) throws IOException {
        String responseHeader = String.join(
                "\r\n",
                "HTTP/1.1 " + statusCode + " " + statusMessage,
                "Content-Type: " + contentType,
                "Content-Length: " + body.length,
                (cookie != null ? "Set-Cookie: " + cookie : ""),
                "",
                ""
        );
        outputStream.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        outputStream.write(body);
        outputStream.flush();
    }

    private void sendRedirect(String location, OutputStream outputStream) throws IOException {
        String responseHeader = String.join(
                "\r\n",
                "HTTP/1.1 302 Found",
                "Location: " + location,
                "",
                ""
        );
        outputStream.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void sendRedirectWithCookie(String location, String cookie, OutputStream outputStream) throws IOException {
        String responseHeader = String.join(
                "\r\n",
                "HTTP/1.1 302 Found",
                "Location: " + location,
                "Set-Cookie: " + cookie,
                "",
                ""
        );
        outputStream.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
