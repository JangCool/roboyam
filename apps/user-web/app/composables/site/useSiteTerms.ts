import type { SiteTerms } from '~/composables/site/useSiteTerms.types';
import type { SiteTermKey } from '~/composables/site/useSiteTerms.types';
import UtilSiteTerms from '~/utils/util-site-terms';

/**
 * 서비스 이름(용어) — 화면 전체가 공유하는 상태(`useState`). 메뉴·하단 탭·헤더 메뉴·뱃지가 읽는다.
 *
 * 상태에는 **운영자가 바꾼 이름만** 든다(서버 값 — 한 문자열이라 언어와 무관하게 그대로 쓴다). 바꾸지 않은 키는 지금 언어의 기본 이름(`siteTerms.*` 문구, `UtilSiteTerms.term`)이라
 * 언어를 바꾸면 같이 바뀐다(`useTranslate` 의 `t` 가 지금 언어를 읽으므로 `computed`·템플릿 안에서 부르면 다시 계산된다).
 *
 * ⚠ 단순 버전 — 원본(community)은 `GET /api/site/terms` 로 받은 이름을 상태와 localStorage 에 두고(`useSiteTermsLoader`, `plugins/01.site-terms.client.ts`)
 * 같은 응답의 사이트 이름·아이콘·슬로건도 채운다. 서버 값이 아직 없으므로 항상 기본 이름(공동구매·소모임·아이템샵·운영자 … / Group buys·Groups·Item shop·Admin …)이다.
 * 홈 화면을 만들 때 원본의 로더를 가져와 이 상태를 채운다(`useSiteName`·`useSiteTagline` 의 상태와 함께).
 *
 * ⚠ 컴포저블은 setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */
export function useSiteTerms() {
    const t = useTranslate();
    /** 운영자가 바꾼 이름만(`UtilSiteTerms.sanitize` 의 결과) — 없으면 `{}` */
    const custom = useState<Partial<SiteTerms>>('site-terms', () => ({}));
    /** 8개를 모두 채운 표 — 바꾼 이름은 그대로, 나머지는 지금 언어의 기본 이름 */
    const terms = computed(() => UtilSiteTerms.resolve(t, custom.value));

    /** 이름 하나 — 템플릿에서 `term('ITEM_SHOP')` 처럼 쓴다(표나 언어가 바뀌면 따라 바뀐다) */
    function term(key: SiteTermKey): string {
        return UtilSiteTerms.term(t, key, custom.value);
    }

    return { terms, term };
}
