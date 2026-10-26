import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SiteTermKey, SiteTerms } from '~/composables/site/useSiteTerms.types';
import koSiteTerms from '../../i18n/locales/ko/siteTerms';

/**
 * 서비스 이름(용어)의 순수 규칙 — 기본 이름 고르기, 서버 응답 읽기, 이 브라우저에 마지막 값을 저장하는 글자 다루기, 조사 붙이기. 상태도 요청도 없다
 * (상태는 `useSiteTerms`, 서버 호출은 `useSiteTermsApi`).
 *
 * **기본 이름은 번역 파일의 `siteTerms.<키>` 다**(`i18n/locales/{ko,en}/siteTerms.ts`) — 한국어는 지금 화면에 박혀 있던 글자 그대로라, 서버 응답이 없거나 실패하거나 깨져도 한국어 화면은 지금과 똑같이 보인다.
 * 운영자가 바꾼 이름(서버 값)은 한 문자열이라 언어와 무관하게 그대로 쓴다. 서버 값이 한국어 기본 이름과 같으면 "바꾸지 않은 이름"으로 보고 기본 이름(지금 언어)을 쓴다.
 * 범용 커뮤니티라 특정 테마 이름을 기본으로 넣지 않는다.
 * 운영자가 각 기능 설정 화면의 이름 구획에서 바꾸면 사이트 전체의 눈에 보이는 이름(메뉴 글자·페이지 제목·"…더보기"·19금 표시)이 따라 바뀐다. 길고 설명적인 문장·오류·정책 문구는 바꾸지 않는다.
 * 메뉴 그룹 이름(주제 게시판·어울림 광장·안내 …)은 용어가 아니다 — 운영자가 메뉴 구성에서 정하고 서버가 `GET /api/menu` 로 준다(`UtilMenu`).
 */

/** 이름 길이 상한 — 서버와 같다(1~12자) */
const MAX_LENGTH = 12;

/** 한글 낱말 끝 글자에 받침이 있는가 — 조사(이/가·을/를)를 고른다. 한글이 아니면 null */
function hasFinalConsonant(word: string): boolean | null {
    const last = word.trim().slice(-1);
    if (!last) return null;
    const code = last.charCodeAt(0) - 0xac00;
    return code >= 0 && code <= 11171 ? code % 28 !== 0 : null;
}

export default class UtilSiteTerms {
    /** 용어 키 8개 — 서버가 주는 순서와 같다(기능 이름 6개 뒤에 공식 뱃지 이름 `ADMIN_BADGE`·`STAFF_BADGE`) */
    static readonly KEYS: readonly SiteTermKey[] = [
        'GROUP_BUY',
        'GROUPS',
        'POINT_SHOP',
        'POPULAR',
        'LATEST',
        'ADULT_LABEL',
        'ADMIN_BADGE',
        'STAFF_BADGE',
    ];

    /** 기본 이름의 문구 키 — 운영자가 바꾸기 전까지 이 이름이다(글자는 `siteTerms.*`, 지금 언어로 읽는다) */
    static readonly DEFAULT_KEYS: Readonly<Record<SiteTermKey, keyof typeof koSiteTerms>> = Object.freeze({
        GROUP_BUY: 'groupBuy',
        GROUPS: 'groups',
        POINT_SHOP: 'pointShop',
        POPULAR: 'popular',
        LATEST: 'latest',
        ADULT_LABEL: 'adultLabel',
        ADMIN_BADGE: 'adminBadge',
        STAFF_BADGE: 'staffBadge',
    });

    /** 이 브라우저에 마지막으로 받은 이름을 남기는 저장 키 — 다음 방문의 첫 화면이 깜빡이지 않게 한다 */
    static readonly STORAGE_KEY = 'roboyam:site-terms';

    /** 이름 길이 상한(글자 수) */
    static readonly MAX_LENGTH = MAX_LENGTH;

    /**
     * 이름 한 개가 쓸 수 있는 값인가 — 1~12자, `<`·`>`·줄바꿈·제어 문자 없음(서버 검증과 같다). 앞뒤 공백은 뗀다.
     * @param {unknown} value - 후보 값.
     * @returns {string | null} 앞뒤 공백을 뗀 이름. 쓸 수 없으면 null.
     */
    static clean(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        const text = value.trim();
        if (text === '' || Array.from(text).length > MAX_LENGTH) return null;
        // eslint-disable-next-line no-control-regex
        if (/[<>\u0000-\u001f\u007f]/.test(text)) return null;
        return text;
    }

    /**
     * 서버 응답(또는 저장해 둔 값)을 읽는다 — 쓸 수 있는 값만 담는다. 없거나 깨진 키, 모르는 키, 한국어 기본 이름과 같은 값(바꾸지 않은 이름)은 담지 않는다 — 그 키는 기본 이름(지금 언어)을 쓴다.
     * @param {unknown} raw - `{ terms: {…} }` 응답, 또는 `terms` 객체 자체.
     * @returns {Partial<SiteTerms>} 운영자가 바꾼 이름만 담긴 표.
     */
    static sanitize(raw: unknown): Partial<SiteTerms> {
        const root = typeof raw === 'object' && raw !== null && !Array.isArray(raw) ? (raw as Record<string, unknown>) : {};
        const source = typeof root.terms === 'object' && root.terms !== null && !Array.isArray(root.terms) ? (root.terms as Record<string, unknown>) : root;
        const out: Partial<SiteTerms> = {};
        for (const key of UtilSiteTerms.KEYS) {
            const value = Object.prototype.hasOwnProperty.call(source, key) ? UtilSiteTerms.clean(source[key]) : null;
            if (value !== null && value !== koSiteTerms[UtilSiteTerms.DEFAULT_KEYS[key]]) out[key] = value;
        }
        return out;
    }

    /**
     * 이름 한 개를 꺼낸다 — 운영자가 바꾼 이름이 있으면 그것, 없으면 지금 언어의 기본 이름.
     * @param {Translate} t - 번역 함수(`useTranslate()` 또는 `useI18n().t`).
     * @param {SiteTermKey} key - 용어 키.
     * @param {Partial<SiteTerms> | null | undefined} terms - 운영자가 바꾼 이름 표. 없으면 기본 이름.
     * @returns {string} 화면에 쓸 이름.
     */
    static term(t: Translate, key: SiteTermKey, terms?: Partial<SiteTerms> | null): string {
        const own = terms?.[key];
        return typeof own === 'string' && own !== '' ? own : t(`siteTerms.${UtilSiteTerms.DEFAULT_KEYS[key]}`);
    }

    /**
     * 이름 8개를 모두 채운 표 — 바꾼 이름은 그대로, 나머지는 지금 언어의 기본 이름.
     * @param {Translate} t - 번역 함수.
     * @param {Partial<SiteTerms> | null | undefined} terms - 운영자가 바꾼 이름 표.
     * @returns {SiteTerms} 8개가 모두 채워진 표.
     */
    static resolve(t: Translate, terms?: Partial<SiteTerms> | null): SiteTerms {
        const out = {} as SiteTerms;
        for (const key of UtilSiteTerms.KEYS) out[key] = UtilSiteTerms.term(t, key, terms);
        return out;
    }

    /**
     * 이름 표를 저장할 글자로 — 운영자가 바꾼 이름만 담는다(바꾸지 않았으면 `{}`).
     * @param {Partial<SiteTerms>} terms - 운영자가 바꾼 이름 표.
     * @returns {string} JSON 글자.
     */
    static serialize(terms: Partial<SiteTerms>): string {
        const changed: Partial<SiteTerms> = {};
        for (const key of UtilSiteTerms.KEYS) if (terms[key] !== undefined) changed[key] = terms[key];
        return JSON.stringify({ terms: changed });
    }

    /**
     * 저장해 둔 글자를 읽는다 — 없거나 깨졌으면 null(기본 이름으로 시작한다).
     * @param {string | null | undefined} text - 저장소에서 읽은 글자.
     * @returns {Partial<SiteTerms> | null} 운영자가 바꾼 이름 표. 읽을 수 없으면 null.
     */
    static parseStored(text: string | null | undefined): Partial<SiteTerms> | null {
        if (!text) return null;
        try {
            return UtilSiteTerms.sanitize(JSON.parse(text));
        } catch {
            return null;
        }
    }

    /**
     * 주격 조사를 붙인다 — "공동구매가"·"인기글이". 한글이 아닌 낱말은 "이(가)" 로 둔다.
     * @param {string} word - 낱말.
     * @returns {string} 조사가 붙은 낱말.
     */
    static iGa(word: string): string {
        const final = hasFinalConsonant(word);
        return final === null ? `${word}이(가)` : `${word}${final ? '이' : '가'}`;
    }

    /**
     * 목적격 조사를 붙인다 — "소모임을"·"포인트 상점을". 한글이 아닌 낱말은 "을(를)" 로 둔다.
     * @param {string} word - 낱말.
     * @returns {string} 조사가 붙은 낱말.
     */
    static eulReul(word: string): string {
        const final = hasFinalConsonant(word);
        return final === null ? `${word}을(를)` : `${word}${final ? '을' : '를'}`;
    }

    /**
     * 보조사 "은/는"을 붙인다 — "소모임은"·"공동구매는". 한글이 아닌 낱말은 "은(는)" 로 둔다.
     * @param {string} word - 낱말.
     * @returns {string} 조사가 붙은 낱말.
     */
    static eunNeun(word: string): string {
        const final = hasFinalConsonant(word);
        return final === null ? `${word}은(는)` : `${word}${final ? '은' : '는'}`;
    }

    /**
     * 접속 조사 "과/와"를 붙인다 — "소모임과"·"공동구매와". 한글이 아닌 낱말은 "과(와)" 로 둔다.
     * @param {string} word - 낱말.
     * @returns {string} 조사가 붙은 낱말.
     */
    static gwaWa(word: string): string {
        const final = hasFinalConsonant(word);
        return final === null ? `${word}과(와)` : `${word}${final ? '과' : '와'}`;
    }

    /**
     * 방향 조사 "으로/로"를 붙인다 — "소모임으로"·"포인트 상점으로"·"공구로". 받침이 ㄹ 이면 "로"다. 한글이 아닌 낱말은 "(으)로" 로 둔다.
     * @param {string} word - 낱말.
     * @returns {string} 조사가 붙은 낱말.
     */
    static euroRo(word: string): string {
        const last = word.trim().slice(-1);
        const code = last ? last.charCodeAt(0) - 0xac00 : -1;
        if (code < 0 || code > 11171) return `${word}(으)로`;
        const jong = code % 28;
        return `${word}${jong === 0 || jong === 8 ? '로' : '으로'}`;
    }

    /**
     * 서술 "이에요/예요"를 붙인다 — "소모임이에요"·"포인트 상점이에요"·"공구예요". 한글이 아닌 낱말은 "이에요" 로 둔다.
     * @param {string} word - 낱말.
     * @returns {string} 서술이 붙은 낱말.
     */
    static ieyo(word: string): string {
        return `${word}${hasFinalConsonant(word) === false ? '예요' : '이에요'}`;
    }
}
