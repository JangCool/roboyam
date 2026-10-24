import type { TermsSummary } from '~/composables/terms/api/useTermsApi.types';

/** 상태 열쇠 — 서비스 이름(용어)의 `site-terms` 와 다르다 */
const STATE_KEY = 'terms-summary';

/** 진행 중인 `summary` 호출 — 레이아웃·가입 화면이 동시에 물어도 한 번만 보낸다 */
let inflight: Promise<void> | null = null;

/**
 * 약관 요약(`GET /api/terms/summary`) — 화면 전체가 공유하는 상태. 로그인 카드 아래 링크(레이아웃 `auth`)·가입 약관 동의의 「보기」(`TermsAgreement`)·기본 레이아웃의 변경 안내 띠(`TermsChangeNotice`)가 같이 쓴다.
 * `undefined` = 아직 안 물어봄, `null` = 못 받음. **불러오기 전·실패 시 링크는 숨긴다**(`showLinks` false). 동의 체크는 이 값과 관계없이 늘 필수다.
 * ⚠ 서비스 이름(용어 — `useSiteTerms`)과 다른 기능이다.
 *
 * @returns `summary` 상태 · `showLinks` 링크를 보일지 · `ensureLoaded()` 아직 안 받았으면 받는다(실패해도 던지지 않는다).
 */
export function useTermsSummary() {
    const api = useTermsApi();
    const summary = useState<TermsSummary | null | undefined>(STATE_KEY, () => undefined);

    const showLinks = computed(() => summary.value?.showLinks === true);

    async function ensureLoaded(): Promise<void> {
        if (summary.value !== undefined) return;
        inflight ??= api
            .summary()
            .then((res) => {
                summary.value = res;
            })
            .catch(() => {
                summary.value = null;
            })
            .finally(() => {
                inflight = null;
            });
        await inflight;
    }

    return { summary, showLinks, ensureLoaded };
}
