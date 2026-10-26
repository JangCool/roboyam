package com.community.membernotification.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 회원 알림 종류 — <b>실제로 발행되는 것만</b> 둔다(코드가 원천, V50 의 CHECK 와 같은 값). 이름은 한 번 정하면 바꾸지 않는다(알림 행이 이 값으로 가리킨다).
 * {@code COMMENT}·{@code REPLY} 는 관리자 회원 상세의 알림 설정 종류(admin-web {@code MemberNotificationKind})·community {@code api-notification.md} 와 같은 이름이다.
 * 같은 이름의 회원 이벤트({@link MemberEvent})가 사용 여부·앱 알림·잠금을 정한다(발행기는 그 설정을 본다).
 *
 * <ul>
 *   <li>{@link #category()}: 목록 거르기 — {@code comment}(댓글·답글) · {@code dm}(쪽지) · {@code system}(그 밖).</li>
 * </ul>
 */
public enum MemberNotificationType {

    /** 내 글에 새 댓글 — 같은 글의 새 댓글은 묶음 시간 안에서 한 줄로 묶는다 */
    COMMENT("comment"),
    /** 내 댓글에 답글 */
    REPLY("comment"),
    /** 내 포인트가 회수됐다(운영 조정 차감) */
    POINTS_REVOKED("system"),
    /** 신고가 쌓여(또는 불법촬영물·권리침해 신고로) 내 글이 자동으로 숨겨졌다 */
    POST_HIDDEN("system"),
    /** 신고로 내 댓글이 자동으로 숨겨졌다 */
    COMMENT_HIDDEN("system"),
    /**
     * 쪽지를 받았다 — 같은 대화의 안 읽은 쪽지 알림이 있으면 새로 만들지 않는다(대화를 읽으면 다음 쪽지부터 다시 알린다). 묶음 {@code dm} 은 목록 거르기 {@code all}·{@code dm} 에 나온다
     * (community {@code api-notification.md} 처럼 댓글·시스템 거르기에는 안 나온다)
     */
    DM("dm"),
    /** 내 닉네임 그림이 승인·반려·해제됐다(운영자 처리 — 사유와 함께) */
    NICKNAME_IMAGE_REVIEWED("system"),
    /** 내가 확인할 의견 고정 요청이 왔다(글쓴이·게시판 매니저 — 게시판 정책 pinRequestReviewers, V78) — 일으킨 사람 = 요청한 회원, 글·댓글을 가리킨다 */
    OPINION_PIN_REQUESTED("comment"),
    /** 내 의견 고정 요청이 승인·거절됐다(거절 사유와 함께, V78) */
    OPINION_PIN_REVIEWED("system"),
    /** 운영자가 내 글을 지웠다(관리자 게시글 관리·신고 관리 — 글 제목·사유, V80). 지운 글은 열 수 없어 링크가 없다 */
    POST_REMOVED("system"),
    /** 운영자가 내 댓글을 지웠다(관리자 신고 관리 — 댓글 앞부분·사유, V82). 링크는 그 글 */
    COMMENT_REMOVED("system"),
    /** 내가 한 신고가 처리됐다(관리자 신고 관리 — 조치됨 · 위반 아님, V82). 게시판 신고 정책 notifyReporterOnResult 가 켜졌을 때만 */
    REPORT_RESULT("system"),
    /** 내 계정에 기능 제한(포인트 동결·공구 참여 제한·충전 제한)이 걸렸다 — 종류·끝·공개 사유(운영 메모는 싣지 않는다, commerce.md 12-3) */
    FEATURE_RESTRICTED("system"),
    /**
     * 내 무상 포인트가 곧 소멸된다(소멸 예정 알림 배치 {@code point-expiry-notice}, commerce.md 14) — 알림 기간({@code point_settings.expiry_notice_days}) 안 소멸 예정 합 · 가장 가까운
     * 소멸일(한국 날짜) · 일수. 같은 묶음으로 두 번 알리지 않는다({@code point_lots.expiry_noticed_at}). 누르면 내 포인트({@code /me/points})
     */
    POINTS_EXPIRING("system");

    private final String category;

    MemberNotificationType(String category) {
        this.category = category;
    }

    public String category() {
        return category;
    }

    public static Optional<MemberNotificationType> of(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
