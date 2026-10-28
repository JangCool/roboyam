import type { GroupBuyPaymentStart } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';

/**
 * 공구 결제창 — 공용 `usePaymentCheckout` 에 공구 값만 채운 것. 주문서(`/group-buys/{id}/order`)와 내 주문 상세(결제 다시 열기·반품비 부족분)가 같이 쓴다.
 * - 모바일 리디렉션은 쿼리 없이 그 주문 상세(`/me/orders/{orderNo}`)로 돌아온다(계약 결정 13) — 주문 상세가 `onMounted` 에서 `resumeRedirect()` 를 부른다.
 *   두 화면이 같은 sessionStorage 키를 쓰므로 주문서에서 떠난 결제도 주문 상세에서 이어 간다.
 * - 가짜 결제 결과·완료 요청은 `useGroupBuyApi` 한 겹을 거친다(목업 단계 — 실제는 결제 공용 API).
 * - 포트원 구매자는 서버가 결제 시작 응답에 싣는다(`customer`). 없으면 회원 닉네임·이메일로 채운다.
 * ⚠ 페이지 setup 에서 부른다.
 */
export function useGroupBuyCheckout(onResult?: (result: PaymentResult, target: GroupBuyPaymentStart) => void) {
    const api = useGroupBuyApi();
    const { user } = useAuth();

    return usePaymentCheckout<GroupBuyPaymentStart>({
        pendingKey: 'roboyam-group-buy-payment-pending',
        redirectPath: (target) => UtilGroupBuy.myOrderPath(target.orderNo),
        customer: (target) => target.customer ?? { fullName: user.value?.nickname ?? '', phoneNumber: '', email: user.value?.email ?? '' },
        fakeCheckout: (paymentId, body) => api.fakeCheckout(paymentId, body),
        complete: (paymentId, body) => api.completePayment(paymentId, body),
        onResult,
    });
}
