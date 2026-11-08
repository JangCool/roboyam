package com.community.installation.repository.master;

import com.community.installation.dto.TablePack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 표 단위 행 묶음({@link TablePack} — 설치 프리셋의 구조 스냅숏·데모 팩)을 DB 에 넣는다.
 *
 * <p><b>네이티브 SQL 인 이유</b>(.claude/guides/backend/repository.md 「인정 사유」): 넣을 표가 데이터(팩)로 정해지는 표 단위 적재라 엔티티가 없다 —
 * PostgreSQL 의 {@code json_populate_recordset}(JSON → 그 표의 행 형식, 칸마다 DB 가 형변환), {@code ON CONFLICT} upsert, 사용자 트리거 끄기({@code ALTER TABLE … DISABLE TRIGGER USER} — DDL),
 * 시퀀스 맞추기({@code setval}), 글 월 조각 함수 호출을 쓴다. 표·칸 이름은 팩에서 오지만 늘 이 DB 의 카탈로그({@link #columns})에서 찾은 이름만 쓰고 따옴표로 감싼다.
 * 값은 JSON 한 덩어리를 바인딩 인자로 넘긴다(SQL 에 값을 이어 붙이지 않는다).
 *
 * <p>부르는 쪽(서비스)의 트랜잭션 안에서 돈다 — {@link JdbcTemplate} 은 스프링이 연 트랜잭션의 연결을 쓴다.
 */
@Repository
public class TablePackRepository {

    private final JdbcTemplate jdbc;

    public TablePackRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 표의 칸 하나 — {@code type} 은 {@code format_type} 글자(예: {@code bigint}, {@code timestamp with time zone}, {@code character varying(40)}) */
    public record Column(String name, String type, boolean generated) {

        public boolean isBigint() {
            return "bigint".equals(type);
        }

        public boolean isBigintArray() {
            return "bigint[]".equals(type);
        }

        /** 시각·날짜 칸(데모 팩이 시각을 옮긴다) */
        public boolean isTemporal() {
            return type.startsWith("timestamp") || "date".equals(type);
        }

        /** 글자·JSON 칸(안에 든 ID 를 옮긴다) */
        public boolean isTextual() {
            return type.startsWith("character varying") || "text".equals(type) || "jsonb".equals(type) || "json".equals(type)
                    || type.startsWith("character varying[]") || "text[]".equals(type);
        }
    }

    /** public 스키마의 그 표의 칸(차례대로). 표가 없으면 빈 맵 */
    public Map<String, Column> columns(String table) {
        Map<String, Column> columns = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod), a.attgenerated <> ''
                FROM pg_attribute a
                WHERE a.attrelid = to_regclass(quote_ident(?)) AND a.attnum > 0 AND NOT a.attisdropped
                ORDER BY a.attnum""",
                rs -> {
                    columns.put(rs.getString(1), new Column(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
                }, table);
        return columns;
    }

    /** 표를 비운다(행 삭제 — 트리거·FK 확인은 그대로 돈다). 지운 행 수 */
    public int deleteAll(String table) {
        return jdbc.update("DELETE FROM " + ident(table));
    }

    /**
     * 행들을 넣는다 — {@code json} 은 행 객체의 JSON 배열, {@code columns} 는 넣을 칸(이 DB 에 있는 이름만, 생성 칸 제외).
     *
     * @return 넣거나 고친 행 수
     */
    public int write(String table, TablePack.Mode mode, List<String> key, List<String> columns, String json) {
        String cols = columns.stream().map(TablePackRepository::ident).collect(Collectors.joining(", "));
        String source = "json_populate_recordset(NULL::" + ident(table) + ", ?::json)";
        if (mode == TablePack.Mode.UPDATE) {
            List<String> sets = new ArrayList<>();
            for (String column : columns) {
                if (!key.contains(column)) {
                    sets.add(ident(column) + " = r." + ident(column));
                }
            }
            if (sets.isEmpty()) {
                return 0;
            }
            String match = key.stream().map(k -> "d." + ident(k) + " = r." + ident(k)).collect(Collectors.joining(" AND "));
            return jdbc.update("UPDATE " + ident(table) + " AS d SET " + String.join(", ", sets) + " FROM " + source + " AS r WHERE " + match, json);
        }
        // OVERRIDING SYSTEM VALUE: GENERATED ALWAYS AS IDENTITY 칸이 있어도 팩의 값을 그대로 넣는다(없는 표에서는 아무 뜻이 없다)
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(ident(table)).append(" (").append(cols).append(") OVERRIDING SYSTEM VALUE SELECT ")
                .append(cols).append(" FROM ").append(source);
        if (mode == TablePack.Mode.UPSERT) {
            String conflict = key.stream().map(TablePackRepository::ident).collect(Collectors.joining(", "));
            List<String> sets = columns.stream().filter(c -> !key.contains(c)).map(c -> ident(c) + " = EXCLUDED." + ident(c)).toList();
            sql.append(" ON CONFLICT (").append(conflict).append(") ").append(sets.isEmpty() ? "DO NOTHING" : "DO UPDATE SET " + String.join(", ", sets));
        }
        return jdbc.update(sql.toString(), json);
    }

    /** 이 표들 가운데 사용자 트리거(내부 FK 트리거가 아닌 것)가 있는 표 */
    public List<String> withUserTriggers(Collection<String> tables) {
        if (tables.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT DISTINCT c.relname FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
                WHERE NOT t.tgisinternal AND c.relnamespace = 'public'::regnamespace AND c.relname = ANY(?::text[])""",
                String.class, (Object) tables.toArray(String[]::new));
    }

    /**
     * 사용자 트리거를 끄거나 켠다 — 팩의 행은 트리거가 이미 만든 결과(원장·잠금 표시 등)라 다시 돌면 안 되고, 「덧붙이기만」 같은 지킴 트리거가 고치기(UPSERT)를 막는다.
     * 트랜잭션 안의 DDL 이라 롤백되면 원래대로다. 표 주인 권한이 필요하다(앱 계정이 마이그레이션으로 만든 표의 주인).
     */
    public void setUserTriggers(String table, boolean enabled) {
        jdbc.execute("ALTER TABLE " + ident(table) + (enabled ? " ENABLE" : " DISABLE") + " TRIGGER USER");
    }

    /** 미뤄 둔 FK 확인(DEFERRABLE — 로보티콘 팩 ↔ 판)을 지금 한다 — 트리거를 다시 켜는 DDL 은 확인이 밀려 있으면 막힌다(55006) */
    public void checkDeferredNow() {
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
    }

    /** 이 표의 시퀀스 칸(serial·identity)을 지금 가장 큰 값 다음으로 맞춘다 — 팩이 넣은 번호와 앞으로 매길 번호가 겹치지 않게 */
    public void resetSequences(String table, Collection<String> columns) {
        for (String column : columns) {
            String sequence = jdbc.queryForObject("SELECT pg_get_serial_sequence(quote_ident(?), ?)", String.class, table, column);
            if (sequence != null) {
                jdbc.queryForObject("SELECT setval(?::regclass, coalesce((SELECT max(" + ident(column) + ") FROM " + ident(table) + "), 0) + 1, false)",
                        Long.class, sequence);
            }
        }
    }

    /** 글 월 조각을 지난달 ~ 6개월 뒤까지 맞춘다(R__batch_17 의 함수 — 서버 기동 때와 같다). 글은 Snowflake ID 범위로 조각에 들어간다 */
    public void ensurePostPartitions() {
        jdbc.queryForObject("SELECT board_ensure_post_partitions(6)", Integer.class);
    }

    private static String ident(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
