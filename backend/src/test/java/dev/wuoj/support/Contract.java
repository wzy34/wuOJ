package dev.wuoj.support;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcRequest;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.MessageResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.schema.SchemaValidator;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final MessageResolver MESSAGES = new MessageResolver();
    private static final SchemaValidator SCHEMAS = new SchemaValidator(API, MESSAGES);
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
        ValidationReport parts = ValidationReport.empty();
        if (result.getRequest() instanceof MockMultipartHttpServletRequest multipart) {
            parts = includeRequest ? validateParts(request, multipart) : parts;
            request = withoutFormFields(request, multipart);
        }
        var response = MockMvcResponse.of(result.getResponse());
        ValidationReport report = includeRequest
                ? VALIDATOR.validate(request, response).merge(parts)
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

    /**
     * The validator doesn't look inside multipart bodies, and the MockMvc adapter reports form fields as query
     * parameters and sends no body. So the request is validated as having a body and only its real query parameters,
     * and {@link #validateParts} checks the fields against the operation's form schema.
     */
    private static Request withoutFormFields(Request request, MockMultipartHttpServletRequest multipart) {
        SimpleRequest.Builder builder = new SimpleRequest.Builder(request.getMethod(), request.getPath())
                .withBody("multipart body")
                .withContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.getHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) builder.withHeader(name, List.copyOf(values));
        });
        queryParameters(multipart).forEach(builder::withQueryParam);
        return builder.build();
    }

    /** Each form field must be a property of the form schema; JSON fields must match their property's schema. */
    private static ValidationReport validateParts(Request request, MockMultipartHttpServletRequest multipart) {
        Schema<?> form = formSchema(request);
        if (form == null) return report("validation.request.multipart.unexpected", "Not a multipart operation");
        var properties = form.getProperties();
        Map<String, List<String>> query = queryParameters(multipart);
        ValidationReport report = ValidationReport.empty();
        Set<String> sent = new HashSet<>(multipart.getMultiFileMap().keySet());
        for (Map.Entry<String, String[]> field : multipart.getParameterMap().entrySet()) {
            if (query.containsKey(field.getKey())) continue;
            sent.add(field.getKey());
            Schema<?> property = properties.get(field.getKey());
            if (property == null) {
                report = report.merge(report("validation.request.multipart.unexpected",
                        "Form field '" + field.getKey() + "' is not in the contract"));
            } else if (property.get$ref() != null) {
                for (String value : field.getValue()) {
                    report = report.merge(SCHEMAS.validate(value, property, "request.body." + field.getKey()));
                }
            }
        }
        for (String name : multipart.getMultiFileMap().keySet()) {
            if (!properties.containsKey(name)) {
                report = report.merge(report("validation.request.multipart.unexpected",
                        "File part '" + name + "' is not in the contract"));
            }
        }
        for (String required : form.getRequired() == null ? List.<String>of() : form.getRequired()) {
            if (!sent.contains(required)) {
                report = report.merge(report("validation.request.multipart.missing",
                        "Required form field '" + required + "' is missing"));
            }
        }
        return report;
    }

    private static @Nullable Schema<?> formSchema(Request request) {
        for (var path : API.getPaths().entrySet()) {
            String regex = path.getKey().replaceAll("\\{[^}]+}", "[^/]+");
            if (!request.getPath().matches(regex)) continue;
            Operation operation = path.getValue().readOperationsMap()
                    .get(PathItem.HttpMethod.valueOf(request.getMethod().name()));
            if (operation == null || operation.getRequestBody() == null) return null;
            io.swagger.v3.oas.models.media.MediaType media = operation.getRequestBody().getContent().get(MediaType.MULTIPART_FORM_DATA_VALUE);
            if (media == null) return null;
            String ref = media.getSchema().get$ref();
            return ref == null ? media.getSchema()
                    : API.getComponents().getSchemas().get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        return null;
    }

    private static Map<String, List<String>> queryParameters(MockMultipartHttpServletRequest multipart) {
        String query = multipart.getQueryString();
        if (query == null) return Map.of();
        return UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams();
    }

    private static ValidationReport report(String key, String message) {
        return ValidationReport.singleton(MESSAGES.create(key, message));
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
