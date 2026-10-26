package com.community.commerce.guard.dto;

import java.util.Arrays;
import java.util.Optional;

/**
 * 속도 제한·멱등 키를 거는 <b>돈 관련 회원 요청</b>의 종류(docs/domain/commerce.md 3절·12-4). 코드({@link #name()})는 {@code request_rate_limits.action}·
 * {@code request_rate_counters.action}·{@code idempotency_requests.action} 에 그대로 들어가므로 한 번 정하면 바꾸지 않는다.
 * 기본 한도는 V62 가 넣는 기본 행과 같다(관리자 화면 「커머스 설정 › 요청 제한」이 바꾼다). 이름·설명 문구는 화면(admin-web i18n {@code commerce.requestLimits.action.*})이 가진다.
 */
public enum RequestAction {

    /** 공구 참여(수량 선점) — 회원 1명 1분 5번 */
    GROUPBUY_JOIN(5),
    /** 포인트 충전 요청 — 3번 */
    POINT_CHARGE(3),
    /** 상점 구매 — 10번 */
    SHOP_PURCHASE(10),
    /** 환불 신청(아이템·충전·반품) — 3번 */
    REFUND_REQUEST(3);

    /** 한도의 범위 — 0 은 「제한 없음」 */
    public static final int MIN_LIMIT = 0;
    public static final int MAX_LIMIT = 100;

    private final int defaultLimit;

    RequestAction(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public static Optional<RequestAction> ofCode(String code) {
        return Arrays.stream(values()).filter(action -> action.name().equals(code)).findFirst();
    }
}
