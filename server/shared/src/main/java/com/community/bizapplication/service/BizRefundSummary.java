package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.DepositRow;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import java.util.List;

/**
 * 신청 건의 환불 합계(biz-pause-contract 「추가 환불(클레임)」 — 관리자 상세·광고 일정표 상세 {@code refundSummary}). 결제 전이면 만들지 않는다(null).
 *
 * @param paid       결제액 — 온라인 결제액(확정 합계) · 수동 입금 확인 금액
 * @param refunded   승인된 환불 합(돌려주기로 한 금액 — 온라인 PG 취소·수동 송금이 끝나기 전 몫도 센다)
 * @param refundable 남은 환불 가능액 = 결제액 − 승인된 환불 합(0 아래로 내려가지 않음)
 * @param feeKept    아직 돌려주지 않은 중도 해지 수수료 = 승인된 중단 환불(STOP_PRORATED)의 수수료 합 − 추가 환불(CLAIM) 합, 0 ~ 남은 환불 가능액(「수수료만」 빠른 채우기 값)
 */
public record BizRefundSummary(long paid, long refunded, long refundable, long feeKept) {

    /** 결제액 — 결제 전(온라인 미결제 · 수동 입금 기록 없음)이면 null */
    public static Long paidOf(AppRow app, DepositRow deposit) {
        if (app.paymentMethod() == null) {
            return null;
        }
        if (app.paymentMethod() == BizPaymentMethod.ONLINE) {
            return app.paidAt() == null || app.approved() == null ? null : app.approved().total();
        }
        return deposit == null ? null : deposit.amount();
    }

    /** 결제액과 환불 줄 전부로 합계를 낸다 */
    public static BizRefundSummary of(long paid, List<RefundRow> rows) {
        long refunded = 0;
        long fees = 0;
        long claimed = 0;
        for (RefundRow r : rows) {
            if (r.status() != BizRefundStatus.APPROVED || r.approvedAmount() == null) {
                continue;
            }
            refunded += r.approvedAmount();
            if (r.type() == BizRefundType.STOP_PRORATED && r.feeAmount() != null) {
                fees += r.feeAmount();
            } else if (r.type() == BizRefundType.CLAIM) {
                claimed += r.approvedAmount();
            }
        }
        long refundable = Math.max(0, paid - refunded);
        return new BizRefundSummary(paid, refunded, refundable, Math.min(refundable, Math.max(0, fees - claimed)));
    }
}
