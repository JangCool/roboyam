/**
 * 문구(i18n)에 쓰는 **순수 규칙** — 번역 함수 타입, 지원 언어, 동적 이름(`name`/`nameEn`) 고르기, 문구 파일 모으기. 상태도 요청도 없다.
 * 이 파일은 `vue`·`~/` 를 쓰지 않는 순수 TypeScript 다. (`useI18n`·`t()` 는 setup 문맥이 필요해 여기서 부르지 않는다 — 번역 함수는 인자로 받는다.)
 */

/** 언어 코드 — 지원하는 언어는 이 둘이다(`@roboyam/i18n` 의 `locales`) */
export type Language = 'ko' | 'en';

/**
 * 번역 함수 — `useI18n().t` 와 같은 모양. 메시지 유틸(`utils/util-*-messages.ts`)의 함수는 이것을 **첫 인자**로 받는다.
 * 자리표시자는 `{name}` 꼴이고 값은 두 번째 인자로 준다(`t('mail.hostPlaceholder')`, `t('common.totalCount', { count: 3 })`).
 */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** 문구 묶음 — 키는 `업무.무엇` 두 마디로 쓰이고, 값은 글자이거나 한 단계 더 중첩된 묶음이다(vue-i18n 의 메시지 사전 모양) */
export type MessageTree = { [key: string]: string | MessageTree };

export default class UtilI18n {
    /** 지원하는 언어 */
    static readonly LANGUAGES: readonly Language[] = ['ko', 'en'];

    /** 기본(폴백) 언어 */
    static readonly DEFAULT_LANGUAGE: Language = 'ko';

    /**
     * 언어 코드를 지원하는 언어로 다듬는다 — 모르는 값은 기본 언어.
     * @param {unknown} value - 후보(서버 응답·저장해 둔 값).
     * @returns {Language} `ko` 또는 `en`.
     */
    static languageOf(value: unknown): Language {
        return value === 'en' ? 'en' : UtilI18n.DEFAULT_LANGUAGE;
    }

    /**
     * 동적 이름 — 운영자가 관리 화면에서 만들고 바꾸는 이름(게시판·메뉴·말머리 …)을 지금 언어로 고른다.
     * 번역 파일에는 둘 수 없는 이름이라 항목이 기본 이름 `name`(한국어)과 선택 영문명 `nameEn` 을 함께 가진다.
     * 언어가 `en` 이고 `nameEn` 이 비어 있지 않으면 `nameEn`, 아니면 `name`.
     * @param {string} name - 기본 이름(한국어).
     * @param {string | null | undefined} nameEn - 영문명. 없거나 공백뿐이면 쓰지 않는다.
     * @param {string} language - 지금 언어(`ko`·`en`).
     * @returns {string} 화면에 보일 이름.
     */
    static localizedName(name: string, nameEn: string | null | undefined, language: string): string {
        if (language !== 'en') return name;
        const english = typeof nameEn === 'string' ? nameEn.trim() : '';
        return english !== '' ? english : name;
    }
}
