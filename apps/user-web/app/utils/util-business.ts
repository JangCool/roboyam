import type { AuthUser, Role } from '~/composables/auth/api/useAuthApi.types';

/** 사용자 메뉴의 사업자 줄 — `CENTER` 사업자 센터(신청했거나 사업자 전용 계정) · `APPLY` 사업자 신청(일반 회원, 신청 전) */
export type BusinessEntry = 'CENTER' | 'APPLY';

/**
 * 사업자 규칙 중 셸(헤더 메뉴·햄버거·글쓰기 버튼)이 쓰는 것만 남긴 단순 버전 — 원본(community)의 `UtilBusiness` 는 사업자등록번호 검사·신청 입력 규칙까지 담은 큰 파일이다.
 */
export default class UtilBusiness {
    /** 사업자 센터 주소 */
    static readonly CENTER_PATH = '/business';

    /**
     * 사업자 전용 계정인가(H-2) — 일반 게시판 글쓰기·소모임·쪽지 보내기·출석·포인트를 못 쓴다.
     * @param {Role | null | undefined} role - 계정의 역할.
     * @returns {boolean} `BUSINESS` 이면 true.
     */
    static isBusinessAccount(role: Role | null | undefined): boolean {
        return role === 'BUSINESS';
    }

    /**
     * 사용자 메뉴의 사업자 줄 — 사업자 전용 계정이거나 신청한 적이 있으면 "사업자 센터", 일반 회원이고 신청 전이면 "사업자 신청", 운영진·비회원은 줄이 없다.
     * @param {Pick<AuthUser, 'role' | 'businessStatus'> | null | undefined} user - 로그인한 사람.
     * @returns {BusinessEntry | null} 줄의 종류. 줄이 없으면 null.
     */
    static entryOf(user: Pick<AuthUser, 'role' | 'businessStatus'> | null | undefined): BusinessEntry | null {
        if (!user) return null;
        if (user.role === 'BUSINESS') return 'CENTER';
        const status = user.businessStatus ?? 'NONE';
        if (status !== 'NONE') return 'CENTER';
        return user.role === 'USER' ? 'APPLY' : null;
    }
}
