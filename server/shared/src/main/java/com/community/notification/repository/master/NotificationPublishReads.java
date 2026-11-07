package com.community.notification.repository.master;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 알림 발행기({@code NotificationPublisher})가 <b>JDBC 세이브포인트 안에서</b> 쓰는 읽기·쓰기 — 이벤트 설정 한 줄, 받는 사람(활성 운영자·권한 보유자·최고 관리자·계정 있는 운영자),
 * 개인 끄기, 이메일 끈 운영자, 묶음 중복, 알림 행 넣기.
 *
 * <p>Native SQL(JDBC)인 이유: 세이브포인트 안에서 JPA 질의(QueryDSL·Spring Data)가 실패하면 Hibernate·스프링이 <b>업무 트랜잭션 전체를 롤백 전용으로</b> 표시해, 세이브포인트로
 * 되돌려도 업무 커밋이 실패한다(2026-11-07 실측 — 「알림 실패가 업무를 깨지 않는다」가 깨진다). 같은 연결의 JDBC 는 실패해도 세이브포인트로 되돌리면 끝난다.
 * 조건은 세이브포인트 밖에서 쓰는 {@link NotificationRecipientRepositoryQDSL} 와 같다 — 활성 조건을 바꾸면 {@link NotificationRecipientRepositoryQDSL#isActive} 도 같이 바꾼다.
 * <b>트랜잭션은 부르는 쪽이 연다</b>(업무 트랜잭션의 같은 연결에 참여한다).
 */
@Repository
public class NotificationPublishReads {

    /** 지금 로그인할 수 있는 운영자 — {@link NotificationRecipientRepositoryQDSL#isActive} 와 같은 조건(별칭 {@code o}, 인자 {@code :now}) */
    private static final String ACTIVE = "o.approved and not o.locked and not o.expired and o.enabled "
            + "and not ((o.account_start_at is not null and o.account_start_at > :now) or (o.account_end_at is not null and o.account_end_at < :now))";

    /** 이벤트 관리 화면이 저장한 값 한 줄({@code notification_events}) */
    public record EventRow(boolean enabled, boolean emailEnabled, boolean browserEnabled, boolean defaultRecipients) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public NotificationPublishReads(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    /** 이벤트 관리에서 저장한 값 — 행이 없으면(맞춤 전) 비어 있다 */
    public Optional<EventRow> eventRow(String eventCode) {
        List<EventRow> rows = jdbc.query("select enabled, email_enabled, browser_enabled, default_recipients from notification_events where code = ?",
                (rs, n) -> new EventRow(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4)), eventCode);
        return rows.stream().findFirst();
    }

    /** 권한 코드 중 하나라도 가진 활성 운영자 + 활성 최고 관리자 */
    public Set<Long> activeHolders(Collection<String> permissionCodes, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", at(now));
        String holds = "o.is_root";
        if (!permissionCodes.isEmpty()) {
            holds = "(o.is_root or exists (select 1 from operator_roles r join role_permissions p on p.role_id = r.role_id "
                    + "where r.operator_id = o.id and p.permission_code in (:codes)))";
            params.addValue("codes", permissionCodes);
        }
        return ids("select o.id from operators o where " + ACTIVE + " and " + holds + " order by o.id", params);
    }

    /** 활성 최고 관리자 */
    public Set<Long> activeRoots(Instant now) {
        return ids("select o.id from operators o where " + ACTIVE + " and o.is_root order by o.id", new MapSqlParameterSource("now", at(now)));
    }

    /** 활성 운영자 전원(루트 포함) */
    public Set<Long> allActive(Instant now) {
        return ids("select o.id from operators o where " + ACTIVE + " order by o.id", new MapSqlParameterSource("now", at(now)));
    }

    /** 그 운영자들 중 활성인 사람 */
    public Set<Long> active(Collection<Long> operatorIds, Instant now) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return ids("select o.id from operators o where o.id in (:ids) and " + ACTIVE + " order by o.id",
                new MapSqlParameterSource("now", at(now)).addValue("ids", operatorIds));
    }

    /** 그 운영자들 중 계정이 있는 사람(상태와 상관없이) */
    public Set<Long> existing(Collection<Long> operatorIds) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return ids("select o.id from operators o where o.id in (:ids) order by o.id", new MapSqlParameterSource("ids", operatorIds));
    }

    /** 그 이벤트를 개인 설정에서 끈 운영자 중 {@code operatorIds} 안에 있는 사람 */
    public Set<Long> muted(String eventCode, Collection<Long> operatorIds) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return ids("select operator_id from operator_notification_mutes where event_code = :code and operator_id in (:ids)",
                new MapSqlParameterSource("code", eventCode).addValue("ids", operatorIds));
    }

    /** 이메일 알림을 끈({@code email_notification = false}) 운영자 — 설정 행이 없으면 처음 값(켜짐)이라 여기에 없다 */
    public Set<Long> emailOptedOut(Collection<Long> operatorIds) {
        if (operatorIds.isEmpty()) {
            return Set.of();
        }
        return ids("select operator_id from operator_preferences where operator_id in (:ids) and not email_notification", new MapSqlParameterSource("ids", operatorIds));
    }

    /** 묶음 시간 안에 같은 이벤트·같은 묶음 키의 알림을 이미 받은 사람 */
    public Set<Long> recentlyNotified(String eventCode, String dedupeKey, Collection<Long> operatorIds, Instant now, int collapseSeconds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", eventCode)
                .addValue("key", dedupeKey)
                .addValue("since", at(now.minus(Duration.ofSeconds(collapseSeconds))))
                .addValue("ids", operatorIds);
        return ids("select distinct operator_id from admin_notifications where event_code = :code "
                + "and dedupe_key is not distinct from :key and created_at > :since and operator_id in (:ids)", params);
    }

    /**
     * 알림 행을 한꺼번에 넣는다. 한 줄 = {@code id, operator_id, event_code, severity, title_key, body_key, args(jsonb 글자), link_path, dedupe_key, created_at}.
     * 영속성 컨텍스트를 거치지 않는다 — 실패한 엔티티가 업무의 다음 flush 를 깨뜨리지 않게.
     */
    public void insertNotifications(List<Object[]> rows) {
        jdbc.batchUpdate("insert into admin_notifications (id, operator_id, event_code, severity, title_key, body_key, args, link_path, dedupe_key, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)", rows);
    }

    private Set<Long> ids(String sql, MapSqlParameterSource params) {
        return new LinkedHashSet<>(namedJdbc.queryForList(sql, params, Long.class));
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
