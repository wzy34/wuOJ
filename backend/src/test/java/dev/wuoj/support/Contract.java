package dev.wuoj.support;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Checks requests and responses against {@code api/openapi.yaml}, the contract shared with the web app, and records
 * which operations the tests exercised.
 */
public final class Contract {

    public static final Path SPEC = Path.of("../api/openapi.yaml").toAbsolutePath().normalize();

    /** Additional properties are rejected by default, so undocumented response fields fail the tests. */
    public static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(SPEC.toUri().toString())
            .withLevelResolver(LevelResolver.create()
                    // Requests without a session are sent on purpose to check the 401 responses.
                    .withLevel("validation.request.security.missing", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    private static final OpenAPI API = new OpenAPIV3Parser().read(SPEC.toString());
    private static final Map<String, Pattern> OPERATIONS = operations();
    private static final Set<String> EXERCISED = ConcurrentHashMap.newKeySet();

    private Contract() {}

    /** The request and the response both conform to the contract. */
    public static ResultMatcher conforms() {
        return result -> check(result, true);
    }

    /** Only the response conforms; for requests that are invalid on purpose (e.g. {@code size=500}). */
    public static ResultMatcher responseConforms() {
        return result -> check(result, false);
    }

    private static void check(MvcResult result, boolean includeRequest) throws Exception {
        Request request = MockMvcRequest.of(result.getRequest());
        var response = MockMvcResponse.of(result.getResponse());
        ValidationReport report = includeRequest
                ? VALIDATOR.validate(request, response)
                : VALIDATOR.validateResponse(request.getPath(), request.getMethod(), response);
        record(request.getMethod().name(), request.getPath());
        if (report.hasErrors()) {
            fail("Contract violation for %s %s -> %d:%n%s%nBody: %s", request.getMethod(), request.getPath(),
                    response.getStatus(), report.getMessages().stream()
                            .map(m -> "  - " + m.getKey() + ": " + m.getMessage())
                            .collect(Collectors.joining("\n")),
                    result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Checks a response as if it had been returned by the given contract operation. */
    public static void assertResponseConformsAs(MvcResult result, Request.Method method, String path) {
        ValidationReport report = VALIDATOR.validateResponse(path, method, MockMvcResponse.of(result.getResponse()));
        if (report.hasErrors()) {
            fail("Response does not conform to %s %s: %s", method, path, report.getMessages());
        }
    }

    /** Records a request made outside MockMvc (e.g. the real-port SSE tests). */
    public static void record(String method, String path) {
        OPERATIONS.forEach((operation, pattern) -> {
            if (pattern.matcher(method + " " + path).matches()) EXERCISED.add(operation);
        });
    }

    public static Set<String> operationIds() {
        return OPERATIONS.keySet();
    }

    public static Set<String> exercised() {
        return Set.copyOf(EXERCISED);
    }

    private static Map<String, Pattern> operations() {
        Map<String, Pattern> operations = new ConcurrentHashMap<>();
        API.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String regex = method.name() + " " + path.replaceAll("\\{[^}]+}", "[^/]+");
            operations.put(operation.getOperationId(), Pattern.compile(regex));
        }));
        return operations;
    }
}
