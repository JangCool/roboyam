import type { CommerceBusiness, CommerceInfo } from '~/composables/commerce/api/useCommerceInfoApi.types';

/** 진행 중인 `info` 호출 — 푸터와 공구 화면이 동시에 물어도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;

/**
 * 커머스 공개 정보(판매 스위치·사업자 정보) 공유 상태 — 사이트 푸터(`AppFooter`)와 공구 상세 하단이 같이 본다. 서버와 말하는 것은 `useCommerceInfoApi`.
 * `undefined` = 아직 안 물어봄, `null` = 못 받음(사업자 정보 칸만 빠지고 화면은 뜬다).
 *
 * - `shownBusiness` — 판매 스위치가 켜져 있고(`salesOpen`) 칸이 다 찼을 때(`ready`)만 사업자 정보, 아니면 null(그리지 않는다 — 계약 결정 56).
 */
export function useCommerceInfo() {
    const api = useCommerceInfoApi();
    const state = useState<CommerceInfo | null | undefined>('commerce-info', () => undefined);

    async function ensureLoaded(): Promise<CommerceInfo | null> {
        if (state.value !== undefined) return state.value;
        inflight ??= api
            .info()
            .then((res) => {
                state.value = res;
            })
            .catch(() => {
                state.value = null;
            })
            .finally(() => {
                inflight = null;
            });
        await inflight;
        return state.value ?? null;
    }

    const shownBusiness = computed<CommerceBusiness | null>(() => {
        const info = state.value;
        return info && info.salesOpen && info.ready ? info.business : null;
    });

    return { info: state, shownBusiness, ensureLoaded };
}
