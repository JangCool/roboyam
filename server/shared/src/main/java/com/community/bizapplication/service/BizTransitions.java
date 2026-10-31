package com.community.bizapplication.service;

import static com.community.bizapplication.entity.BizApplicationStatus.AWAITING_DEPOSIT;
import static com.community.bizapplication.entity.BizApplicationStatus.AWAITING_PAYMENT;
import static com.community.bizapplication.entity.BizApplicationStatus.CANCELED;
import static com.community.bizapplication.entity.BizApplicationStatus.EXPIRED;
import static com.community.bizapplication.entity.BizApplicationStatus.REJECTED;
import static com.community.bizapplication.entity.BizApplicationStatus.SCHEDULED;
import static com.community.bizapplication.entity.BizApplicationStatus.SUBMITTED;
import static com.community.bizapplication.entity.BizApplicationStatus.TAKEN_DOWN;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.exception.BizApplicationException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 상태 전이 표(★-3 · 설계 2절) — 처리마다 <b>어느 상태에서 어느 상태로</b> 갈 수 있는지의 원천이다. 시각·입금 기록 같은 덧붙은 조건(시작 전인가, 입금 기록이 없는가)은
 * 서비스가 같이 본다. 표에 없는 조합은 409 {@code BIZ_APPLICATION_STATE_CHANGED}.
 */
public final class BizTransitions {

    /** 상태를 바꾸는 처리 */
    public enum Action {
        /** 운영자 승인(온라인) */
        APPROVE_ONLINE,
        /** 운영자 승인(수동) */
        APPROVE_MANUAL,
        REJECT,
        /** 사업자 취소 — 입금 기록이 있는 입금 대기는 서비스가 막는다 */
        MEMBER_CANCEL,
        /** 사업자 소재 바꾸기 — 심사 대기는 그대로, 승인 뒤 결제 전은 다시 심사 대기(입금 기록이 있으면 서비스가 막는다) */
        CREATIVE_CHANGE,
        /** 온라인 결제 완료 */
        PAY,
        /** 결제 기한 지남(배치) */
        EXPIRE,
        /** 수동 결제로 전환(운영자) */
        MANUAL_SWITCH,
        /** 수동 예약 확정(계약서 + 입금 확인) */
        CONFIRM,
        /** 운영자 취소 — 예약은 시작 전만(서비스가 본다). 심사 대기는 운영자 등록 건(source=OPERATOR — 등록 실수)만 — 사업자 신청 건은 반려로 닫는다(서비스가 본다, 04 §2) */
        OPERATOR_CANCEL,
        /** 결제 전 건 정리(사업자 정지 · 탈퇴) — 심사·결제·입금 대기 → 취소 */
        SYSTEM_CANCEL,
        /** 시작 전 전액 자동 환불(사업자 요청 · 정지 · 탈퇴) */
        REFUND_FULL,
        /** 환불 승인(진행 중·내림 건 노출 중단) */
        REFUND_APPROVE,
        /** 내림(정지·긴급) */
        TAKEDOWN,
        /** 내림 되돌리기 */
        RESTORE,
        /** 남은 기간 포기(탈퇴) */
        FORFEIT
    }

    private static final Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> TABLE = build();

    private BizTransitions() {
    }

    private static Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> build() {
        Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> t = new EnumMap<>(Action.class);
        put(t, Action.APPROVE_ONLINE, SUBMITTED, AWAITING_PAYMENT);
        put(t, Action.APPROVE_MANUAL, SUBMITTED, AWAITING_DEPOSIT);
        put(t, Action.REJECT, SUBMITTED, REJECTED);
        put(t, Action.MEMBER_CANCEL, SUBMITTED, CANCELED);
        put(t, Action.MEMBER_CANCEL, AWAITING_PAYMENT, CANCELED);
        put(t, Action.MEMBER_CANCEL, AWAITING_DEPOSIT, CANCELED);
        put(t, Action.CREATIVE_CHANGE, SUBMITTED, SUBMITTED);
        put(t, Action.CREATIVE_CHANGE, AWAITING_PAYMENT, SUBMITTED);
        put(t, Action.CREATIVE_CHANGE, AWAITING_DEPOSIT, SUBMITTED);
        put(t, Action.PAY, AWAITING_PAYMENT, SCHEDULED);
        put(t, Action.EXPIRE, AWAITING_PAYMENT, EXPIRED);
        put(t, Action.MANUAL_SWITCH, AWAITING_PAYMENT, AWAITING_DEPOSIT);
        put(t, Action.CONFIRM, AWAITING_DEPOSIT, SCHEDULED);
        put(t, Action.OPERATOR_CANCEL, SUBMITTED, CANCELED);
        put(t, Action.OPERATOR_CANCEL, AWAITING_PAYMENT, CANCELED);
        put(t, Action.OPERATOR_CANCEL, AWAITING_DEPOSIT, CANCELED);
        put(t, Action.OPERATOR_CANCEL, SCHEDULED, CANCELED);
        put(t, Action.SYSTEM_CANCEL, SUBMITTED, CANCELED);
        put(t, Action.SYSTEM_CANCEL, AWAITING_PAYMENT, CANCELED);
        put(t, Action.SYSTEM_CANCEL, AWAITING_DEPOSIT, CANCELED);
        put(t, Action.REFUND_FULL, SCHEDULED, CANCELED);
        put(t, Action.REFUND_FULL, TAKEN_DOWN, CANCELED);
        put(t, Action.REFUND_APPROVE, SCHEDULED, CANCELED);
        put(t, Action.REFUND_APPROVE, TAKEN_DOWN, CANCELED);
        put(t, Action.TAKEDOWN, SCHEDULED, TAKEN_DOWN);
        put(t, Action.RESTORE, TAKEN_DOWN, SCHEDULED);
        put(t, Action.FORFEIT, SCHEDULED, CANCELED);
        put(t, Action.FORFEIT, TAKEN_DOWN, CANCELED);
        Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> out = new EnumMap<>(Action.class);
        t.forEach((k, v) -> out.put(k, Collections.unmodifiableMap(v)));
        return Collections.unmodifiableMap(out);
    }

    private static void put(Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> t, Action action, BizApplicationStatus from, BizApplicationStatus to) {
        t.computeIfAbsent(action, k -> new EnumMap<>(BizApplicationStatus.class)).put(from, to);
    }

    /** 그 처리로 갈 상태(허용되지 않으면 비어 있다) */
    public static Optional<BizApplicationStatus> target(Action action, BizApplicationStatus from) {
        return Optional.ofNullable(TABLE.getOrDefault(action, Map.of()).get(from));
    }

    /** 허용되지 않으면 409 {@code BIZ_APPLICATION_STATE_CHANGED} */
    public static BizApplicationStatus require(Action action, BizApplicationStatus from) {
        return target(action, from).orElseThrow(BizApplicationException::stateChanged);
    }

    /** 표 전체(시험·문서용) */
    public static Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> table() {
        return TABLE;
    }
}
