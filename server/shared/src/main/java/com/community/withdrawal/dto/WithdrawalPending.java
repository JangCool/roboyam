package com.community.withdrawal.dto;

import com.community.withdrawal.entity.WithdrawalPendingCode;
import java.time.Instant;

/**
 * 남은 일 한 건 — 화면 타입 {@code WithdrawalPendingItem}(회원·관리자 공통). 신청 줄의 남은 일 스냅샷({@code pending_snapshot})도 이 모양으로 저장한다.
 *
 * @param label     식별 글(주문번호·신청 번호 등). 화면은 가공하지 않는다
 * @param link      회원 화면 경로(없으면 null)
 * @param refId     관리자 화면이 링크를 만들 대상 ID(주문·요청·결제 취소·송금·환불 신청·광고 신청 건의 ID) — 모르면 null
 * @param waitUntil 언제쯤 끝나는지 알면 그 시각(반품 기한·광고 끝), 모르면 null
 */
public record WithdrawalPending(WithdrawalPendingCode code, String label, String link, String refId, Instant waitUntil) {
}
