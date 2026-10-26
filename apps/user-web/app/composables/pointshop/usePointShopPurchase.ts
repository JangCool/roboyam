import type { PointShopPurchaseResult } from '~/composables/pointshop/api/usePointShopApi.types';
import type { FeatureRestriction } from '~/utils/util-feature-restriction';
import { ApiError } from '~/composables/common/useApiClient';
import UtilFeatureRestriction from '~/utils/util-feature-restriction';
import UtilIdempotency from '~/utils/util-idempotency';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

/**
 * 포인트 상점 구매 한 번의 흐름 — 확인 대화상자(`PointShopPurchaseDialog`)의 상태와 `POST /api/point-shop/purchases` 호출. 품목 상세와 상점 아바타 탭이 같이 쓴다(화면마다 새로 부른다 — 공유 상태가 아니다).
 * - 멱등 키는 대화상자 한 번에 하나 — 네트워크가 끊겨 다시 누르면 같은 키로 보낸다. 서버가 판정해 거절하면(4xx) 새 키.
 * - 오류는 코드로 가른다: `403 MEMBER_RESTRICTED` → `restriction`(기능 제한 안내) · `400 VALIDATION fields.agreeRefundNotice` → `agreeError` · 그 밖은 `error`(서버 문구 그대로, 429 는 기다릴 시간).
 *   `429 REQUEST_RATE_LIMITED` 는 `Retry-After` 초를 붙인다. 멱등 키 오류(`400 IDEMPOTENCY_KEY_REQUIRED|INVALID`·`409 IDEMPOTENCY_KEY_REUSED|IDEMPOTENCY_IN_PROGRESS`)는 서버 문구 그대로 + 새 키.
 *   `409` 는 화면이 본 판매·재고·보유·잔액이 낡았다는 뜻이라 `buy` 의 `onConflict` 를 부른다(부르는 쪽이 다시 읽는다).
 * ⚠ setup 에서 부른다(안에서 `useTranslate`·`useDisplayTime`·API 컴포저블을 잡는다).
 */
export function usePointShopPurchase() {
    const api = usePointShopApi();
    const t = useTranslate();
    const time = useDisplayTime();

    const open = ref(false);
    const agreed = ref(false);
    const buying = ref(false);
    const error = ref('');
    const agreeError = ref('');
    const restriction = ref<FeatureRestriction | null>(null);
    let idempotencyKey = '';

    watch(agreed, () => (agreeError.value = ''));

    /** 대화상자를 연다 — 동의·오류를 비우고 새 멱등 키 */
    function start(): void {
        agreed.value = false;
        error.value = '';
        agreeError.value = '';
        restriction.value = null;
        idempotencyKey = UtilIdempotency.newKey();
        open.value = true;
    }

    /**
     * 산다 — 성공하면 대화상자를 닫고 결과를 돌려준다. 실패하면 대화상자에 이유를 두고 null.
     * @param itemId 품목 ID
     * @param onConflict `409` 일 때 부른다(품목을 다시 읽기)
     */
    async function buy(itemId: string, onConflict?: () => void): Promise<PointShopPurchaseResult | null> {
        if (buying.value || !agreed.value) return null;
        buying.value = true;
        error.value = '';
        agreeError.value = '';
        try {
            const result = await api.purchase({ itemId, agreeRefundNotice: agreed.value }, idempotencyKey);
            open.value = false;
            return result;
        } catch (e) {
            if (e instanceof ApiError && e.status < 500) idempotencyKey = UtilIdempotency.newKey();
            const limited = UtilFeatureRestriction.fromError(e);
            if (limited) restriction.value = limited;
            else if (e instanceof ApiError && e.code === 'VALIDATION' && e.field('agreeRefundNotice')) agreeError.value = e.field('agreeRefundNotice') ?? '';
            else {
                error.value = UtilPointShopMessages.error(t, time.value, e);
                if (e instanceof ApiError && e.status === 409) onConflict?.();
            }
            return null;
        } finally {
            buying.value = false;
        }
    }

    return { open, agreed, buying, error, agreeError, restriction, start, buy };
}
