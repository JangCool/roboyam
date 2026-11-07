package com.community.menu.repository.master;

import com.community.menu.dto.AdminMenuNode;
import com.community.menu.entity.AdminMenuNodeEntity;
import com.community.menu.entity.AdminMenuStateEntity;
import com.community.menu.entity.QAdminMenuNodeEntity;
import com.community.menu.entity.QAdminMenuStateEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Repository;

/**
 * 관리자 사이트 메뉴 구성 표(admin 스트림 V2 의 {@code admin_menu_state}·{@code admin_menu_nodes}, 엔티티는 admin 모듈 {@code com.community.menu.entity}) 읽기·전체 교체.
 *
 * <p><b>트랜잭션은 부르는 서비스가 연다.</b> 노드는 엔티티가 아니라 칸을 골라 읽는다 — 전체 교체가 같은 ID 를 다시 넣으므로, 읽은 노드가 1차 캐시에 남아 있으면 안 된다.
 * 상태 행만 잠금 읽기로 엔티티를 읽고 {@link #saved} 가 그 엔티티를 고친다(변경 감지).
 *
 * <p>네이티브 SQL 로 남긴 것: 상태 기본 행 넣기({@code ON CONFLICT} — 사유는 메서드 주석).
 */
@Repository
public class AdminMenuQueries extends RoboyamRepositoryQDSL {

    private static final QAdminMenuStateEntity STATE = QAdminMenuStateEntity.adminMenuStateEntity;
    private static final QAdminMenuNodeEntity NODE = QAdminMenuNodeEntity.adminMenuNodeEntity;

    private final EntityManager entityManager;

    public AdminMenuQueries(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 구성 상태 — 버전과 마지막 저장 시각(저장한 적 없으면 null) */
    public record State(long version, Instant updatedAt) {
    }

    /** 상태 행을 읽는다(행이 없으면 버전 1·저장 안 함) */
    public State state() {
        Tuple t = query.select(STATE.version, STATE.updatedAt).from(STATE).where(STATE.id.eq(AdminMenuStateEntity.ROW_ID)).fetchOne();
        return t == null ? new State(1, null) : new State(t.get(STATE.version), t.get(STATE.updatedAt));
    }

    /**
     * 상태 행을 쓰기 잠금으로 읽는다({@code FOR NO KEY UPDATE}) — 같은 구성을 동시에 저장하는 요청을 줄 세운다(트랜잭션 안에서만 의미가 있다).
     * 행이 없으면(마이그레이션이 넣지만) 먼저 만든다.
     *
     * <p>기본 행 넣기가 Native SQL 인 이유: {@code INSERT ... ON CONFLICT DO NOTHING} 은 JPQL·QueryDSL 에 대응 문법이 없다(동시에 둘이 넣어도 하나만 생긴다).
     */
    public State lockState() {
        entityManager.createNativeQuery("INSERT INTO admin_menu_state (id) VALUES (1) ON CONFLICT (id) DO NOTHING").executeUpdate();
        RowLocks.forUpdate(entityManager, AdminMenuStateEntity.class, AdminMenuStateEntity.ROW_ID); // 전환 전 세기(FOR UPDATE)
        AdminMenuStateEntity row = query.selectFrom(STATE).where(STATE.id.eq(AdminMenuStateEntity.ROW_ID)).fetchOne();
        return row == null ? new State(1, null) : new State(row.getVersion(), row.getUpdatedAt());
    }

    /** 저장된 두 모드의 트리(모드 → 최상위 노드들, 차례대로) */
    public Map<String, List<AdminMenuNode>> load() {
        List<Row> rows = query.select(NODE.id, NODE.mode, NODE.parentId, NODE.kind, NODE.labelKey, NODE.label, NODE.labelEn, NODE.icon, NODE.visible, NODE.targetType,
                        NODE.targetKey, NODE.href, NODE.newTab, NODE.permission)
                .from(NODE).orderBy(NODE.sortOrder.asc(), NODE.id.asc()).fetch().stream()
                .map(AdminMenuQueries::row)
                .toList();
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
     * 두 모드를 통째로 바꾼다 — 지금 노드를 모두 지우고(한 문장) 차례대로 넣는다(부모 먼저 — 넣는 차례 그대로 나간다). 노드 ID 는 {@code idOf} 가 정한다(지금 있던 ID 재사용·새 발급).
     *
     * @return 모드 → ID 를 매긴 트리
     */
    public Map<String, List<AdminMenuNode>> replace(Map<String, List<AdminMenuNode>> modes, Function<AdminMenuNode, Long> idOf) {
        query.delete(NODE).execute();
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        modes.forEach((mode, nodes) -> out.put(mode, insert(mode, null, nodes, idOf)));
        return out;
    }

    /** 저장했다 — 버전을 올리고 저장한 사람·시각을 남긴다({@link #lockState} 로 잠근 행, 커밋 때 UPDATE). 행이 없으면 아무 일도 하지 않는다 */
    public void saved(long operatorId, Instant now) {
        AdminMenuStateEntity row = entityManager.find(AdminMenuStateEntity.class, AdminMenuStateEntity.ROW_ID);
        if (row != null) {
            row.markSaved(operatorId, now);
        }
    }

    private List<AdminMenuNode> insert(String mode, Long parentId, List<AdminMenuNode> nodes, Function<AdminMenuNode, Long> idOf) {
        List<AdminMenuNode> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            AdminMenuNode node = nodes.get(i);
            long id = idOf.apply(node);
            entityManager.persist(AdminMenuNodeEntity.create(id, mode, parentId, i, node));
            out.add(node.withIdAndChildren(id, insert(mode, id, node.children(), idOf)));
        }
        return out;
    }

    private static Row row(Tuple t) {
        long id = t.get(NODE.id);
        return new Row(id, t.get(NODE.mode), t.get(NODE.parentId),
                new AdminMenuNode(id, t.get(NODE.kind), t.get(NODE.labelKey), t.get(NODE.label), t.get(NODE.labelEn), t.get(NODE.icon),
                        Boolean.TRUE.equals(t.get(NODE.visible)), t.get(NODE.targetType), t.get(NODE.targetKey), t.get(NODE.href),
                        Boolean.TRUE.equals(t.get(NODE.newTab)), t.get(NODE.permission), List.of()));
    }

    private static AdminMenuNode build(Row row, Map<Long, List<Row>> byParent) {
        List<AdminMenuNode> children = byParent.getOrDefault(row.node().id(), List.of()).stream().map(child -> build(child, byParent)).toList();
        return row.node().withIdAndChildren(row.node().id(), children);
    }

    private record Row(long id, String mode, Long parentId, AdminMenuNode node) {
    }
}
