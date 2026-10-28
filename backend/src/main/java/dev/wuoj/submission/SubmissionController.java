package dev.wuoj.submission;

import java.io.IOException;
import java.net.URI;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.Verdict;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.ClientAddress;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import dev.wuoj.common.web.ProblemResponses;
import dev.wuoj.submission.SubmissionDtos.SubmissionDetail;
import dev.wuoj.submission.SubmissionDtos.SubmissionSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/submissions")
@ConditionalOnRole(Role.API)
public class SubmissionController {

    /** The contract's {@code SubmissionScope}. */
    public enum Scope {
        mine,
        all
    }

    /**
     * {@code language} is a string rather than the enum so that an unknown language is reported as a field error.
     * The source limit counts UTF-16 code units, the same as the web app's JavaScript {@code length}.
     */
    public record CreateSubmissionRequest(
            @NotNull Long problemId,
            @NotNull @Pattern(regexp = "CPP17", message = "must be CPP17") String language,
            @NotBlank @Size(max = 65536, message = "must be at most 64 KB (65,536 characters)") String sourceCode) {}

    private final SubmissionService service;
    private final SubmissionRepository submissions;
    private final StreamRegistry streams;
    private final ProblemResponses problems;

    public SubmissionController(SubmissionService service, SubmissionRepository submissions, StreamRegistry streams,
            ProblemResponses problems) {
        this.service = service;
        this.submissions = submissions;
        this.streams = streams;
        this.problems = problems;
    }

    @PostMapping
    ResponseEntity<SubmissionDetail> create(Viewer viewer, @Valid @RequestBody CreateSubmissionRequest body,
            HttpServletRequest request) {
        SubmissionDetail created = service.create(viewer, ClientAddress.of(request), body.problemId(),
                Language.valueOf(body.language()), body.sourceCode());
        return ResponseEntity.created(URI.create("/api/submissions/" + created.id())).body(created);
    }

    /** {@code scope=mine} needs a session; the security configuration lets visitors in for {@code scope=all}. */
    @GetMapping
    Page<SubmissionSummary> list(Viewer viewer,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageRequest.MAX_SIZE) int size,
            @RequestParam(defaultValue = "mine") Scope scope,
            @RequestParam(required = false) @Nullable String problem,
            @RequestParam(required = false) @Nullable String user,
            @RequestParam(required = false) @Nullable Verdict verdict) {
        if (scope == Scope.mine && !viewer.signedIn()) {
            throw new InsufficientAuthenticationException("Sign in to see your submissions.");
        }
        String slug = problem == null || problem.isBlank() ? null : problem.strip();
        String username = user == null || user.isBlank() ? null : user.strip();
        return submissions.list(viewer, scope == Scope.mine, slug, username, verdict, new PageRequest(page, size))
                .map(SubmissionSummary::of);
    }

    @GetMapping("/{id}")
    SubmissionDetail get(Viewer viewer, @PathVariable long id) {
        return service.detail(viewer, id);
    }

    /**
     * Live status events. Access is checked before the stream starts; a 404 is written directly, because clients of
     * this endpoint only accept {@code text/event-stream} and content negotiation would reject a problem body.
     */
    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Nullable SseEmitter events(Viewer viewer, @PathVariable long id, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (submissions.find(viewer, id).isEmpty()) {
            problems.write(request, response, HttpStatus.NOT_FOUND, "Submission not found.");
            return null;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return streams.open(id);
    }
}
