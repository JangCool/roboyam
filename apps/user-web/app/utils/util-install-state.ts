/**
 * 설치 상태(설치 전 · 설치 후)를 다루는 가장 작은 순수 로직 — 설치 상태 공유 상태의 키·마법사 주소·설치 전 오류 판정.
 * 통로(`useApiClient`)·설치 상태 컴포저블(`useInstallState`)·라우트 가드가 함께 쓴다. 마법사 자체의 로직은 `util-install.ts` 에 있다.
 *
 * 일부러 아무것도 import 하지 않는다 — `useApiClient` 가 이 파일을 쓰므로 `ApiError` 를 가져오면 서로를 가져오는 고리가 생긴다(그래서 오류도 상태·코드 숫자·글자로만 본다).
 */
export default class UtilInstallState {
    /** 설치 상태를 담는 공유 상태(`useState`)의 키 — `useInstallState` 와 `useApiClient`(503 `NOT_INSTALLED`)가 같은 값을 본다 */
    static readonly STATE_KEY = 'install-state';
    /** 마법사 화면 주소 */
    static readonly PATH = '/install';

    /**
     * 마법사 화면 주소인가 — 설치 전에는 이 주소만 열리고, 설치 후에는 이 주소가 닫힌다.
     * @param {string} path - 이동할 곳의 경로(쿼리 제외).
     * @returns {boolean} `/install` 이거나 그 아래이면 true.
     */
    static isInstallPath(path: string): boolean {
        return path === UtilInstallState.PATH || path.startsWith(`${UtilInstallState.PATH}/`);
    }

    /**
     * 설치 전이라 막혔다(`503 NOT_INSTALLED`) — 설치 전에는 `/api/system/installation/**` 외의 API 가 모두 이 오류다.
     * @param {number} status - HTTP 상태 코드.
     * @param {string} code - 서버 오류 코드.
     * @returns {boolean} 설치 전 오류이면 true.
     */
    static isNotInstalled(status: number, code: string): boolean {
        return status === 503 && code === 'NOT_INSTALLED';
    }
}
