/**
 * 사이트 기본 정보(일반 화면의 사이트 이름·한 줄 소개)의 입력 규칙 — 서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 서버다.
 * 서버 규칙(설치 마법사와 같다, `docs/features/install-contract.md`): 사이트 이름 1~50자, 한 줄 소개 0~100자(비우면 문구 없음). 사이트 이름은 화면 로고·탭 제목에도 쓰이므로
 * `UtilAdminSite.clean` 과 같이 `<` `>` 제어 문자를 받지 않고, 한 줄 소개는 `<` `>` 를 받지 않는다(공개 사이트 설치 화면의 `UtilSite` 와 같다).
 * 글자 수는 앞뒤 공백을 뗀 뒤 코드포인트로 센다. 화면에 보이는 문구는 `util-site-setting-messages.ts`.
 */

/** 입력칸 이름 — 서버 오류 `fields` 의 이름과 같다 */
export type SiteSettingField = 'siteName' | 'tagline';

/** 로고 그림 파일의 문제 — 쓸 수 없는 형식 · 너무 큼 · 비어 있음 */
export type SiteLogoFileProblem = 'TYPE' | 'SIZE' | 'EMPTY';

/** SVG 소스의 문제 — 비어 있음 · 너무 김 · `<svg` 가 없음 */
export type SiteLogoSvgProblem = 'EMPTY' | 'LENGTH' | 'NOT_SVG';

/** 입력칸의 문제 — 비어 있음 · 너무 김 · 넣을 수 없는 글자 */
export type SiteSettingProblem = 'REQUIRED' | 'LENGTH' | 'CHARS';

export type SiteSettingProblems = Partial<Record<SiteSettingField, SiteSettingProblem>>;

// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f]/;

export default class UtilSiteSetting {
    /** 사이트 이름 최대 글자 수 — 서버와 같다 */
    static readonly NAME_MAX = 50;
    /** 한 줄 소개 최대 글자 수 — 서버와 같다 */
    static readonly TAGLINE_MAX = 100;
    /** 로고 그림 파일 최대 용량(2MB) — 서버(hamonica3 `FilePurpose.SYSTEM_LOGO`)와 같다 */
    static readonly LOGO_MAX_BYTES = 2 * 1024 * 1024;
    /** 로고로 올릴 수 있는 그림 형식. SVG 는 파일이 아니라 소스로 받는다(스크립트를 품을 수 있는 문서라서) */
    static readonly LOGO_ACCEPT = '.png,.jpg,.jpeg,.gif,.webp';
    static readonly LOGO_TYPES: readonly string[] = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];
    /** SVG 소스 최대 글자 수 — 서버(hamonica3 `MAX_SVG_LENGTH`)와 같다 */
    static readonly SVG_MAX_LENGTH = 100_000;

    /**
     * 사이트 이름의 문제.
     * @param {string} value - 입력한 사이트 이름.
     * @returns {SiteSettingProblem | null} 문제 없으면 null, 있으면 `REQUIRED` · `LENGTH` · `CHARS`.
     */
    static siteName(value: string): SiteSettingProblem | null {
        const text = value.trim();
        if (!text) return 'REQUIRED';
        if (Array.from(text).length > UtilSiteSetting.NAME_MAX) return 'LENGTH';
        if (/[<>]/.test(text) || CONTROL.test(text)) return 'CHARS';
        return null;
    }

    /**
     * 한 줄 소개의 문제 — 비워도 된다.
     * @param {string} value - 입력한 한 줄 소개.
     * @returns {SiteSettingProblem | null} 문제 없으면 null, 있으면 `LENGTH` · `CHARS`.
     */
    static tagline(value: string): SiteSettingProblem | null {
        const text = value.trim();
        if (Array.from(text).length > UtilSiteSetting.TAGLINE_MAX) return 'LENGTH';
        if (/[<>]/.test(text) || CONTROL.test(text)) return 'CHARS';
        return null;
    }

    /**
     * 두 칸을 한꺼번에 검사한다.
     * @param {string} siteName - 입력한 사이트 이름.
     * @param {string} tagline - 입력한 한 줄 소개.
     * @returns {SiteSettingProblems} 문제가 있는 칸만 담은 목록.
     */
    static validate(siteName: string, tagline: string): SiteSettingProblems {
        const out: SiteSettingProblems = {};
        const name = UtilSiteSetting.siteName(siteName);
        const line = UtilSiteSetting.tagline(tagline);
        if (name) out.siteName = name;
        if (line) out.tagline = line;
        return out;
    }

    /**
     * 고른 로고 그림 파일의 문제 — 보내기 전 편의 검사이고, 형식의 최종 판정은 서버가 파일 첫 바이트로 한다.
     * @param {{ size: number; type: string }} file - 고른 파일.
     * @returns {SiteLogoFileProblem | null} 문제 없으면 null.
     */
    static logoFile(file: { size: number; type: string }): SiteLogoFileProblem | null {
        if (file.size === 0) return 'EMPTY';
        if (!UtilSiteSetting.LOGO_TYPES.includes(file.type)) return 'TYPE';
        if (file.size > UtilSiteSetting.LOGO_MAX_BYTES) return 'SIZE';
        return null;
    }

    /**
     * SVG 소스의 문제 — 비었음·길이·`<svg` 포함 셋만 본다(그리는 자리가 `<img>` 라 위험한 마크업은 서버가 아니라 그리는 방식이 막는다).
     * @param {string} source - 붙여 넣은 SVG 소스.
     * @returns {SiteLogoSvgProblem | null} 문제 없으면 null.
     */
    static logoSvg(source: string): SiteLogoSvgProblem | null {
        const text = source.trim();
        if (!text) return 'EMPTY';
        if (text.length > UtilSiteSetting.SVG_MAX_LENGTH) return 'LENGTH';
        if (!text.toLowerCase().includes('<svg')) return 'NOT_SVG';
        return null;
    }

    /**
     * 저장된 값과 입력값이 다른 칸 수 — 앞뒤 공백은 떼고 견준다.
     * @param {{ siteName: string; tagline: string | null }} saved - 서버가 준 값.
     * @param {string} siteName - 입력한 사이트 이름.
     * @param {string} tagline - 입력한 한 줄 소개.
     * @returns {number} 바뀐 칸 수(0~2).
     */
    static changes(saved: { siteName: string; tagline: string | null }, siteName: string, tagline: string): number {
        return Number(siteName.trim() !== saved.siteName) + Number(tagline.trim() !== (saved.tagline ?? ''));
    }
}
