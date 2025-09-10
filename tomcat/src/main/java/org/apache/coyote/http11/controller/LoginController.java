package org.apache.coyote.http11.controller;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.model.User;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;
import org.apache.coyote.http11.Session;
import org.apache.coyote.http11.SessionManager;

public class LoginController extends AbstractController {

    @Override
    protected void doGet(HttpRequest request, HttpResponse response) throws Exception {
        String cookie = request.getHeaders().get("Cookie");
        if (cookie != null) {
            for (String c : cookie.split(";")) {
                String[] keyValue = c.trim().split("=", 2);
                if (keyValue.length == 2 && "JSESSIONID".equals(keyValue[0])) {
                    Session session = SessionManager.findSession(keyValue[1]);
                    if (session != null && session.getAttribute("user") != null) {
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
            Session session = SessionManager.createSession();
            session.setAttribute("user", user);
            response.setStatusLine("HTTP/1.1", 302, "Found");
            response.addHeader("Location", "/index.html");
            response.addHeader("Set-Cookie", "JSESSIONID=" + session.getId());
        } else {
            response.setStatusLine("HTTP/1.1", 302, "Found");
            response.addHeader("Location", "/401.html");
        }
    }
}
