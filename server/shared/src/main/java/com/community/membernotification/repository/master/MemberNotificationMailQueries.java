package com.community.membernotification.repository.master;

import com.community.board.entity.QBoardNotificationSettingEntity;
import com.community.user.entity.QMemberDisplayPreferenceEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 회원 알림 메일 발송({@code MemberNotificationEmailSender}·{@code RequiredMailOutboxSender})이 읽는 값 — 받는 회원(주소·인증·상태·닉네임·표시 언어)과 게시판 기본 설정 「알림」의 조용한 시간.
 *
 * <p>커밋 뒤 메일 스레드풀에서 트랜잭션 없이 한 번씩 읽는 짧은 조회라 엔티티를 올리지 않고 프로젝션으로 읽는다(질의 하나가 자기 연결로 끝난다).
 * 이메일 주소는 돌려주기만 하고 로그에 남기지 않는다({@link Recipient#toString()} 도 감춘다).
 */
@Repository
public class MemberNotificationMailQueries extends RoboyamRepositoryQDSL {

    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QMemberDisplayPreferenceEntity DISPLAY = QMemberDisplayPreferenceEntity.memberDisplayPreferenceEntity;
    private static final QBoardNotificationSettingEntity SETTING = QBoardNotificationSettingEntity.boardNotificationSettingEntity;
    /** 게시판 기본 설정 「알림」 행(한 줄) */
    private static final int SETTING_ROW = 1;

    public MemberNotificationMailQueries(JPAQueryFactory query) {
        super(query);
    }

    /** 받는 회원 — 없거나 지워진 회원이면 비어 있다 */
    public Optional<Recipient> recipient(long userId) {
        Tuple row = query.select(USER.email, USER.status, USER.emailVerifiedAt, PROFILE.nickname, DISPLAY.language).from(USER)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(USER.id))
                .leftJoin(DISPLAY).on(DISPLAY.userId.eq(USER.id))
                .where(USER.id.eq(userId), USER.deletedAt.isNull())
                .fetchFirst();
        if (row == null) {
            return Optional.empty();
        }
        UserStatus status = row.get(USER.status);
        return Optional.of(new Recipient(row.get(USER.email), status == null ? null : status.name(), row.get(USER.emailVerifiedAt) != null,
                row.get(PROFILE.nickname), row.get(DISPLAY.language)));
    }

    /** 회원 닉네임(일으킨 사람) — 없으면 비어 있다 */
    public Optional<String> nickname(long userId) {
        return Optional.ofNullable(query.select(PROFILE.nickname).from(PROFILE).join(USER).on(USER.id.eq(PROFILE.userId))
                .where(PROFILE.userId.eq(userId), USER.deletedAt.isNull(), USER.status.ne(UserStatus.WITHDRAWN)).fetchFirst());
    }

    /** 게시판 기본 설정 「알림」의 이메일 조용한 시간 — 행이 없으면 비어 있다 */
    public Optional<QuietHours> quietHours() {
        return Optional.ofNullable(query.select(Projections.constructor(QuietHours.class, SETTING.emailQuietHoursEnabled, SETTING.emailQuietStart, SETTING.emailQuietEnd))
                .from(SETTING).where(SETTING.id.eq(SETTING_ROW)).fetchFirst());
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
