package com.community.membernotification.entity;

import com.community.notification.entity.NotificationArea;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 회원 이벤트 카탈로그 — 공개 사이트 회원이 받는 알림의 일. <b>코드가 원천</b>이다(관리자 알림 {@code AdminNotificationEvent} 와 같은 방식). 지금 발행되는 회원 알림 5종
 * ({@link MemberNotificationType})과 예전 게시판 기본 설정 「알림」의 7종({@code board_notification_settings.member_defaults} 의 kind)을 하나로 합쳤다 — 실제로 발행되는 것만 바꿀 수 있고
 * ({@link #featureReady()}), 나머지는 「준비 중」이다. docs/features/message-template-contract.md 「서버 구현 결과(회원 이벤트·이메일 확장)」.
 *
 * <p>코드({@link #code()} = 상수 이름)는 한 번 정하면 바꾸지 않는다(설정 표 {@code member_event_settings} 가 이 값으로 가리킨다). 실제 이벤트의 코드는 {@link MemberNotificationType} 이름과 같다.
 * 운영자가 바꾸는 것은 사용 여부 · 앱 알림 · 이메일 · 회원이 끌 수 있음뿐이고(표), 영역 · 받는 사람 · 잠금은 코드에 고정이다.
 *
 * <ul>
 *   <li>{@link #lockReason()} — 회원이 끌 수 있는지를 코드가 잠갔다(늘 끌 수 없음): 계정 알림(포인트 회수·신고로 숨김) · 처리 알림(운영·스텝 처리 — 사유와 함께 보낸다).</li>
 *   <li>{@link #boardSettingKind()} — 예전 게시판 기본 설정 「알림」({@code board_notification_settings.member_defaults} — 옛 칸)의 같은 종류. V58 이 그 값을 옮겼다.</li>
 *   <li>처음 값(표에 줄이 없을 때): 실제 이벤트는 사용 · 앱 알림 켬, 준비 중은 사용 꺼짐 · 앱 알림 켬. 이메일은 모두 꺼짐. 회원이 끌 수 있음은 잠기지 않았으면 켬.</li>
 * </ul>
 *
 * <p>문구 키(ko·en): {@code member.event.<코드>.name|description|recipients}.
 */
public enum MemberEvent {

    /** 내 글에 댓글 */
    COMMENT(NotificationArea.CONTENT, MemberNotificationType.COMMENT, null, "COMMENT_ON_POST"),
    /** 내 댓글에 답글 */
    REPLY(NotificationArea.CONTENT, MemberNotificationType.REPLY, null, "REPLY_ON_COMMENT"),
    /** 포인트 회수 — 계정 알림 */
    POINTS_REVOKED(NotificationArea.MEMBER, MemberNotificationType.POINTS_REVOKED, MemberEventLockReason.ACCOUNT, null),
    /** 내 글 숨김(신고) — 계정 알림 */
    POST_HIDDEN(NotificationArea.SAFETY, MemberNotificationType.POST_HIDDEN, MemberEventLockReason.ACCOUNT, null),
    /** 내 댓글 숨김(신고) — 계정 알림 */
    COMMENT_HIDDEN(NotificationArea.SAFETY, MemberNotificationType.COMMENT_HIDDEN, MemberEventLockReason.ACCOUNT, null),
    /** 기능 제한이 걸림 — 계정 알림 */
    FEATURE_RESTRICTED(NotificationArea.SAFETY, MemberNotificationType.FEATURE_RESTRICTED, MemberEventLockReason.ACCOUNT, null),
    /** 포인트 소멸 예정(알림 배치 point-expiry-notice) — 내 포인트에 관한 계정 알림이라 회원이 끌 수 없다(포인트 회수와 같다). 운영자는 이벤트 설정에서 끌 수 있다 */
    POINTS_EXPIRING(NotificationArea.MEMBER, MemberNotificationType.POINTS_EXPIRING, MemberEventLockReason.ACCOUNT, null),
    /** 포인트 상점 환불 신청 수락(정지 중 신청 — 운영자 처리) — 처리 알림이라 회원이 끌 수 없다 */
    POINT_SHOP_REFUND_APPROVED(NotificationArea.MEMBER, MemberNotificationType.POINT_SHOP_REFUND_APPROVED, MemberEventLockReason.ACTION, null),
    /** 포인트 상점 환불 신청 거절(사유와 함께) — 처리 알림이라 회원이 끌 수 없다 */
    POINT_SHOP_REFUND_REJECTED(NotificationArea.MEMBER, MemberNotificationType.POINT_SHOP_REFUND_REJECTED, MemberEventLockReason.ACTION, null),

    // ── 준비 중(발행처 없음) — 기능이 생기면 MemberNotificationType 에 한 줄 + 여기서 그 종류를 넣는다 ──
    MENTION(NotificationArea.CONTENT, null, null, "MENTION"),
    POST_POPULAR(NotificationArea.CONTENT, null, null, "POST_POPULAR"),
    OPERATOR_ACTION(NotificationArea.SAFETY, null, MemberEventLockReason.ACTION, "OPERATOR_ACTION"),
    STAFF_ACTION(NotificationArea.SAFETY, null, MemberEventLockReason.ACTION, "STAFF_ACTION"),
    /** 내 신고의 처리 결과(관리자 신고 관리 — 조치됨 · 위반 아님) — 회원이 끌 수 있다. 게시판 신고 정책 notifyReporterOnResult 가 켜졌을 때만 발행된다(V82 부터 실제 이벤트) */
    REPORT_RESULT(NotificationArea.SAFETY, MemberNotificationType.REPORT_RESULT, null, "REPORT_RESULT"),

    /** 쪽지 받음 — 회원이 끌 수 있다(잠금 없음) */
    DM(NotificationArea.MEMBER, MemberNotificationType.DM, null, null),
    /** 닉네임 그림 검토 결과(승인·반려·해제) — 운영자 처리 알림이라 회원이 끌 수 없다 */
    NICKNAME_IMAGE_REVIEWED(NotificationArea.MEMBER, MemberNotificationType.NICKNAME_IMAGE_REVIEWED, MemberEventLockReason.ACTION, null),
    /** 의견 고정 요청이 옴(확인할 수 있는 글쓴이·게시판 매니저에게) — 회원이 끌 수 있다 */
    OPINION_PIN_REQUESTED(NotificationArea.CONTENT, MemberNotificationType.OPINION_PIN_REQUESTED, null, null),
    /** 내 의견 고정 요청의 결과(승인·거절) — 처리 알림이라 회원이 끌 수 없다 */
    OPINION_PIN_REVIEWED(NotificationArea.CONTENT, MemberNotificationType.OPINION_PIN_REVIEWED, MemberEventLockReason.ACTION, null),
    /** 운영자가 내 글을 지움(관리자 게시글 관리 — 사유와 함께) — 처리 알림이라 회원이 끌 수 없다 */
    POST_REMOVED(NotificationArea.SAFETY, MemberNotificationType.POST_REMOVED, MemberEventLockReason.ACTION, null),
    /** 운영자가 내 댓글을 지움(관리자 신고 관리 — 사유와 함께) — 처리 알림이라 회원이 끌 수 없다 */
    COMMENT_REMOVED(NotificationArea.SAFETY, MemberNotificationType.COMMENT_REMOVED, MemberEventLockReason.ACTION, null);

    private static final Map<String, MemberEvent> BY_CODE = Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(MemberEvent::code, Function.identity()));

    private final NotificationArea area;
    private final MemberNotificationType type;
    private final MemberEventLockReason lockReason;
    private final String boardSettingKind;

    MemberEvent(NotificationArea area, MemberNotificationType type, MemberEventLockReason lockReason, String boardSettingKind) {
        this.area = area;
        this.type = type;
        this.lockReason = lockReason;
        this.boardSettingKind = boardSettingKind;
    }

    public String code() {
        return name();
    }

    public NotificationArea area() {
        return area;
    }

    /** 발행되는 회원 알림 종류 — 준비 중이면 null */
    public MemberNotificationType type() {
        return type;
    }

    /** 실제로 발행되는가 — false 면 「준비 중」(바꿀 수 없다) */
    public boolean featureReady() {
        return type != null;
    }

    /** 회원이 끌 수 있는지를 코드가 잠근 까닭 — null 이면 운영자가 정한다 */
    public MemberEventLockReason lockReason() {
        return lockReason;
    }

    public boolean locked() {
        return lockReason != null;
    }

    /** 예전 게시판 기본 설정 「알림」의 같은 종류(옛 칸) — 없으면 null */
    public String boardSettingKind() {
        return boardSettingKind;
    }

    public boolean defaultEnabled() {
        return featureReady();
    }

    public boolean defaultInApp() {
        return true;
    }

    public boolean defaultEmail() {
        return false;
    }

    public boolean defaultMemberCanTurnOff() {
        return !locked();
    }

    public String nameKey() {
        return key("name");
    }

    public String descriptionKey() {
        return key("description");
    }

    public String recipientsKey() {
        return key("recipients");
    }

    /** 번들에 있어야 하는 문구 키 */
    public List<String> bundleKeys() {
        return List.of(nameKey(), descriptionKey(), recipientsKey());
    }

    private String key(String part) {
        return "member.event." + name() + "." + part;
    }

    public static Optional<MemberEvent> ofCode(String code) {
        return Optional.ofNullable(code == null ? null : BY_CODE.get(code));
    }

    /** 발행되는 종류의 이벤트 */
    public static MemberEvent of(MemberNotificationType type) {
        return BY_CODE.get(type.name());
    }
}
