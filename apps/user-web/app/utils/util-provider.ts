import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 소셜 로그인 제공자 표시 정보.
 * 어떤 제공자를 쓸 수 있는지는 서버가 정한다(`useAuthApi().providers()`) — 여기는 이름·모양만 안다.
 * 이름 글자는 `i18n/locales/{ko,en}/provider.ts` 에 있고, 여기는 어느 키인지만 안다(번역 함수 `t` 를 첫 인자로 받는다).
 */

/** 제공자 id → 이름 문구 키 — 글자는 그릴 때 `t` 로 얻는다 */
const NAME_KEYS: Record<string, string> = {
    kakao: 'provider.kakao',
    naver: 'provider.naver',
    google: 'provider.google',
};

/** 버튼 안 작은 글자 표지(시안의 "카"·"N"·"G") */
const MARKS: Record<string, string> = {
    kakao: '카',
    naver: 'N',
    google: 'G',
};

/** 표지 색 — 제공자 브랜드 색 */
const MARK_CLASSES: Record<string, string> = {
    kakao: 'bg-[#fee500] text-[#191919]',
    naver: 'bg-[#03c75a] text-white',
    google: 'bg-white text-[#4285f4] ring-1 ring-inset ring-[#dadce0]',
};

/** 받침이 없거나 ㄹ 이면 true */
function endsWithoutBatchim(word: string, allowRieul: boolean): boolean | null {
    const last = word.charCodeAt(word.length - 1);
    if (last < 0xac00 || last > 0xd7a3) return null; // 한글이 아니면 모른다
    const batchim = (last - 0xac00) % 28;
    return batchim === 0 || (allowRieul && batchim === 8);
}

export default class UtilProvider {
    /**
     * 제공자의 이름을 지금 언어로 돌려줍니다.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} id - 제공자 id(`kakao` · `naver` · `google`).
     * @param {string} [fallback] - 모르는 제공자일 때 쓸 이름. 없으면 "소셜"(`provider.other`).
     * @returns {string} 이름.
     */
    static name(t: Translate, id: string | null | undefined, fallback?: string): string {
        const key = id ? NAME_KEYS[id.toLowerCase()] : undefined;
        return key ? t(key) : (fallback ?? t('provider.other'));
    }

    /**
     * 이름이 들어가는 문구의 자리표시자 값 — `{provider}`(이름) · `{providerRo}`(이름+로/으로) · `{providerEul}`(이름+을/를).
     * 조사는 한국어 문장만 쓰고 영어 문장은 `{provider}` 만 쓴다(영어 이름에도 계산은 하지만 쓰이지 않는다).
     * @param {string} name - 지금 언어의 이름(`name()` 의 결과).
     * @returns {{ provider: string; providerRo: string; providerEul: string }} 자리표시자 값.
     */
    static params(name: string): { provider: string; providerRo: string; providerEul: string } {
        return { provider: name, providerRo: UtilProvider.withRo(name), providerEul: UtilProvider.withEul(name) };
    }

    /**
     * 버튼 안에 넣을 작은 글자 표지를 돌려줍니다.
     * @param {string} id - 제공자 id.
     * @returns {string} 표지 글자. 모르는 제공자면 id 의 첫 글자(대문자).
     */
    static mark(id: string): string {
        return MARKS[id.toLowerCase()] ?? id.slice(0, 1).toUpperCase();
    }

    /**
     * 표지의 색 클래스(제공자 브랜드 색)를 돌려줍니다.
     * @param {string} id - 제공자 id.
     * @returns {string} 배경·글자색 클래스 문자열.
     */
    static markClass(id: string): string {
        return MARK_CLASSES[id.toLowerCase()] ?? 'bg-surface-200 text-surface-700 dark:bg-surface-700 dark:text-surface-100';
    }

    /**
     * "카카오" → "카카오로", "라인" → "라인으로" (ㄹ 받침은 로)
     * @param {string} word - 단어.
     * @returns {string} "로" 또는 "으로"를 붙인 글자. 한글이 아니면 "(으)로".
     */
    static withRo(word: string): string {
        const open = endsWithoutBatchim(word, true);
        return word + (open === null ? '(으)로' : open ? '로' : '으로');
    }

    /**
     * "카카오" → "카카오를", "구글" → "구글을"
     * @param {string} word - 단어.
     * @returns {string} "를" 또는 "을"을 붙인 글자. 한글이 아니면 "을(를)".
     */
    static withEul(word: string): string {
        const open = endsWithoutBatchim(word, false);
        return word + (open === null ? '을(를)' : open ? '를' : '을');
    }
}
