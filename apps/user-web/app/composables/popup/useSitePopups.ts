import type { SitePopupsResponse } from '~/composables/popup/api/useSitePopupApi.types';

/**
 * 팝업에 보일 목록(`GET /api/site/popups`)과 「이번 방문 동안 닫은 팝업」 — 앱이 열릴 때 한 번 받는다(화면을 옮겨도 다시 묻지 않는다).
 * 못 받으면 팝업을 띄우지 않는다(오류를 보이지 않는다 — 팝업이 없어도 화면은 쓸 수 있다).
 * 「닫기」로 닫은 팝업은 이 앱이 열려 있는 동안만 기억한다(새로 고치면 다시 뜬다). 「오늘 하루 보지 않기」 등은 브라우저 저장(`UtilSitePopup`)이다.
 * @returns `popups` — `undefined` 아직 안 물어봄 · `null` 못 받음 · 응답 / `closedIds` — 이번 방문 동안 닫은 팝업 ID
 */
export function useSitePopups() {
    const api = useSitePopupApi();
    const popups = useState<SitePopupsResponse | null | undefined>('site-popups', () => undefined);
    const loading = useState('site-popups-loading', () => false);
    const closedIds = useState<string[]>('site-popups-closed', () => []);

    async function ensureLoaded(): Promise<void> {
        if (popups.value !== undefined || loading.value) return;
        loading.value = true;
        try {
            popups.value = await api.popups();
        } catch {
            popups.value = null;
        } finally {
            loading.value = false;
        }
    }

    /** 이번 방문 동안 닫는다 */
    function closeForVisit(ids: readonly string[]): void {
        closedIds.value = [...new Set([...closedIds.value, ...ids])];
    }

    return { popups, closedIds, ensureLoaded, closeForVisit };
}
