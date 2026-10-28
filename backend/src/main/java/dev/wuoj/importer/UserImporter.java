package dev.wuoj.importer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.user.UserRepository;
import dev.wuoj.user.Usernames;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

/**
 * Imports accounts from CSV ({@code username,password,role[,enabled]}). All rows are checked first; if any is
 * invalid, nothing is applied. Passwords are hashed and never printed.
 */
@Service
public class UserImporter {

    private static final Set<String> REQUIRED = Set.of("username", "password", "role");
    private static final Set<String> KNOWN = Set.of("username", "password", "role", "enabled");

    public record Summary(int created, int updated) {}

    public static class InvalidCsvException extends Exception {
        private final List<String> errors;

        InvalidCsvException(List<String> errors) {
            super(String.join("\n", errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    private record Row(String username, String password, UserRole role, boolean enabled) {}

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;

    public UserImporter(UserRepository users, PasswordEncoder passwordEncoder, TransactionTemplate transactions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
    }

    public Summary importCsv(Path file) throws IOException, InvalidCsvException {
        List<Row> rows = parse(file);
        return transactions.execute(status -> {
            int created = 0;
            int updated = 0;
            for (Row row : rows) {
                String hash = passwordEncoder.encode(row.password());
                var existing = users.findByUsername(row.username());
                if (existing.isPresent()) {
                    users.update(existing.get().id(), hash, row.role(), row.enabled());
                    updated++;
                } else {
                    users.insert(row.username(), hash, row.role(), row.enabled());
                    created++;
                }
            }
            return new Summary(created, updated);
        });
    }

    private static List<Row> parse(Path file) throws IOException, InvalidCsvException {
        List<String> errors = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        CsvMapper mapper = CsvMapper.builder().build();
        try (MappingIterator<Map<String, String>> it = mapper.readerForMapOf(String.class)
                .with(CsvSchema.emptySchema().withHeader())
                .readValues(file.toFile())) {
            int line = 1;
            boolean headerChecked = false;
            while (it.hasNextValue()) {
                Map<String, String> values = it.nextValue();
                line++;
                if (!headerChecked) {
                    headerChecked = true;
                    Set<String> columns = values.keySet();
                    for (String column : REQUIRED) {
                        if (!columns.contains(column)) errors.add("line 1: missing column '" + column + "'");
                    }
                    for (String column : columns) {
                        if (!KNOWN.contains(column)) errors.add("line 1: unknown column '" + column + "'");
                    }
                    if (!errors.isEmpty()) throw new InvalidCsvException(errors);
                }
                Row row = row(values, line, errors);
                if (row != null && !seen.add(row.username().toLowerCase(Locale.ROOT))) {
                    errors.add("line " + line + ": username '" + row.username() + "' appears more than once");
                } else if (row != null) {
                    rows.add(row);
                }
            }
        } catch (JacksonException e) {
            errors.add("not a valid CSV file (" + e.getOriginalMessage() + ")");
        }
        if (rows.isEmpty() && errors.isEmpty()) errors.add("the file contains no users");
        if (!errors.isEmpty()) throw new InvalidCsvException(errors);
        return rows;
    }

    private static Row row(Map<String, String> values, int line, List<String> errors) {
        int before = errors.size();
        String username = values.getOrDefault("username", "").strip();
        String password = values.getOrDefault("password", "");
        String role = values.getOrDefault("role", "").strip();
        String enabled = values.getOrDefault("enabled", "").strip();
        String at = "line " + line + ": ";

        if (!Usernames.isValid(username)) {
            errors.add(at + "username '" + username + "' is invalid (1-64 letters, digits, '.', '_' or '-')");
        }
        if (password.isEmpty()) errors.add(at + "password is required");
        if (password.length() > 128) errors.add(at + "password must be at most 128 characters");
        UserRole parsedRole = null;
        try {
            parsedRole = UserRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            errors.add(at + "role '" + role + "' is invalid (USER or ADMIN)");
        }
        if (!enabled.isEmpty() && !enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) {
            errors.add(at + "enabled must be true or false");
        }
        if (errors.size() > before) return null;
        return new Row(username, password, parsedRole, !enabled.equalsIgnoreCase("false"));
    }
}
