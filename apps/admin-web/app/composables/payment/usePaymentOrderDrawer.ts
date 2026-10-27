import type { ApiError } from '~/composables/common/useApiClient';
import type { PaymentOrderDetail, PaymentOrderRow } from '~/composables/payment/api/usePaymentAdminApi.types';

/**
 * 결제 상세 서랍(`PaymentOrderDrawer`)과 「포트원에서 다시 확인」 사유 대화상자의 흐름 — 결제 주문·결제 이상·웹훅 수신 내역·대사 불일치 네 화면이 같은 서랍을 연다.
 * 공유 상태(`useState`)가 아니다: 부를 때마다 그 화면만의 상태를 새로 만든다. 서버 호출은 `usePaymentAdminApi` 한 겹.
 * `onChanged` 는 다시 확인으로 결제가 바뀌었을 때 부르는 쪽 목록을 다시 읽게 한다.
 */
export function usePaymentOrderDrawer(onChanged?: () => void) {
    const api = usePaymentAdminApi();
    const t = useTranslate();
    const toast = useAppToast();
    const toApiError = useApiErrorOf();

    const open = ref(false);
    const openId = ref<string | null>(null);
    const summary = ref<PaymentOrderRow | null>(null);
    const detail = ref<PaymentOrderDetail | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);

    /** 늦게 도착한 앞 요청의 응답은 버린다(번호표) */
    let seq = 0;
    async function load(): Promise<void> {
        const id = openId.value;
        if (!id) return;
        const mine = ++seq;
        loading.value = true;
        error.value = null;
        try {
            const found = await api.order(id);
            if (mine !== seq) return;
            detail.value = found;
        } catch (e) {
            if (mine !== seq) return;
            detail.value = null;
            error.value = toApiError(e);
        } finally {
            if (mine === seq) loading.value = false;
        }
    }

    /** 결제 주문 번호로 연다 — 줄(`row`)이 있으면 받는 동안 머리를 채운다 */
    function show(id: string, row: PaymentOrderRow | null = null): void {
        summary.value = row;
        if (openId.value !== id || error.value) {
            openId.value = id;
            detail.value = null;
            void load();
        }
        open.value = true;
    }

    watch(open, (value) => {
        if (value) return;
        openId.value = null;
    });

    // ── 포트원에서 다시 확인 ─
    const recheckOpen = ref(false);
    const recheckBusy = ref(false);
    const recheckError = ref('');
    const recheckFieldError = ref('');

    function startRecheck(): void {
        if (!detail.value || recheckBusy.value) return;
        recheckError.value = '';
        recheckFieldError.value = '';
        recheckOpen.value = true;
    }

    async function submitRecheck(reason: string): Promise<void> {
        const target = detail.value;
        if (!target || recheckBusy.value) return;
        recheckBusy.value = true;
        recheckError.value = '';
        recheckFieldError.value = '';
        try {
            // 감사(돈 관련)는 서버가 남긴다
            const res = await api.recheck(target.id, reason);
            detail.value = res.detail;
            recheckOpen.value = false;
            if (res.outcome === 'CONFIRMED') toast.success(t('payment.recheck.confirmed'));
            else if (res.outcome === 'PG_NOT_PAID') toast.show(t('payment.recheck.notPaid'));
            else toast.show(t('payment.recheck.unchanged'));
            if (res.outcome === 'CONFIRMED') onChanged?.();
        } catch (e) {
            const failure = toApiError(e);
            const field = failure.status === 400 ? failure.field('reason') : '';
            if (field) recheckFieldError.value = field;
            else recheckError.value = failure.message;
        } finally {
            recheckBusy.value = false;
        }
    }

    return { open, summary, detail, loading, error, show, reload: load, recheckOpen, recheckBusy, recheckError, recheckFieldError, startRecheck, submitRecheck };
}
