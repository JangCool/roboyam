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
    POINTS_EXPIRING("system"),
    /** 정지 중에 낸 포인트 상점 환불 신청을 운영자가 수락해 환불했다(V87) — 품목 이름 · 돌려준 포인트. 누르면 내 아이템({@code /me/items}) */
    POINT_SHOP_REFUND_APPROVED("system"),
    /** 정지 중에 낸 포인트 상점 환불 신청을 운영자가 거절했다(V87) — 품목 이름 · 사유. 누르면 내 아이템 */
    POINT_SHOP_REFUND_REJECTED("system"),

    // ── 소모임(V89 — docs/features/group-contract.md 1단계, community api-groups.md 7절). 묶음 system(user-web 거르기에 소모임 묶음이 없어 계정·처리 알림과 같이). 덧붙임 groupId·groupName(+reason·applicantId) ──
    /** 내 개설 신청이 승인돼 소모임이 열렸다 — 누르면 그 소모임 */
    GROUP_APPROVED("system"),
    /** 내 개설 신청이 반려됐다(사유와 함께) — 누르면 내 소모임 */
    GROUP_REJECTED("system"),
    /** 내가 처리할 가입 신청이 왔다(모임장 — 3단계부터 APPLICATIONS 매니저도) — 일으킨 사람 = 신청한 회원. 누르면 가입 신청 관리 */
    GROUP_JOIN_REQUEST("system"),
    /** 내 가입 신청이 승인됐다 — 누르면 그 소모임 */
    GROUP_JOIN_APPROVED("system"),
    /** 내 가입 신청이 거절됐다(처리자가 사유를 적었을 때만 사유) — 누르면 내 소모임 */
    GROUP_JOIN_REJECTED("system"),
    /** 소모임에서 내보내졌다 — 누르면 소모임 목록 */
    GROUP_KICKED("system"),
    /** 내가 멤버인 소모임이 해산됐다 — 누르면 소모임 목록 */
    GROUP_DISSOLVED("system"),
    /** 모임장 자리를 넘겨받았다 — 누르면 그 소모임 */
    GROUP_LEADER_TRANSFERRED("system"),
    /** 내가 모임장인 소모임이 운영 조치로 비활성이 됐다(사유와 함께) — 누르면 그 소모임 */
    GROUP_DEACTIVATED("system"),
    /** 내가 모임장인 소모임이 다시 열렸다(운영 메모는 싣지 않는다) — 누르면 그 소모임 */
    GROUP_REACTIVATED("system"),
    /**
     * 모임장(또는 되돌리기를 맡은 매니저 — 3단계)이 내 공개 소모임 글을 비공개로 되돌렸다(V90, community api-groups.md 8절) — 덧붙임 groupId·groupName·title·actorRole(LEADER · MANAGER).
     * 글·일으킨 사람 칸은 그 글·처리자. 누르면 그 글. 작성자 본인이 되돌리면 알림 없음
     */
    GROUP_POST_UNPUBLISHED("system"),

    // ── 소모임 매니저(V91 — group-contract 3단계, community api-groups.md 5-1·7절 「매니저 알림 5종」). 모두 끌 수 없다(내 자리·할 일이 바뀐 알림) ──
    /** 매니저로 임명됐다 — 덧붙임 groupId·groupName·permissions(맡은 일 키, 고정 순서). 일으킨 사람 = 모임장. 누르면 관리 메인 */
    GROUP_MANAGER_APPOINTED("system"),
    /** 내 맡은 일이 바뀌었다(바뀐 것이 없으면 안 간다) — 덧붙임 permissions = 바뀐 뒤 지금 맡은 일 전체. 누르면 관리 메인 */
    GROUP_MANAGER_PERMISSIONS_CHANGED("system"),
    /** 매니저에서 해임됐다 — 누르면 그 소모임 */
    GROUP_MANAGER_REMOVED("system"),
    /** 매니저가 스스로 물러났다(모임장에게) — 일으킨 사람 = 물러난 매니저(덧붙임 actorId). 누르면 멤버 관리 */
    GROUP_MANAGER_RESIGNED("system"),
    /** 매니저가 멤버를 강퇴했다(모임장에게) — 일으킨 사람 = 강퇴한 매니저, 덧붙임 targetId(내보낸 멤버). 누르면 멤버 관리 */
    GROUP_KICKED_BY_MANAGER("system"),

    // ── 소모임 일정(V92 — group-contract 4단계, community api-groups.md 9절 「알림 GROUP_EVENT」). 소모임 알림 중 유일하게 회원이 끌 수 있다 ──
    /**
     * 내가 멤버인 소모임의 일정이 등록·수정·취소됐다 — 덧붙임 groupId·groupName·eventId·title(일정 제목)·eventAction(CREATED · UPDATED · CANCELED)·reason(취소 사유, 적었을 때만).
     * 일으킨 사람 = 한 사람(모임장 · 일정 관리를 맡은 매니저). 누르면 그 일정({@code /groups/{groupId}/events/{eventId}})
     */
    GROUP_EVENT("system");

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
