/**
 * 관리자 화면에 붙는 사이트 이름 규칙 — 로고 글자와 탭 제목. 상태도 요청도 없다(이름은 `useAdminAuth().siteName`).
 *
 * 이름은 서버가 `me`·로그인 응답의 `siteName` 으로 준다(비로그인에도 준다 — 로그인 화면의 로고·탭 제목이 쓴다). 받지 못했으면(설치 전·서버에 닿지 못함) `DEFAULT` 를 쓴다.
 * 공개 사이트와 같은 기본 이름을 쓴다 — 앱의 진짜 이름이 정해지면(D-01) 공개 사이트의 `UtilSiteName.DEFAULT` 와 함께 바꾼다.
 */

/** 이름 길이 상한 — 서버(사이트 설정)와 같다(1~50자) */
const MAX_LENGTH = 50;

export default class UtilAdminSite {
    /** 이름을 모를 때 쓰는 임시 이름 */
    static readonly DEFAULT = 'SAMPLE';

    /** 쪽 제목과 이름 사이의 구분 글자 */
    static readonly SEPARATOR = ' · ';

    /**
     * 사이트 이름으로 쓸 수 있는 값인가 — 1~50자, `<`·`>`·줄바꿈·제어 문자 없음. 앞뒤 공백은 뗀다.
     * @param {unknown} value - 후보 값(서버 응답).
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
     * 화면에 글자로 꼭 보여야 하는 사이트 이름 — 모르면 기본 이름.
     * @param {string | null | undefined} siteName - 사이트 이름. 모르면 null.
     * @returns {string} 보일 이름(항상 글자가 있다).
     */
    static label(siteName: string | null | undefined): string {
        return siteName || UtilAdminSite.DEFAULT;
    }

    /**
     * 탭 제목 — "쪽 제목 · 이름 관리자". 쪽 제목이 없으면 "이름 관리자".
     * @param {string | undefined} title - 쪽이 정한 제목.
     * @param {string} siteLabel - 사이트 이름(`label` 을 거친 값).
     * @param {string} adminWord - "관리자" 에 해당하는 지금 언어의 말.
     * @returns {string} 탭에 보일 제목.
     */
    static formatTitle(title: string | undefined, siteLabel: string, adminWord: string): string {
        const base = `${siteLabel} ${adminWord}`;
        return title ? `${title}${UtilAdminSite.SEPARATOR}${base}` : base;
    }
}
