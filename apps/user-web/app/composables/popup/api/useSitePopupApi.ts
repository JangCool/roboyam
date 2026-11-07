import type { SitePopupsResponse } from '~/composables/popup/api/useSitePopupApi.types';

/**
 * 공개 팝업 API — 서버 주소 문자열(`/api/site/popups`)이 있는 **유일한 곳**이다. 상태는 없다(상태는 `useSitePopups`). 계약: `docs/features/site-popup-contract.md`.
 * 비로그인도 받는다. 노출을 켰고 기간 안인 팝업만 목록 차례대로 온다.
 */
const PATH = {
    popups: '/api/site/popups',
};

export function useSitePopupApi() {
    const client = useApiClient();

    return {
        /** 지금 뜰 수 있는 팝업(노출 켬·기간 안) — 목록 차례 */
        popups: (signal?: AbortSignal): Promise<SitePopupsResponse> => client.get<SitePopupsResponse>(PATH.popups, { signal }),
    };
}
