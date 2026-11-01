import { mockHotdealForbidden } from '~/composables/hotdeal/api/mock/hotdeal-mock';

/**
 * 핫딜 화면의 조치 권한 — 감추는 것은 편의이고 막는 것은 서버다. 권한 코드는 `util-permission-catalog.ts`(이름 그대로).
 *  · `canManage` 종료·재개 · 의심 후보 무시 · 핫딜 설정 저장(`hotdeal.manage` — 모든 핫딜 게시판)
 *  · `canDomain` 링크 규칙 추가·고치기·지우기(`hotdeal.domain.manage`)
 *  · `canReadReports` 신고 관리로 가는 링크(`report.read`) · `canReadMembers` 회원 관리로 가는 링크(`member.read`) · `canReadBoards` 게시판 설정 링크(`board.read`) · `canReadPosts` 글 관리 링크(`post.read`)
 * 게시판 매니저·스텝은 그 게시판 글만 종료·재개한다(사용자 결정 2026-11-01) — 그 판정은 서버가 하고, 관리자 앱은 `hotdeal.manage` 만 본다.
 */
export function useHotdealPermission() {
    const { can } = useAdminPermission();
    // MOCK: `?mock=forbidden` 이면 조치 권한이 없는 운영자처럼 보인다 — 서버가 붙으면 이 줄과 import 를 지운다
    const mockDenied = mockHotdealForbidden();

    return {
        canManage: computed(() => !mockDenied && can('hotdeal.manage')),
        canDomain: computed(() => !mockDenied && can('hotdeal.domain.manage')),
        canReadReports: computed(() => can('report.read')),
        canReadMembers: computed(() => can('member.read')),
        canReadBoards: computed(() => can('board.read')),
        /** `post.read` — 글 관리 서랍(숨김·사유 창)으로 가는 바로가기(의심 후보 서랍 「글 관리에서 숨기기」) */
        canReadPosts: computed(() => can('post.read')),
    };
}
