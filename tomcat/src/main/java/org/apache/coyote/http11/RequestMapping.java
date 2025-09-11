package org.apache.coyote.http11;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.coyote.http11.controller.Controller;
import org.apache.coyote.http11.controller.LoginController;
import org.apache.coyote.http11.controller.RegisterController;
import org.apache.coyote.http11.controller.RootController;
import org.apache.coyote.http11.controller.StaticController;

public class RequestMapping {

    private final Map<String, Controller> controllers = new HashMap<>();

    public RequestMapping() {
        controllers.put("/", new RootController());
        controllers.put("/login", new LoginController());
        controllers.put("/register", new RegisterController());
    }

    public Controller getController(HttpRequest request) {
        Controller controller = controllers.get(request.getRequestLine().getPath());
        if (controller != null) {
            return controller;
        }

        return (req, res) -> {
            try {
                StaticController.handleStaticResource(req.getRequestLine().getPath(), res);
            } catch (IOException e) {
                res.setStatusLine("HTTP/1.1", 500, "Internal Server Error");
                res.addHeader("Content-Type", "text/plain;charset=utf-8");
                res.setBody("Static resource handling failed".getBytes());
            }
        };
    }
}
