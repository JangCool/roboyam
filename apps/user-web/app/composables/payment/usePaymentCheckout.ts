import type { PaymentFakeCheckoutRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import type { PaymentCheckoutNote, PaymentCheckoutOptions, PaymentCheckoutTarget } from '~/composables/payment/usePaymentCheckout.types';
import UtilPortone from '~/utils/util-portone';

/** 리디렉션으로 돌아올 때 포트원이 붙이는 쿼리 — 처리한 뒤 지운다(`?mock=` 같은 다른 쿼리는 둔다) */
const REDIRECT_QUERY = ['paymentId', 'code', 'message', 'pgCode', 'pgMessage', 'transactionType', 'txId'];

/**
 * 결제창 하나를 열고 끝까지 가는 공용 흐름 — `/dev/payment`(시험 주문)·공구 주문서·내 주문(결제 다시 열기·반품비 부족분)이 같이 쓴다. 계약 commerce-groupbuy 결정 1.
 * 서버 주소는 모른다: 결제 시작은 도메인 API 가 하고(그 응답을 `open(target)` 에 넘긴다), 가짜 PG 남기기·완료 요청은 옵션으로 받는다.
 *
 * - 게이트웨이가 가짜(`FAKE`)면 `fakeOpen` 이 켜진다 — 화면이 `PaymentFakeCheckoutDialog` 를 그리고 `submitFake`·`closeFake` 를 잇는다.
 * - 포트원이면 브라우저 SDK `requestPayment`(카드) — 결제했으면 완료 요청을 보낸다(금액·상태는 서버가 PG 에 다시 물어 정한다 — SDK 응답의 금액은 믿지 않는다).
 *   실패·창 닫기(SDK 응답 `code`)는 완료 요청을 보내지 않고 `note` 에 까닭을 둔다(창 닫기 판정은 `UtilPortone.isUserCancel`).
 * - 모바일은 결제사 화면으로 갔다가 `redirectPath`(쿼리 없이)로 돌아온다 — 떠나기 전 대상을 sessionStorage(`pendingKey`)에 두고, 돌아온 쿼리 `paymentId`(실패면 `code`·`message`)가
 *   같을 때만 복원해 이어 간 뒤 쿼리를 지운다. 돌아올 화면이 `onMounted` 에서 `resumeRedirect()` 를 부른다.
 *
 * ```ts
 * const pay = usePaymentCheckout<MyTarget>({ pendingKey: 'roboyam-x-pending', redirectPath: (t) => '/x', customer: () => ({…}), fakeCheckout: api.fakeCheckout });
 * pay.open(await api.start());             // 결제창 열기
 * onMounted(() => void pay.resumeRedirect());
 * ```
 * ⚠ 페이지 setup 에서 부른다(`useRoute`·`useRouter` 를 붙잡는다).
 */
export function usePaymentCheckout<T extends PaymentCheckoutTarget>(options: PaymentCheckoutOptions<T>) {
    const t = useTranslate();
    const route = useRoute();
    const router = useRouter();
    const paymentApi = usePaymentApi();

    /** 지금 결제창을 연(또는 열) 대상 */
    const target = ref<T | null>(null) as Ref<T | null>;
    /** 완료 요청의 결과 — 받으면 같은 대상의 결제창은 다시 열지 않는다 */
    const result = ref<PaymentResult | null>(null);

    const fakeOpen = ref(false);
    const fakeBusy = ref(false);
    const fakeError = ref('');
    const completeBusy = ref(false);
    const completeError = ref('');
    const note = ref<PaymentCheckoutNote | null>(null);
    /** 포트원이 준 실패 문구와 코드(`code`·`pgCode`) — 개발 화면은 그대로 보여 PG 마다 다른 취소 코드를 확인한다 */
    const noteDetail = ref('');
    /** 포트원 결제창이 열려 있는 동안 */
    const portoneBusy = ref(false);
    const busy = computed(() => completeBusy.value || portoneBusy.value || fakeBusy.value);

    /** 새 대상으로 갈아 끼운다(결과·까닭·오류를 비운다) — 결제창은 열지 않는다 */
    function reset(next: T | null): void {
        target.value = next;
        result.value = null;
        note.value = null;
        noteDetail.value = '';
        completeError.value = '';
        fakeError.value = '';
        fakeOpen.value = false;
    }

    /** 결제창을 연다 — 대상을 주면 갈아 끼운 뒤 연다. 이미 결과를 받았거나 진행 중이면 아무것도 하지 않는다 */
    function open(next?: T): void {
        if (next) reset(next);
        const current = target.value;
        if (!current || result.value !== null || completeBusy.value || portoneBusy.value) return;
        note.value = null;
        noteDetail.value = '';
        completeError.value = '';
        if (current.checkout.gateway === 'PORTONE') {
            void openPortone(current);
            return;
        }
        fakeError.value = '';
        fakeOpen.value = true;
    }

    /* ── 포트원 결제창(브라우저 SDK) ── */

    function savePending(value: T): void {
        try {
            window.sessionStorage.setItem(options.pendingKey, JSON.stringify(value));
        } catch {
            // 저장할 수 없는 환경(사생활 보호 창 등) — 팝업 결제는 그대로 되고, 리디렉션에서 돌아오면 이어 가지 못할 뿐이다
        }
    }

    function takePending(): T | null {
        try {
            const raw = window.sessionStorage.getItem(options.pendingKey);
            window.sessionStorage.removeItem(options.pendingKey);
            const found = raw ? (JSON.parse(raw) as T) : null;
            return found?.checkout?.paymentId && found.orderRefId ? found : null;
        } catch {
            return null;
        }
    }

    /** SDK 응답의 실패 — 사용자가 닫았으면 CLOSED, 아니면 PG_FAILED. 완료 요청은 보내지 않는다 */
    function settleFailure(code: string, message: string | undefined, pgCode: string | undefined): void {
        note.value = UtilPortone.isUserCancel(code, message) ? 'CLOSED' : 'PG_FAILED';
        noteDetail.value = [message, `code ${code}`, pgCode ? `pgCode ${pgCode}` : ''].filter(Boolean).join(' · ');
    }

    async function openPortone(value: T): Promise<void> {
        const { storeId, channelKey, paymentId, orderName, amount } = value.checkout;
        if (!import.meta.client) return;
        if (!storeId || !channelKey) {
            completeError.value = options.missingConfigMessage ? options.missingConfigMessage() : t('payment.portoneMissing');
            return;
        }
        portoneBusy.value = true;
        savePending(value);
        let response;
        try {
            const { default: PortOne } = await import('@portone/browser-sdk/v2');
            response = await PortOne.requestPayment({
                storeId,
                channelKey,
                paymentId,
                orderName,
                totalAmount: amount,
                currency: 'KRW',
                payMethod: 'CARD',
                customer: options.customer(value),
                redirectUrl: new URL(router.resolve({ path: options.redirectPath(value) }).href, window.location.origin).href,
            });
        } catch (e) {
            takePending();
            completeError.value = e instanceof Error ? e.message : String(e);
            return;
        } finally {
            portoneBusy.value = false;
        }
        // 리디렉션된 경우 — 이 화면을 떠난다. 돌아오면 `resumeRedirect` 가 이어 간다
        if (response === undefined) return;
        takePending();
        if (response.code !== undefined) {
            settleFailure(response.code, response.message, response.pgCode);
            return;
        }
        // 금액·상태는 서버가 PG 에 다시 물어 정한다 — 응답의 값은 쓰지 않는다
        await complete(value);
    }

    /**
     * 모바일 리디렉션에서 돌아왔다 — 떠나기 전 둔 대상과 결제 번호가 같을 때만 이어 간다.
     * @returns 이어 간 대상(같은 결제가 아니었거나 돌아온 것이 아니면 null)
     */
    async function resumeRedirect(): Promise<T | null> {
        const query = route.query;
        const paymentId = typeof query.paymentId === 'string' ? query.paymentId : null;
        if (!paymentId) return null;
        const code = typeof query.code === 'string' && query.code ? query.code : null;
        const message = typeof query.message === 'string' ? query.message : undefined;
        const pgCode = typeof query.pgCode === 'string' && query.pgCode ? query.pgCode : undefined;
        const pending = takePending();
        const rest = { ...query };
        for (const key of REDIRECT_QUERY) delete rest[key];
        await router.replace({ query: rest });
        if (!pending || pending.checkout.paymentId !== paymentId) return null;
        target.value = pending;
        result.value = null;
        if (code) {
            settleFailure(code, message, pgCode);
            return pending;
        }
        await complete(pending);
        return pending;
    }

    /* ── 가짜 결제창 ── */

    /** (가짜 결제창) 닫기·Esc — 회원이 결제창을 닫은 것과 같다 */
    function closeFake(): void {
        if (fakeBusy.value) return;
        fakeOpen.value = false;
        note.value = 'CLOSED';
    }

    /** (가짜 결제창) 고른 결과를 가짜 PG 에 남기고, 결제했으면 완료 요청 */
    async function submitFake(body: PaymentFakeCheckoutRequest): Promise<void> {
        const current = target.value;
        if (!current || fakeBusy.value) return;
        fakeBusy.value = true;
        fakeError.value = '';
        try {
            await options.fakeCheckout(current.checkout.paymentId, body);
        } catch (e) {
            fakeError.value = e instanceof Error ? e.message : String(e);
            fakeBusy.value = false;
            return;
        }
        fakeBusy.value = false;
        fakeOpen.value = false;
        if (body.outcome === 'FAILED') {
            note.value = 'FAILED';
            return;
        }
        await complete(current);
    }

    /** 완료 요청 — 결제창이 끝난 뒤 서버가 PG 에 다시 물어 확정한다 */
    async function complete(value: T): Promise<void> {
        completeBusy.value = true;
        completeError.value = '';
        try {
            const body = { orderType: value.orderType, orderRefId: value.orderRefId };
            const done = options.complete ? await options.complete(value.checkout.paymentId, body) : await paymentApi.complete(value.checkout.paymentId, body);
            result.value = done;
            options.onResult?.(done, value);
        } catch (e) {
            completeError.value = e instanceof Error ? e.message : String(e);
        } finally {
            completeBusy.value = false;
        }
    }

    return {
        target,
        result,
        fakeOpen,
        fakeBusy,
        fakeError,
        completeBusy,
        completeError,
        portoneBusy,
        busy,
        note,
        noteDetail,
        reset,
        open,
        resumeRedirect,
        closeFake,
        submitFake,
        complete,
    };
}
