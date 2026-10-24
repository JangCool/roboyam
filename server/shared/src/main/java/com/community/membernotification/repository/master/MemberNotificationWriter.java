package com.community.membernotification.repository.master;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 알림 행 만들기·묶기와 그 판정에 필요한 읽기 — {@code MemberNotificationPublisher} 만 부른다.
 *
 * <p>Native SQL(JDBC)인 이유: 알림 실패가 업무(댓글쓰기·포인트 조정·신고)를 깨지 않도록 업무 트랜잭션 연결의 세이브포인트 안에서 영속성 컨텍스트를 거치지 않고 쓴다
 * ({@code IsolatedSideWork}, 관리자 알림 {@code NotificationPublisher} 와 같은 방식). 업무 트랜잭션(JPA)의 같은 연결에 참여하므로 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class MemberNotificationWriter {

    private final JdbcTemplate jdbc;

    public MemberNotificationWriter(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 알림을 받을 수 있는 회원인가 — 탈퇴·삭제된 회원은 받지 않는다(정지 회원은 받는다) */
    public boolean receivable(long userId) {
        return !jdbc.queryForList("select 1 from users where id = ? and status <> 'WITHDRAWN' and deleted_at is null", Integer.class, userId).isEmpty();
    }

    /** 게시판 기본 설정 「알림」의 묶음 시간(분). 행이 없으면 비어 있다(옛 칸 {@code member_defaults} 는 더 이상 읽지 않는다 — 회원 이벤트 설정이 대신한다) */
    public Optional<Integer> groupWindowMinutes() {
        return jdbc.queryForList("select group_window_minutes from board_notification_settings where id = 1", Integer.class).stream().findFirst();
    }

    /** 회원 이벤트 설정 한 줄(V58 {@code member_event_settings}) — 사용 여부 · 앱 알림 · 이메일. 줄이 없으면 비어 있다(코드의 처음 값을 쓴다) */
    public Optional<EventSwitch> eventSetting(String code) {
        List<EventSwitch> rows = jdbc.query("select enabled, in_app, email from member_event_settings where code = ?",
                (rs, i) -> new EventSwitch(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)), code);
        return rows.stream().findFirst();
    }

    /** 묶을 줄 — 같은 회원·같은 글의 안 읽은 COMMENT 중 {@code since} 뒤에 만든 가장 최근 줄(잠근다) */
    public Optional<Long> groupable(long userId, long postId, Instant since) {
        return jdbc.queryForList("select id from member_notifications where user_id = ? and type = 'COMMENT' and post_id = ? and read_at is null "
                + "and created_at > ? order by id desc limit 1 for update", Long.class, userId, postId, Timestamp.from(since)).stream().findFirst();
    }

    /** 묶인 줄에 새 댓글을 더한다 — 일으킨 사람·가리키는 댓글은 가장 최근 것으로 바꾸고 묶인 수를 하나 늘린다(만든 시각·차례는 그대로) */
    public void addToGroup(long id, Long actorUserId, String actorName, Long commentId) {
        jdbc.update("update member_notifications set group_count = group_count + 1, actor_user_id = ?, actor_name = ?, comment_id = ? where id = ?",
                actorUserId, actorName, commentId, id);
    }

    /** 이 대화의 안 읽은 쪽지 알림(DM)이 있는가 — 덧붙임 {@code threadId} 로 찾는다(안 읽은 알림 부분 색인 {@code (user_id, id)} 안에서 거른다) */
    public boolean unreadDmExists(long userId, long threadId) {
        return !jdbc.queryForList("select 1 from member_notifications where user_id = ? and type = 'DM' and read_at is null and payload ->> 'threadId' = ? limit 1",
                Integer.class, userId, Long.toString(threadId)).isEmpty();
    }

    public void insert(long id, long userId, String type, Long actorUserId, String actorName, Long postId, Long commentId, String payloadJson, Instant createdAt) {
        jdbc.update("insert into member_notifications (id, user_id, type, actor_user_id, actor_name, post_id, comment_id, payload, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)", id, userId, type, actorUserId, actorName, postId, commentId, payloadJson, Timestamp.from(createdAt));
    }

    /** 회원 이벤트의 사용 여부·앱 알림·이메일 — 앱 알림·이메일은 사용 중일 때만 뜻이 있다 */
    public record EventSwitch(boolean enabled, boolean inApp, boolean email) {

        public EventSwitch {
            inApp = enabled && inApp;
            email = enabled && email;
        }

        /** 나가는 것이 하나라도 있는가 */
        public boolean delivers() {
            return inApp || email;
        }
    }
}
