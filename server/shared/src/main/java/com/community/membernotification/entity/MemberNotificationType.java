package com.community.membernotification.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 회원 알림 종류 — <b>실제로 발행되는 것만</b> 둔다(코드가 원천, V50 의 CHECK 와 같은 값). 이름은 한 번 정하면 바꾸지 않는다(알림 행이 이 값으로 가리킨다).
 * {@code COMMENT}·{@code REPLY} 는 관리자 회원 상세의 알림 설정 종류(admin-web {@code MemberNotificationKind})·community {@code api-notification.md} 와 같은 이름이다.
 *
 * <ul>
 *   <li>{@link #category()}: 목록 거르기 — {@code comment}(댓글·답글) · {@code system}(그 밖).</li>
 *   <li>{@link #boardSettingKind()}: 게시판 기본 설정 「알림」({@code board_notification_settings.member_defaults})의 종류 — 그 줄의 {@code site} 가 꺼져 있으면 만들지 않는다.
 *       null 이면 끌 수 없는 계정 알림(포인트 회수·신고로 숨김)이다.</li>
 * </ul>
 */
public enum MemberNotificationType {

    /** 내 글에 새 댓글 — 같은 글의 새 댓글은 묶음 시간 안에서 한 줄로 묶는다 */
    COMMENT("comment", "COMMENT_ON_POST"),
    /** 내 댓글에 답글 */
    REPLY("comment", "REPLY_ON_COMMENT"),
    /** 내 포인트가 회수됐다(운영 조정 차감) */
    POINTS_REVOKED("system", null),
    /** 신고가 쌓여(또는 불법촬영물·권리침해 신고로) 내 글이 자동으로 숨겨졌다 */
    POST_HIDDEN("system", null),
    /** 신고로 내 댓글이 자동으로 숨겨졌다 */
    COMMENT_HIDDEN("system", null);

    private final String category;
    private final String boardSettingKind;

    MemberNotificationType(String category, String boardSettingKind) {
        this.category = category;
        this.boardSettingKind = boardSettingKind;
    }

    public String category() {
        return category;
    }

    public String boardSettingKind() {
        return boardSettingKind;
    }

    public static Optional<MemberNotificationType> of(String name) {
        return Arrays.stream(values()).filter(type -> type.name().equals(name)).findFirst();
    }
}
