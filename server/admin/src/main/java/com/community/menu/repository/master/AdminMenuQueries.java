package com.community.menu.repository.master;

import com.community.menu.dto.AdminMenuNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 사이트 메뉴 구성 표(admin 스트림 V2 의 {@code admin_menu_state}·{@code admin_menu_nodes}) 읽기·전체 교체.
 *
 * <p>JDBC(native SQL)인 이유: 이 표는 admin 서버만 쓰는 admin 스트림의 표다 — 엔티티·Q 클래스는 shared 모듈에서만 만들어지고(admin 모듈에는 QueryDSL 처리기가 없다),
 * shared 에 엔티티를 두면 이 표가 없는 공개 서버(api)의 엔티티 목록에도 들어간다. 저장은 트리 전체 교체뿐이라 행 매핑이 단순하다.
 * 트랜잭션은 부르는 서비스의 것을 따른다(같은 DataSource).
 */
@Repository
public class AdminMenuQueries {

    private final JdbcTemplate jdbc;

    public AdminMenuQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 구성 상태 — 버전과 마지막 저장 시각(저장한 적 없으면 null) */
    public record State(long version, Instant updatedAt) {
    }

    /** 상태 행을 읽는다(행이 없으면 버전 1·저장 안 함) */
    public State state() {
        return read("");
    }

    /**
     * 상태 행을 쓰기 잠금으로 읽는다 — 같은 구성을 동시에 저장하는 요청을 줄 세운다(트랜잭션 안에서만 의미가 있다). 행이 없으면(마이그레이션이 넣지만) 먼저 만든다
     */
    public State lockState() {
        jdbc.update("INSERT INTO admin_menu_state (id) VALUES (1) ON CONFLICT (id) DO NOTHING");
        return read(" FOR UPDATE");
    }

    private State read(String suffix) {
        List<State> rows = jdbc.query("SELECT version, updated_at FROM admin_menu_state WHERE id = 1" + suffix, (rs, i) -> {
            Timestamp at = rs.getTimestamp(2);
            return new State(rs.getLong(1), at == null ? null : at.toInstant());
        });
        return rows.isEmpty() ? new State(1, null) : rows.get(0);
    }

    /** 저장된 두 모드의 트리(모드 → 최상위 노드들, 차례대로) */
    public Map<String, List<AdminMenuNode>> load() {
        List<Row> rows = jdbc.query(
                "SELECT id, mode, parent_id, kind, label_key, label, label_en, icon, visible, target_type, target_key, href, new_tab, permission "
                        + "FROM admin_menu_nodes ORDER BY sort_order, id",
                (rs, i) -> new Row(rs.getLong("id"), rs.getString("mode"), (Long) rs.getObject("parent_id", Long.class),
                        new AdminMenuNode(rs.getLong("id"), rs.getString("kind"), rs.getString("label_key"), rs.getString("label"), rs.getString("label_en"),
                                rs.getString("icon"), rs.getBoolean("visible"), rs.getString("target_type"), rs.getString("target_key"), rs.getString("href"),
                                rs.getBoolean("new_tab"), rs.getString("permission"), List.of())));
        Map<Long, List<Row>> byParent = new LinkedHashMap<>();
        for (Row row : rows) {
            byParent.computeIfAbsent(row.parentId(), key -> new ArrayList<>()).add(row);
        }
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        for (Row top : byParent.getOrDefault(null, List.of())) {
            out.computeIfAbsent(top.mode(), key -> new ArrayList<>()).add(build(top, byParent));
        }
        return out;
    }

    /**
     * 두 모드를 통째로 바꾼다 — 지금 노드를 모두 지우고 차례대로 넣는다. 노드 ID 는 {@code idOf} 가 정한다(지금 있던 ID 재사용·새 발급).
     *
     * @return 모드 → ID 를 매긴 트리
     */
    public Map<String, List<AdminMenuNode>> replace(Map<String, List<AdminMenuNode>> modes, Function<AdminMenuNode, Long> idOf) {
        jdbc.update("DELETE FROM admin_menu_nodes");
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        modes.forEach((mode, nodes) -> out.put(mode, insert(mode, null, nodes, idOf)));
        return out;
    }

    /** 저장했다 — 버전을 올리고 저장한 사람·시각을 남긴다 */
    public void saved(long operatorId, Instant now) {
        jdbc.update("UPDATE admin_menu_state SET version = version + 1, updated_at = ?, updated_by = ? WHERE id = 1", Timestamp.from(now), operatorId);
    }

    private List<AdminMenuNode> insert(String mode, Long parentId, List<AdminMenuNode> nodes, Function<AdminMenuNode, Long> idOf) {
        List<AdminMenuNode> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            AdminMenuNode node = nodes.get(i);
            long id = idOf.apply(node);
            jdbc.update("INSERT INTO admin_menu_nodes (id, mode, parent_id, kind, sort_order, label_key, label, label_en, icon, visible, target_type, target_key, "
                            + "href, new_tab, permission) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, mode, parentId, node.kind(), i, node.labelKey(), node.label(), node.labelEn(), node.icon(), node.visible(), node.targetType(),
                    node.targetKey(), node.href(), node.newTab(), node.permission());
            out.add(node.withIdAndChildren(id, insert(mode, id, node.children(), idOf)));
        }
        return out;
    }

    private static AdminMenuNode build(Row row, Map<Long, List<Row>> byParent) {
        List<AdminMenuNode> children = byParent.getOrDefault(row.node().id(), List.of()).stream().map(child -> build(child, byParent)).toList();
        return row.node().withIdAndChildren(row.node().id(), children);
    }

    private record Row(long id, String mode, Long parentId, AdminMenuNode node) {
    }
}
