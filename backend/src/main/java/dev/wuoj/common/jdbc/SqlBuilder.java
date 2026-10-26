package dev.wuoj.common.jdbc;

import java.util.HashMap;
import java.util.Map;

/** Builds SQL with optional {@code WHERE} conditions and their named parameters. */
public final class SqlBuilder {

    private final StringBuilder sql;
    private final Map<String, Object> params = new HashMap<>();
    private boolean hasWhere;

    public SqlBuilder(String base) {
        this.sql = new StringBuilder(base);
    }

    public SqlBuilder where(String condition) {
        sql.append(hasWhere ? " AND " : " WHERE ").append(condition);
        hasWhere = true;
        return this;
    }

    public SqlBuilder where(String condition, String param, Object value) {
        params.put(param, value);
        return where(condition);
    }

    public SqlBuilder append(String text) {
        sql.append(' ').append(text);
        return this;
    }

    public SqlBuilder param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public String sql() {
        return sql.toString();
    }

    public Map<String, Object> params() {
        return Map.copyOf(params);
    }

    /** Escapes {@code \}, {@code %} and {@code _} so they match literally in {@code LIKE ... ESCAPE '\'}. */
    public static String likeContains(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
