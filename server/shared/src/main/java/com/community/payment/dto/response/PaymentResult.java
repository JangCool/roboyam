package com.community.payment.dto.response;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 결제 한 건의 결과(2a-1 화면 계약 {@code PaymentResult}) — 완료 API({@code POST /api/payments/{paymentId}/complete})와 시험 결제 다시 읽기({@code GET /api/dev/payments/{paymentId}})가
 * 같이 쓴다. 도메인 화면은 {@code accepted} 하나로 「완료」를 판단한다(확정한 목업 가정 5).
 *
 * @param orderType      주문 종류(화면 계약 밖 — 더해 둔 칸)
 * @param orderRefId     도메인 주문 ID(문자열 — Snowflake, 화면 계약 밖 — 더해 둔 칸)
 * @param anomaly        이상(없으면 null)
 * @param accepted       받아들인 결제인가(결제된 계열 + 이상 없음)
 * @param paidAmount     포트원 결제 금액(결제 전이면 null)
 * @param canceledAmount 취소된 금액 누계(포트원이 취소를 마친 것만)
 * @param cancel         이 결제의 자동 취소({@code AUTO_REJECT} — 받지 않은 결제의 전액 취소). 없으면 null
 */
public record PaymentResult(String paymentId, String orderType, String orderRefId, PaymentStatus status, PaymentAnomaly anomaly, boolean accepted, long amount,
                            Long paidAmount, long canceledAmount, Instant paidAt, Cancel cancel) {

    /** 자동 취소의 금액·진행 상태 */
    public record Cancel(long amount, CancelStatus status) {
    }

    /**
     * 결제 주문과 그 취소 줄들로 결과를 만든다. {@code cancel} = 종류가 {@code AUTO_REJECT} 인 줄 — 요청 키 {@code AUTO_REJECT:<결제 주문 ID>} 가 유일해 결제마다 많아야 하나지만,
     * 여럿이면 가장 나중에 넣은 줄(ID 가 가장 큰 것)을 고른다. 회원 취소·환불 등 다른 종류는 여기에 싣지 않는다(누계는 {@code canceledAmount}).
     */
    public static PaymentResult of(OrderRow order, List<CancelRow> cancels) {
        Cancel cancel = cancels.stream().filter(c -> c.kind() == CancelKind.AUTO_REJECT).max(Comparator.comparingLong(CancelRow::id))
                .map(c -> new Cancel(c.amount(), c.status())).orElse(null);
        return new PaymentResult(order.paymentId(), order.orderType().name(), Long.toString(order.orderRefId()), order.status(), order.anomaly(), order.accepted(),
                order.amount(), order.paidAmount(), order.canceledAmount(), order.paidAt(), cancel);
    }
}
