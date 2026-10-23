package com.community.membernotification.repository.master;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 알림 메일 발송({@code MemberNotificationEmailSender})이 읽는 값 — 받는 회원(주소·인증·상태·닉네임·표시 언어)과 게시판 기본 설정 「알림」의 조용한 시간.
 *
 * <p>Native SQL(JDBC)인 이유: 커밋 뒤 메일 스레드풀에서 트랜잭션 없이 한 번씩 읽는 짧은 조회다(회원·프로필·표시 설정 세 표를 한 번에 — 엔티티를 셋 올릴 까닭이 없다).
 * 이메일 주소는 돌려주기만 하고 로그에 남기지 않는다({@link Recipient#toString()} 도 감춘다).
 */
@Repository
public class MemberNotificationMailQueries {

    private final JdbcTemplate jdbc;

    public MemberNotificationMailQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 받는 회원 — 없거나 지워진 회원이면 비어 있다 */
    public Optional<Recipient> recipient(long userId) {
        List<Recipient> rows = jdbc.query("select u.email, u.status, u.email_verified_at is not null as verified, p.nickname, d.language "
                + "from users u left join profiles p on p.user_id = u.id left join member_display_preferences d on d.user_id = u.id "
                + "where u.id = ? and u.deleted_at is null", (rs, i) -> new Recipient(rs.getString("email"), rs.getString("status"), rs.getBoolean("verified"),
                rs.getString("nickname"), rs.getString("language")), userId);
        return rows.stream().findFirst();
    }

    /** 회원 닉네임(일으킨 사람) — 없으면 비어 있다 */
    public Optional<String> nickname(long userId) {
        return jdbc.queryForList("select p.nickname from profiles p join users u on u.id = p.user_id where p.user_id = ? and u.deleted_at is null "
                + "and u.status <> 'WITHDRAWN'", String.class, userId).stream().findFirst();
    }

    /** 게시판 기본 설정 「알림」의 이메일 조용한 시간 — 행이 없으면 비어 있다 */
    public Optional<QuietHours> quietHours() {
        List<QuietHours> rows = jdbc.query("select email_quiet_hours_enabled, email_quiet_start, email_quiet_end from board_notification_settings where id = 1",
                (rs, i) -> new QuietHours(rs.getBoolean(1), rs.getString(2), rs.getString(3)));
        return rows.stream().findFirst();
    }

    /**
     * @param status   {@code users.status}({@code ACTIVE}·{@code SUSPENDED}·{@code BANNED}·{@code WITHDRAWN})
     * @param verified 이메일 인증을 마쳤는가
     * @param language 회원 표시 설정의 언어(없으면 null — 사이트 표시 기본 언어)
     */
    public record Recipient(String email, String status, boolean verified, String nickname, String language) {

        @Override
        public String toString() {
            return "Recipient[email=(감춤), status=" + status + ", verified=" + verified + "]";
        }
    }

    /** @param start·end {@code HH:mm}(사이트 표시 시간대) */
    public record QuietHours(boolean enabled, String start, String end) {
    }
}
