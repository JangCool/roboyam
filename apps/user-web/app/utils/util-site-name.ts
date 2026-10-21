/**
 * 사이트 이름(탭 제목에 붙는 이름)의 순수 규칙 — 서버 응답 읽기, 이 브라우저에 마지막 값을 저장하는 글자 다루기, 탭 제목 만들기. 상태도 요청도 없다
 * (상태는 `useSiteInfo`·`useSiteName`, 서버 호출은 `useSiteInfoApi`).
 *
 * 이름은 설치 마법사·운영자 설정이 DB 에 정하는 값이고, 서버는 같은 값을 검색엔진용 초기 HTML 의 `<title>`·`og:site_name` 에 쓴다. 화면은 `GET /api/site/info` 의
 * `siteName` 으로 받아 쓴다. 받지 못했으면(없음·실패·깨진 응답) **탭 제목에는 이름을 붙이지 않고** 서버가 초기 HTML 에 넣은 제목을 그대로 둔다.
 *
 * 로고 글자·설명 문구처럼 **글자가 꼭 있어야 하는 곳**은 이름을 모를 때 `DEFAULT` 를 쓴다(`label`). 앱의 진짜 이름이 정해지면(D-01) `DEFAULT` 한 곳만 바꾸면 된다
 * (정적 파일 `public/manifest.webmanifest`·`public/sw.js` 는 값을 가져올 수 없어 같은 글자를 따로 적어 두었다 — 거기도 함께 바꾼다).
 */

/** 이름 길이 상한 — 서버(사이트 설정)와 같다(1~50자) */
const MAX_LENGTH = 50;

export default class UtilSiteName {
    /** 이름을 모를 때(받기 전·실패·설치 전) 쓰는 임시 이름 — 앱 이름이 정해지면(D-01) 여기만 바꾼다 */
    static readonly DEFAULT = 'SAMPLE';

    /** 제목과 이름 사이에 넣는 구분 글자 */
    static readonly SEPARATOR = ' · ';

    /**
     * 사이트 이름으로 쓸 수 있는 값인가 — 1~50자, `<`·`>`·줄바꿈·제어 문자 없음. 앞뒤 공백은 뗀다.
     * @param {unknown} value - 후보 값(서버 응답·저장해 둔 값).
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
     * 서버 응답에서 사이트 이름을 꺼낸다.
     * @param {unknown} raw - `GET /api/site/info` 응답.
     * @returns {string | null} 쓸 수 있는 이름. 없거나 깨졌으면 null.
     */
    static fromResponse(raw: unknown): string | null {
        if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return null;
        return UtilSiteName.clean((raw as Record<string, unknown>).siteName);
    }

    /**
     * 화면에 글자로 꼭 보여야 하는 사이트 이름 — 모르면 기본 이름.
     * @param {string | null | undefined} siteName - 사이트 이름. 모르면 null.
     * @returns {string} 보일 이름(항상 글자가 있다).
     */
    static label(siteName: string | null | undefined): string {
        return siteName || UtilSiteName.DEFAULT;
    }

    /** 홈 제목에서 이름과 슬로건 사이에 넣는 구분 글자 — 서버가 초기 HTML 의 홈 제목에 쓰는 것과 같다(`SeoPages.homeTitle`) */
    static readonly HOME_SEPARATOR = ' — ';

    /**
     * 홈 제목 — 슬로건이 있으면 "이름 — 슬로건", 없으면 이름만. 이름을 모르면 null(서버가 넣은 초기 제목이 남는다).
     * @param {string | null | undefined} siteName - 사이트 이름. 모르면 null.
     * @param {string | null | undefined} tagline - 사이트 슬로건. 없으면 null.
     * @returns {string | null} 홈 탭 제목. 정할 수 없으면 null.
     */
    static homeTitle(siteName: string | null | undefined, tagline: string | null | undefined): string | null {
        if (!siteName) return null;
        return tagline ? `${siteName}${UtilSiteName.HOME_SEPARATOR}${tagline}` : siteName;
    }

    /**
     * 탭 제목 — 쪽 제목 뒤에 이름을 붙인다. 쪽 제목이 없거나 이름과 같으면(홈) 이름만, 홈 제목("이름 — 슬로건")이면 그대로, 이름을 모르면 쪽 제목 그대로(둘 다 없으면 null 이라 초기 제목이 남는다).
     * @param {string | undefined} title - 쪽이 정한 제목.
     * @param {string | null | undefined} siteName - 사이트 이름. 모르면 null.
     * @param {string | null | undefined} tagline - 사이트 슬로건(홈 제목을 알아보는 데 쓴다). 없으면 null.
     * @returns {string | null} 탭에 보일 제목. 정할 수 없으면 null.
     */
    static formatTitle(title: string | undefined, siteName: string | null | undefined, tagline?: string | null): string | null {
        if (!siteName) return title || null;
        if (!title || title === siteName || title === UtilSiteName.homeTitle(siteName, tagline)) return title || siteName;
        return `${title}${UtilSiteName.SEPARATOR}${siteName}`;
    }
}
