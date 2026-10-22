package com.community.point.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 원장 한 줄의 사유 코드 — community {@code point_ledger.reason} + {@code SIGNUP}(roboyam 추가). admin-web {@code PointReasonCode} · V51 의 CHECK 와 같은 값이다.
 * 이름(화면에 보이는 「글 작성」 등)은 번들 키 {@code point.reason.<코드>} 로 요청 언어에 맞춰 서버가 준다.
 *
 * <p><b>지금 실제로 생기는 줄</b>: {@code POST}·{@code COMMENT}(작성) · {@code POST_REVOKE}·{@code COMMENT_REVOKE}(본인 삭제 회수) · {@code LIKE_BONUS}·{@code LIKE_BONUS_REVOKE}
 * (추천 받음 보너스·삭제 회수) · {@code ADMIN_ADJUST}(운영 조정). 출석·인기글·교환권·핫딜 종료 요청·가입은 기능이 없어 정책만 저장하고 줄이 생기지 않는다.
 */
public enum PointReason {

    ATTENDANCE,
    POST,
    COMMENT,
    POPULAR,
    POST_REVOKE,
    COMMENT_REVOKE,
    TICKET_PURCHASE,
    ADMIN_ADJUST,
    DEAL_END_REQUEST,
    LIKE_BONUS,
    LIKE_BONUS_REVOKE,
    SIGNUP;

    /** 원장 종류 — 교환권 구매는 사용, 그 밖은 부호로 지급·회수 */
    public String kindOf(long delta) {
        if (this == TICKET_PURCHASE) {
            return "USE";
        }
        return delta > 0 ? "EARN" : "REVOKE";
    }

    public String labelKey() {
        return "point.reason." + name();
    }

    public static Optional<PointReason> of(String code) {
        return Arrays.stream(values()).filter(reason -> reason.name().equals(code)).findFirst();
    }
}
