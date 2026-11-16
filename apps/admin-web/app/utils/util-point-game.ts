/**
 * 포인트 게임 관리자 설정 규칙 — 당첨 확인 링크(복권 사업자 이름·주소)의 입력 검사. 상태도 요청도 없다.
 * 판정은 서버다(shared `PointGameSettingsService`). 화면은 미리 알리고 저장 단추를 끈다.
 * 회원 화면(user-web `UtilPointGame.safeLink`)도 `https://` 주소가 아니면 링크를 그리지 않는다 — 같은 규칙.
 */

/** 사업자 이름 문제 — 비었음 · 너무 김 */
export type PointGameProviderNameProblem = 'REQUIRED' | 'TOO_LONG';
/** 링크 문제 — 비었음 · 주소 모양 아님 · https 아님 · 너무 김 */
export type PointGameCheckUrlProblem = 'REQUIRED' | 'INVALID' | 'NOT_HTTPS' | 'TOO_LONG';

export default class UtilPointGame {
    /** 사업자 이름 최대 글자 수(가정) */
    static readonly PROVIDER_NAME_MAX = 30;
    /** 링크 최대 글자 수(가정) */
    static readonly CHECK_URL_MAX = 300;

    /**
     * 사업자 이름 검사 — 앞뒤 공백을 뺀 1~30자
     * @param {string} text - 입력.
     * @returns {PointGameProviderNameProblem | null} 문제. 없으면 null.
     */
    static providerNameProblem(text: string): PointGameProviderNameProblem | null {
        const value = text.trim();
        if (!value) return 'REQUIRED';
        if (value.length > UtilPointGame.PROVIDER_NAME_MAX) return 'TOO_LONG';
        return null;
    }

    /**
     * 링크 검사 — `https://` 로 시작하는 절대 주소(호스트가 있어야 한다), 300자 이하
     * @param {string} text - 입력.
     * @returns {PointGameCheckUrlProblem | null} 문제. 없으면 null.
     */
    static checkUrlProblem(text: string): PointGameCheckUrlProblem | null {
        const value = text.trim();
        if (!value) return 'REQUIRED';
        if (value.length > UtilPointGame.CHECK_URL_MAX) return 'TOO_LONG';
        let url: URL;
        try {
            url = new URL(value);
        } catch {
            return 'INVALID';
        }
        if (url.protocol !== 'https:') return url.protocol === 'http:' ? 'NOT_HTTPS' : 'INVALID';
        if (!url.hostname || !url.hostname.includes('.')) return 'INVALID';
        return null;
    }
}
