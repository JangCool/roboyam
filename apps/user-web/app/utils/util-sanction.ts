import type { AccountStatus, Restriction } from '~/composables/auth/api/useAuthApi.types';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 제재 규칙 중 로그인 직후 안내 이동·전역 배너가 쓰는 것만 남긴 단순 버전.
 * 원본(community)의 `UtilSanction` 은 제재 안내·이의제기 화면 전체의 규칙을 담은 750줄짜리 파일이다 — 제재 안내 화면을 만들 때 원본으로 바꾼다.
 * (소셜 로그인 직후 표시(`LOGIN_MARK_KEY`)는 가져오지 않았다 — 연결된 소셜로 로그인하면 서버가 바로 `next` 로 보내므로 그 경우의 제재 안내 착륙은 아직 없다. 소셜 가입 마무리는 이 `afterLoginPath` 를 거친다.)
 */

/** 로그인 뒤 보낼 곳 — 안내가 필요 없으면 그냥 경로(쿼리 포함 문자열), 필요하면 `/sanction?next=<가려던 곳>` */
export type SanctionDestination = string | { path: string; query: Record<string, string> };

export default class UtilSanction {
    /** 제재 안내 화면 주소 */
    static readonly PATH = '/sanction';

    /**
     * 제재 안내 화면(`/sanction`, `/sanction/…`)의 주소인가
     * @param {string} path - 라우트 경로(쿼리 제외).
     * @returns {boolean} 제재 안내 화면이면 true.
     */
    static isSanctionPath(path: string): boolean {
        return path === UtilSanction.PATH || path.startsWith(`${UtilSanction.PATH}/`);
    }

    /**
     * 이용이 제한된 계정인가 — 기간 정지 중이거나 영구 정지
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @returns {boolean} 정지·영구 정지면 true.
     */
    static isRestricted(restriction: Restriction | null | undefined): boolean {
        const status: AccountStatus | undefined = restriction?.status;
        return status === 'SUSPENDED' || status === 'BANNED';
    }

    /**
     * 로그인 직후 제재 안내 화면을 먼저 보여야 하는가 — 정지·영구 정지 중이거나 확인하지 않은 경고가 있다
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @returns {boolean} 안내를 먼저 보여야 하면 true.
     */
    static needsNotice(restriction: Restriction | null | undefined): boolean {
        if (!restriction) return false;
        return UtilSanction.isRestricted(restriction) || (typeof restriction.pendingNotices === 'number' && restriction.pendingNotices > 0);
    }

    /**
     * 로그인이 끝난 뒤 보낼 곳 — 안내가 필요하면(정지·영구 정지 중이거나 확인하지 않은 경고) `/sanction?next=<가려던 곳>`, 아니면 가려던 곳(없거나 위험하면 `/`).
     * 가려던 곳이 이미 제재 안내 화면이면 겹치지 않는다. `next` 는 같은 사이트 안의 경로만 쓴다(`UtilRedirect.safeNext`).
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @param {unknown} next - 원래 가려던 곳(주소의 `next` 쿼리 값 — 배열일 수도 있다).
     * @returns {SanctionDestination} `navigateTo` 에 넘길 곳.
     */
    static afterLoginPath(restriction: Restriction | null | undefined, next: unknown): SanctionDestination {
        const target = UtilRedirect.safeNext(next);
        const path = target.split(/[?#]/)[0] ?? '/';
        if (!UtilSanction.needsNotice(restriction) || UtilSanction.isSanctionPath(path)) return target;
        return target === '/' ? { path: UtilSanction.PATH, query: {} } : { path: UtilSanction.PATH, query: { next: target } };
    }
}
