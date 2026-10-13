package com.et.cloud.controller;

import com.et.cloud.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import javax.servlet.http.HttpServletRequest;

/** Browser entry: authenticate through cloud before opening the private Agent stream. */
@RestController
@RequestMapping("/agent")
public class AgentPreviewController {
    private final UserService users;
    private final AgentPreviewProxy proxy;
    public AgentPreviewController(UserService users, AgentPreviewProxy proxy) {
        this.users = users; this.proxy = proxy;
    }
    public static class Ask {
        public String query;
        public Ask() { }
        public Ask(String query) { this.query = query; }
    }
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody stream(@RequestBody Ask ask, HttpServletRequest request) {
        users.getLoginUser(request);
        if (ask == null || ask.query == null || ask.query.trim().isEmpty() || ask.query.length() > 4000)
            throw new IllegalArgumentException("Invalid question");
        return proxy.stream(ask.query);
    }
}
