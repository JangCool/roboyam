package com.community.zconfiguration.jpa;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.metamodel.mapping.CompositeIdentifierMapping;
import org.hibernate.metamodel.mapping.EntityIdentifierMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 행 잠금을 PostgreSQL {@code FOR UPDATE} 세기로 거는 한 곳.
 *
 * <p>JPA {@code PESSIMISTIC_WRITE} 는 PostgreSQL 에서 {@code FOR NO KEY UPDATE} 다(Hibernate 방언은 설정으로 바꿀 수 없다) — 전환 전 {@code FOR UPDATE} 세기(그 행을 FK 로
 * 참조하는 INSERT 도 기다리게)를 지키려고 이 한 곳에서 네이티브로 잠근다. 부르는 쪽은 엔티티 클래스와 id 만 넘긴다 — 표·id 칸 이름은 Hibernate 메타모델(그 엔티티의
 * 식별자 칸)에서 읽으므로 부르는 쪽에 SQL 문자열이 없다.
 *
 * <ul>
 *   <li>문장: {@code SELECT <id칸> FROM <표> WHERE <id칸> IN (…) ORDER BY <id칸> FOR UPDATE} — 여러 행은 id 차례로 잠근다(같은 행들을 잡는 작업자끼리 교착하지 않는다).
 *       복합 키는 {@code (<칸1>, <칸2>) IN ((…), …)} 이고 id 값은 키 클래스({@code @IdClass}) 객체다.</li>
 *   <li>같은 트랜잭션의 연결에서 JDBC 로 낸다(영속성 문맥을 flush 하지 않는다 — 전환 전 JDBC 잠금과 같다). 트랜잭션 밖이면 {@link IllegalStateException}(잠금이 바로 풀린다).</li>
 *   <li>잠금만 건다. 행 내용은 잠근 뒤 따로 읽는다 — 잠근 행이 이미 영속성 문맥에 있으면 잠근 직후 DB 에서 다시 읽어 낡은 1차 캐시 값을 보지 않게 한다. 조건으로 고르던 잠금({@code WHERE status = … FOR UPDATE})은 「조건으로 id 고르기(잠금 없이) → 잠그기 → 조건을 넣어 다시
 *       읽기」 — PostgreSQL 이 잠근 뒤 조건을 다시 보는 것과 같은 결과다.</li>
 *   <li>{@code SKIP LOCKED} 판({@link #forUpdateSkipLocked})은 다른 트랜잭션이 잡고 있는 행(그 행을 FK 로 참조하며 넣는 중인 트랜잭션 포함)을 기다리지 않고 건너뛴다.</li>
 *   <li>처음부터 JPA 잠금이던 곳(파생 메서드의 {@code @Lock(PESSIMISTIC_WRITE)} 등)은 이것으로 바꾸지 않는다 — 전환 전 네이티브 {@code FOR UPDATE} 자리만 쓴다.</li>
 * </ul>
 */
public final class RowLocks {

    private static final ConcurrentMap<Class<?>, Target> TARGETS = new ConcurrentHashMap<>();

    private RowLocks() {
    }

    /** 한 행을 {@code FOR UPDATE} 로 잠근다 — 있으면 true. 복합 키면 {@code id} 는 키 클래스({@code @IdClass}) 객체 */
    public static boolean forUpdate(EntityManager entityManager, Class<?> entity, Object id) {
        return !forUpdate(entityManager, entity, List.of(id)).isEmpty();
    }

    /** 여러 행을 id 차례로 {@code FOR UPDATE} 로 잠근다 — 잠근(있는) id 를 id 차례로 돌려준다(넘긴 값 그대로) */
    public static <T> List<T> forUpdate(EntityManager entityManager, Class<?> entity, Collection<T> ids) {
        return lock(entityManager, entity, ids, "FOR UPDATE");
    }

    /** 한 행을 잠겨 있으면 건너뛰며 {@code FOR UPDATE SKIP LOCKED} 로 잠근다 — 잡았으면 true */
    public static boolean forUpdateSkipLocked(EntityManager entityManager, Class<?> entity, Object id) {
        return !forUpdateSkipLocked(entityManager, entity, List.of(id)).isEmpty();
    }

    /** 여러 행을 id 차례로 {@code FOR UPDATE SKIP LOCKED} 로 잠근다 — 잡은 id 를 id 차례로 돌려준다(다른 트랜잭션이 잡은 행·없는 행은 빠진다) */
    public static <T> List<T> forUpdateSkipLocked(EntityManager entityManager, Class<?> entity, Collection<T> ids) {
        return lock(entityManager, entity, ids, "FOR UPDATE SKIP LOCKED");
    }

    private static <T> List<T> lock(EntityManager entityManager, Class<?> entity, Collection<T> ids, String clause) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("행 잠금은 트랜잭션 안에서만 의미가 있다: " + entity.getSimpleName());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Target target = TARGETS.computeIfAbsent(entity, type -> Target.of(entityManager, type));
        List<T> keys = List.copyOf(ids);
        String sql = target.sql(keys.size(), clause);
        List<T> locked = entityManager.unwrap(Session.class).doReturningWork(connection -> run(connection, sql, target, keys));
        refreshManaged(entityManager, entity, locked);
        return locked;
    }

    /**
     * 잠근 행이 이미 영속성 문맥에 올라와 있으면 DB 에서 다시 읽는다 — 잠금을 기다리는 사이 다른 트랜잭션이 커밋한 값을 보게 한다(전환 전 JDBC 는 잠근 뒤 늘 새로 읽었다).
     * 올라와 있지 않은 행은 건드리지 않는다(뒤의 질의가 DB 에서 읽는다). 다시 읽기 전에 영속성 문맥에 내보내지 않은 변경이 있으면 먼저 내보낸다(잠금은 이미 쥐고 있다) —
     * 그래야 잠그기 전에 고쳐 둔 값이 다시 읽기로 버려지지 않는다.
     */
    private static void refreshManaged(EntityManager entityManager, Class<?> entity, List<?> locked) {
        if (locked.isEmpty()) {
            return;
        }
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        EntityPersister persister = session.getFactory().getMappingMetamodel().getEntityDescriptor(entity);
        List<Object> managed = new ArrayList<>();
        for (Object id : locked) {
            Object instance = session.getPersistenceContextInternal().getEntity(session.generateEntityKey(id, persister));
            if (instance != null) {
                managed.add(instance);
            }
        }
        if (managed.isEmpty()) {
            return;
        }
        if (session.isDirty()) {
            session.flush();
        }
        managed.forEach(entityManager::refresh);
    }

    private static <T> List<T> run(Connection connection, String sql, Target target, List<T> keys) throws SQLException {
        List<Object[]> parts = keys.stream().map(target::parts).toList();
        List<Object[]> locked = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object[] key : parts) {
                for (Object part : key) {
                    statement.setObject(index++, part);
                }
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Object[] row = new Object[target.columns.size()];
                    for (int i = 0; i < row.length; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    locked.add(row);
                }
            }
        }
        List<T> result = new ArrayList<>(locked.size());
        for (Object[] row : locked) {
            for (int i = 0; i < keys.size(); i++) {
                if (same(parts.get(i), row)) {
                    result.add(keys.get(i));
                    break;
                }
            }
        }
        return result;
    }

    private static boolean same(Object[] key, Object[] row) {
        for (int i = 0; i < key.length; i++) {
            Object a = key[i];
            Object b = row[i];
            boolean equal = a instanceof Number x && b instanceof Number y ? x.longValue() == y.longValue() : Objects.equals(a, b);
            if (!equal) {
                return false;
            }
        }
        return true;
    }

    /** 엔티티 하나의 표·식별자 칸(메타모델에서 읽는다). 복합 키면 칸마다 그 속성 이름도 둔다(키 객체에서 값을 꺼낸다) */
    private record Target(String table, List<String> columns, List<String> attributes) {

        static Target of(EntityManager entityManager, Class<?> entity) {
            SessionFactoryImplementor factory = entityManager.getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
            EntityPersister persister = factory.getMappingMetamodel().getEntityDescriptor(entity);
            EntityIdentifierMapping identifier = persister.getIdentifierMapping();
            List<String> columns = new ArrayList<>();
            List<String> attributes = new ArrayList<>();
            String[] table = new String[1];
            if (identifier instanceof CompositeIdentifierMapping composite) {
                composite.getPartMappingType().forEachAttributeMapping(attribute -> attribute.forEachSelectable((i, selectable) -> {
                    columns.add(selectable.getSelectionExpression());
                    attributes.add(attribute.getAttributeName());
                    table[0] = selectable.getContainingTableExpression();
                }));
            } else {
                identifier.forEachSelectable((i, selectable) -> {
                    columns.add(selectable.getSelectionExpression());
                    table[0] = selectable.getContainingTableExpression();
                });
            }
            if (columns.isEmpty() || table[0] == null) {
                throw new IllegalStateException("식별자 칸을 찾지 못했다: " + entity.getName());
            }
            return new Target(table[0], List.copyOf(columns), List.copyOf(attributes));
        }

        /** 키 하나를 칸 값들로 — 단일 키는 그 값, 복합 키는 키 클래스({@code @IdClass}) 객체(속성 이름으로 값을 꺼낸다) */
        Object[] parts(Object key) {
            if (columns.size() == 1) {
                return new Object[] {key};
            }
            Object[] parts = new Object[attributes.size()];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = field(key, attributes.get(i));
            }
            return parts;
        }

        private static Object field(Object key, String name) {
            for (Class<?> type = key.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(key);
                } catch (NoSuchFieldException e) {
                    // 부모 클래스에서 찾는다
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("복합 키 값을 읽지 못했다: " + key.getClass().getName() + "." + name, e);
                }
            }
            throw new IllegalArgumentException("복합 키 클래스에 속성이 없다: " + key.getClass().getName() + "." + name);
        }

        String sql(int count, String clause) {
            String cols = String.join(", ", columns);
            boolean single = columns.size() == 1;
            String one = single ? "?" : "(" + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
            String left = single ? cols : "(" + cols + ")";
            return "SELECT " + cols + " FROM " + table + " WHERE " + left + " IN (" + String.join(", ", Collections.nCopies(count, one)) + ") ORDER BY " + cols + " "
                    + clause;
        }
    }
}
