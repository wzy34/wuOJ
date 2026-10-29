package dev.wuoj.importer;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs {@code import-problems [--force] <dir>} or {@code import-users <csv>} when the application is started as a
 * command (see {@code WuojApplication}), then lets the process exit with the command's status. {@code --force}
 * overwrites problems that were edited in the web app since their last import.
 */
@Component
@ConditionalOnProperty("oj.command")
public class ImportCommand implements ApplicationRunner, ExitCodeGenerator {

    public static final Set<String> COMMANDS = Set.of("import-problems", "import-users");

    private final ProblemImporter problemImporter;
    private final UserImporter userImporter;
    private final PrintStream out = System.out;
    private int exitCode;

    public ImportCommand(ProblemImporter problemImporter, UserImporter userImporter) {
        this.problemImporter = problemImporter;
        this.userImporter = userImporter;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> positional = args.getNonOptionArgs();
        if (positional.size() != 2 || !COMMANDS.contains(positional.get(0))) {
            out.println("Usage: import-problems [--force] <package or directory of packages>"
                    + " | import-users <users.csv>");
            exitCode = 2;
            return;
        }
        Path path = Path.of(positional.get(1)).toAbsolutePath().normalize();
        exitCode = positional.get(0).equals("import-problems")
                ? importProblems(path, args.containsOption("force"))
                : importUsers(path);
    }

    private int importProblems(Path path, boolean force) throws Exception {
        List<ProblemImporter.Report> reports = problemImporter.importPath(path, force);
        int rejected = 0;
        for (ProblemImporter.Report report : reports) {
            out.printf("%-10s %s%n", report.outcome(), report.name());
            for (String error : report.errors()) out.println("           - " + error);
            if (report.outcome() == ProblemImporter.Outcome.REJECTED) rejected++;
        }
        out.printf("%d package(s): %d rejected%n", reports.size(), rejected);
        return rejected == 0 && !reports.isEmpty() ? 0 : 1;
    }

    private int importUsers(Path path) throws Exception {
        try {
            UserImporter.Summary summary = userImporter.importCsv(path);
            out.printf("%d created, %d updated%n", summary.created(), summary.updated());
            return 0;
        } catch (UserImporter.InvalidCsvException e) {
            out.println("No users were imported:");
            for (String error : e.errors()) out.println("  - " + error);
            return 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
