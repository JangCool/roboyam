import type { EmoticonPurchaseStart } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import UtilEmoticon from '~/utils/util-emoticon';

/**
 * 로보티콘 유료 팩 결제창 — 공용 `usePaymentCheckout` 에 로보티콘 값만 채운 것(공구 `useGroupBuyCheckout` 과 같은 방식). 팩 상세(`/robotikon/{id}`)가 쓴다.
 * - 모바일 리디렉션은 쿼리 없이 그 팩 상세로 돌아온다 — 팩 상세가 `onMounted` 에서 `resumeRedirect()` 를 부른다.
 * - 가짜 결제 결과·완료 요청은 `useEmoticonApi` 한 겹을 거쳐 결제 공용 API(`usePaymentTestApi().fakeCheckout` · `usePaymentApi().complete`)를 부른다.
 * - 포트원 구매자는 서버가 결제 준비 응답에 싣는다(`customer`). 없으면 회원 닉네임·이메일로 채운다.
 * ⚠ 페이지 setup 에서 부른다.
 */
export function useEmoticonCheckout(onResult?: (result: PaymentResult, target: EmoticonPurchaseStart) => void) {
    const api = useEmoticonApi();
    const { user } = useAuth();

    return usePaymentCheckout<EmoticonPurchaseStart>({
        pendingKey: 'roboyam-robotikon-payment-pending',
        redirectPath: (target) => UtilEmoticon.packPath(target.packId),
        customer: (target) => target.customer ?? { fullName: user.value?.nickname ?? '', phoneNumber: '', email: user.value?.email ?? '' },
        fakeCheckout: (paymentId, body) => api.fakeCheckout(paymentId, body),
        complete: (paymentId, body) => api.completePayment(paymentId, body),
        onResult,
    });
}
