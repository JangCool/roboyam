package com.community.membernotification.repository.master;

import com.community.membernotification.entity.QMemberNotificationPreferenceEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 개인 알림 설정({@code member_notification_preferences}, V67 — shared {@code MemberNotificationPreferenceEntity}) 읽기·저장 — {@code /api/me/notification-settings}.
 * 발행기는 같은 표를 shared {@code MemberNotificationWriter#memberChoice} 로 읽는다(세이브포인트 안이라 JDBC). 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>읽기는 QueryDSL 이다. Native SQL 로 남긴 것과 이유: {@link #upsert}(아래).
 */
@Repository
public class MemberNotificationPreferenceQueries extends RoboyamRepositoryQDSL {

    private static final QMemberNotificationPreferenceEntity P = QMemberNotificationPreferenceEntity.memberNotificationPreferenceEntity;

    private final JdbcTemplate jdbc;

    public MemberNotificationPreferenceQueries(JPAQueryFactory query, DataSource dataSource) {
        super(query);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 회원이 고른 값 — 이벤트 코드 → 선택. 줄이 없는 이벤트는 빠진다(둘 다 켬) */
    public Map<String, Choice> choicesOf(long userId) {
        Map<String, Choice> out = new HashMap<>();
        for (Tuple row : query.select(P.eventCode, P.inApp, P.email).from(P).where(P.userId.eq(userId)).fetch()) {
            out.put(row.get(P.eventCode), new Choice(Boolean.TRUE.equals(row.get(P.inApp)), Boolean.TRUE.equals(row.get(P.email))));
        }
        return out;
    }

    /**
     * 저장한다(없으면 넣고 있으면 고친다).
     *
     * <p>Native SQL 인 이유: PostgreSQL {@code INSERT … ON CONFLICT DO UPDATE} 한 문장 upsert — 같은 회원의 동시 저장이 키 충돌로 실패하지 않는다(나눠 쓰면 진 쪽이 유일 제약 위반으로
     * 트랜잭션 전체를 잃는다). JPQL·QueryDSL 에 대응 문법이 없다. 이 표의 엔티티는 넣거나 고치지 않으므로 영속성 컨텍스트와 어긋나지 않는다.
     */
    public void upsert(long userId, String code, boolean inApp, boolean email, Instant now) {
        jdbc.update("insert into member_notification_preferences (user_id, event_code, in_app, email, updated_at) values (?, ?, ?, ?, ?) "
                        + "on conflict (user_id, event_code) do update set in_app = excluded.in_app, email = excluded.email, updated_at = excluded.updated_at",
                userId, code, inApp, email, Timestamp.from(now));
    }

    /** 회원이 고른 받기 */
    public record Choice(boolean inApp, boolean email) {
    }
}
