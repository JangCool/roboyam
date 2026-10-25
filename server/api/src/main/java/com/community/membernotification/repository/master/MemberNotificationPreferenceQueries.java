package com.community.membernotification.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 개인 알림 설정({@code member_notification_preferences}, V67) 읽기·저장 — {@code /api/me/notification-settings}. 발행기는 같은 표를 shared
 * {@code MemberNotificationWriter#memberChoice} 로 읽는다.
 *
 * <p>Native SQL(JDBC)인 이유: 저장은 PostgreSQL {@code INSERT … ON CONFLICT DO UPDATE}(한 문장 upsert — 같은 회원의 동시 저장이 키 충돌로 실패하지 않는다)이고,
 * 복합 키 엔티티를 두지 않으려고 읽기도 같은 방식으로 한다. 업무 트랜잭션(JPA)의 연결에 참여한다.
 */
@Repository
public class MemberNotificationPreferenceQueries {

    private final JdbcTemplate jdbc;

    public MemberNotificationPreferenceQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 회원이 고른 값 — 이벤트 코드 → 선택. 줄이 없는 이벤트는 빠진다(둘 다 켬) */
    public Map<String, Choice> choicesOf(long userId) {
        Map<String, Choice> out = new HashMap<>();
        jdbc.query("select event_code, in_app, email from member_notification_preferences where user_id = ?",
                rs -> {
                    out.put(rs.getString(1), new Choice(rs.getBoolean(2), rs.getBoolean(3)));
                }, userId);
        return out;
    }

    public void upsert(long userId, String code, boolean inApp, boolean email, Instant now) {
        jdbc.update("insert into member_notification_preferences (user_id, event_code, in_app, email, updated_at) values (?, ?, ?, ?, ?) "
                        + "on conflict (user_id, event_code) do update set in_app = excluded.in_app, email = excluded.email, updated_at = excluded.updated_at",
                userId, code, inApp, email, Timestamp.from(now));
    }

    /** 회원이 고른 받기 */
    public record Choice(boolean inApp, boolean email) {
    }
}
