package org.apache.coyote.http11.controller;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.model.User;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;
import org.apache.coyote.http11.HttpSession;
import org.apache.coyote.http11.HttpSessionManager;

public class LoginController extends AbstractController {

    @Override
    protected void doGet(HttpRequest request, HttpResponse response) throws Exception {
        String cookie = request.getHeaders().get("Cookie");
        if (cookie != null) {
            for (String c : cookie.split(";")) {
                String[] keyValue = c.trim().split("=", 2);
                if (keyValue.length == 2 && "JSESSIONID".equals(keyValue[0])) {
                    HttpSession httpSession = HttpSessionManager.findSession(keyValue[1]);
                    if (httpSession != null && httpSession.getAttribute("user") != null) {
                        response.setStatusLine("HTTP/1.1", 302, "Found");
                        response.addHeader("Location", "/index.html");
                        return;
                    }
                }
            }
        }
        StaticController.handleStaticResource("/login.html", response);
    }

    @Override
    protected void doPost(HttpRequest request, HttpResponse response) throws Exception {
        String account = request.getQueryParams().get("account");
        String password = request.getQueryParams().get("password");

        boolean success = InMemoryUserRepository.findByAccount(account)
                .map(user -> user.checkPassword(password))
                .orElse(false);

        if (success) {
            User user = InMemoryUserRepository.findByAccount(account).get();
            HttpSession httpSession = HttpSessionManager.createSession();
            httpSession.setAttribute("user", user);
            response.setStatusLine("HTTP/1.1", 302, "Found");
            response.addHeader("Location", "/index.html");
            response.addHeader("Set-Cookie", "JSESSIONID=" + httpSession.getId());
        } else {
            response.setStatusLine("HTTP/1.1", 302, "Found");
            response.addHeader("Location", "/401.html");
        }
    }
}
