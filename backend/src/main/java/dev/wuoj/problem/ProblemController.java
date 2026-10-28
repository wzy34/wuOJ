package dev.wuoj.problem;

import java.util.List;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.common.domain.ProblemUserStatus;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.NotFoundException;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
@ConditionalOnRole(Role.API)
public class ProblemController {

    public record ProblemSummary(long id, String displayId, String slug, String title, Difficulty difficulty,
            ProblemUserStatus status) {

        static ProblemSummary of(ProblemRow row) {
            return new ProblemSummary(row.id(), row.displayId(), row.slug(), row.title(), row.difficulty(),
                    row.status());
        }
    }

    public record ProblemDetail(long id, String displayId, String slug, String title, Difficulty difficulty,
            ProblemUserStatus status, int timeLimitMs, int memoryLimitMb, String statement, List<Sample> samples,
            List<Language> languages) {}

    private final ProblemRepository problems;

    public ProblemController(ProblemRepository problems) {
        this.problems = problems;
    }

    @GetMapping
    Page<ProblemSummary> list(Viewer viewer,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageRequest.MAX_SIZE) int size,
            @RequestParam(required = false) @Size(max = 100) @Nullable String q,
            @RequestParam(required = false) @Nullable Difficulty difficulty) {
        String query = q == null || q.isBlank() ? null : q.strip();
        return problems.list(viewer, query, difficulty, new PageRequest(page, size)).map(ProblemSummary::of);
    }

    @GetMapping("/{slug}")
    ProblemDetail get(Viewer viewer, @PathVariable String slug) {
        ProblemRow row = problems.findBySlug(viewer, slug)
                .orElseThrow(() -> new NotFoundException("Problem not found."));
        return new ProblemDetail(row.id(), row.displayId(), row.slug(), row.title(), row.difficulty(), row.status(),
                row.timeLimitMs(), row.memoryLimitMb(), row.statement(), problems.samples(row.id()),
                List.of(Language.values()));
    }
}
