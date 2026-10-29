package dev.wuoj.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * A browser-like client: keeps the cookies the server sets and, like the web app, echoes the {@code XSRF-TOKEN}
 * cookie as {@code X-XSRF-TOKEN} on state-changing requests. Every exchange is checked against the contract.
 */
public class ApiSession {

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private boolean sendCsrfHeader = true;

    public ApiSession(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Like the web app on startup: fetch the current user, which issues the CSRF cookie. */
    public ApiSession start() throws Exception {
        perform(get("/api/auth/me"));
        return this;
    }

    public ApiSession signIn(String username, String password) throws Exception {
        start();
        perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
        return this;
    }

    public ApiSession withoutCsrfHeader() {
        sendCsrfHeader = false;
        return this;
    }

    public ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return exchange(request, true);
    }

    /** For requests that are invalid on purpose: only the response is checked against the contract. */
    public ResultActions performInvalid(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return exchange(request, false);
    }

    private ResultActions exchange(AbstractMockHttpServletRequestBuilder<?> request, boolean validateRequest)
            throws Exception {
        if (!cookies.isEmpty()) request.cookie(cookies.values().toArray(Cookie[]::new));
        String token = cookie("XSRF-TOKEN");
        if (sendCsrfHeader && token != null) request.header("X-XSRF-TOKEN", token);
        ResultActions actions = mvc.perform(request)
                .andExpect(validateRequest ? Contract.conforms() : Contract.responseConforms());
        remember(actions.andReturn());
        return actions;
    }

    private void remember(MvcResult result) {
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), new Cookie(cookie.getName(), cookie.getValue()));
            }
        }
    }

    public @Nullable String cookie(String name) {
        Cookie cookie = cookies.get(name);
        return cookie == null ? null : cookie.getValue();
    }

    public void setCookie(String name, String value) {
        cookies.put(name, new Cookie(name, value));
    }
}
