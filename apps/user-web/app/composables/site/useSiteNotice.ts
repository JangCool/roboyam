import type { SiteNoticeBarResponse } from '~/composables/site/api/useSiteNoticeApi.types';

/**
 * 공지 띠에 보일 공지(`GET /api/site/notice-bar`) — 앱이 열릴 때 한 번 받는다(화면을 옮겨도 다시 묻지 않는다).
 * 못 받으면 띠를 그리지 않는다(오류를 보이지 않는다 — 공지 띠가 없어도 화면은 쓸 수 있다).
 * @returns `notice` — `undefined` 아직 안 물어봄 · `null` 못 받음 · 응답(꺼져 있으면 `items` 가 비어 있다)
 */
export function useSiteNotice() {
    const api = useSiteNoticeApi();
    const notice = useState<SiteNoticeBarResponse | null | undefined>('site-notice-bar', () => undefined);
    const loading = useState('site-notice-bar-loading', () => false);

    async function ensureLoaded(): Promise<void> {
        if (notice.value !== undefined || loading.value) return;
        loading.value = true;
        try {
            notice.value = await api.noticeBar();
        } catch {
            notice.value = null;
        } finally {
            loading.value = false;
        }
    }

    return { notice, ensureLoaded };
}
