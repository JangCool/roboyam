package com.community.membernotification.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 회원 알림 종류 — <b>실제로 발행되는 것만</b> 둔다(코드가 원천, V50 의 CHECK 와 같은 값). 이름은 한 번 정하면 바꾸지 않는다(알림 행이 이 값으로 가리킨다).
 * {@code COMMENT}·{@code REPLY} 는 관리자 회원 상세의 알림 설정 종류(admin-web {@code MemberNotificationKind})·community {@code api-notification.md} 와 같은 이름이다.
 * 같은 이름의 회원 이벤트({@link MemberEvent})가 사용 여부·앱 알림·잠금을 정한다(발행기는 그 설정을 본다).
 *
 * <ul>
 *   <li>{@link #category()}: 목록 거르기 — {@code comment}(댓글·답글) · {@code system}(그 밖).</li>
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
    /** 내 계정에 기능 제한(포인트 동결·공구 참여 제한·충전 제한)이 걸렸다 — 종류·끝·공개 사유(운영 메모는 싣지 않는다, commerce.md 12-3) */
    FEATURE_RESTRICTED("system");

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
