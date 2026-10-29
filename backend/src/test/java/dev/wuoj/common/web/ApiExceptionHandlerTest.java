package dev.wuoj.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.model.Request.Method;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.support.Contract;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Each error mapping produces an RFC 9457 body that matches the contract's {@code ApiProblem}, checked against an
 * operation that documents that status.
 */
class ApiExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Test
    void parameterValidationNamesTheParameter() throws Exception {
        MvcResult result = mvc.perform(get("/t/page").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.GET, "/api/problems");

        mvc.perform(get("/t/page").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void typeMismatchIsBadRequest() throws Exception {
        MvcResult result = mvc.perform(get("/t/page").param("difficulty", "INSANE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("difficulty"))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.GET, "/api/problems");
    }

    @Test
    void bodyValidationListsEveryField() throws Exception {
        MvcResult result = mvc
                .perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"sourceCode\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("The request is invalid."))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "sourceCode", "problemId")))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.POST, "/api/submissions");
    }

    @Test
    void unreadableBodyIsBadRequest() throws Exception {
        MvcResult result = mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The request body is not valid JSON."))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.POST, "/api/submissions");
    }

    @Test
    void notFound() throws Exception {
        MvcResult result = mvc.perform(get("/t/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Problem not found."))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.GET, "/api/problems/{slug}");
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        MvcResult result = mvc.perform(get("/t/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.GET, "/api/problems/{slug}");
    }

    @Test
    void rateLimitedHasRetryAfter() throws Exception {
        MvcResult result = mvc.perform(get("/t/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.detail").value("Too many submissions. Try again in 7 seconds."))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.POST, "/api/submissions");
    }

    @Test
    void oversizedUploadsNameTheLimit() throws Exception {
        MvcResult result = mvc.perform(get("/t/too-many-tests"))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A problem can have at most 1000 tests."))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.POST, "/api/admin/problems/1/tests");

        result = mvc.perform(get("/t/multipart-limit"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.UPLOAD_TOO_LARGE))
                .andReturn();
        Contract.assertResponseConformsAs(result, Method.POST, "/api/admin/problems");
    }

    @Test
    void unexpectedErrorIsAProblemWithoutDetails() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    /** Nested in a test class, so Boot's test type filter keeps it out of the application context. */
    @RestController
    @RequestMapping("/t")
    static class Endpoints {

        @GetMapping("/page")
        String page(@RequestParam(defaultValue = "1") @Min(1) int page,
                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                @RequestParam(required = false) @Nullable Difficulty difficulty) {
            return "ok";
        }

        record Body(@NotBlank String sourceCode, @NotNull Long problemId) {}

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/missing")
        String missing() {
            throw new NotFoundException("Problem not found.");
        }

        @GetMapping("/limited")
        String limited() {
            throw new RateLimitedException(7, "Too many submissions. Try again in 7 seconds.");
        }

        @GetMapping("/too-many-tests")
        String tooManyTests() {
            throw new PayloadTooLargeException("A problem can have at most 1000 tests.");
        }

        @GetMapping("/multipart-limit")
        String multipartLimit() {
            throw new MaxUploadSizeExceededException(64L * 1024 * 1024);
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("bug");
        }
    }
}
