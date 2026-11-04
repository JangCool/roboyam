/**
 * 제재·이의제기 화면들(제재·이의제기·회원 상세·신고 서랍)이 같이 보는 권한 — 계약 `member-sanction-contract.md` 정책 3(서버 `Permission`). 감추는 것은 편의이고 막는 것은 서버다.
 *   - `canRead` 조회 `sanction.read`(목록·현황·이의제기 목록·상세)
 *   - `canManage` 경고·기간 정지 부여, 기간 정지 해제 `sanction.manage`
 *   - `canBan` 영구 정지·단계 건너뛰기·영구 정지 해제 `sanction.ban`(민감 — 이것만 있어도 영구 정지를 주고 풀 수 있다)
 *   - `canAnswer` 이의제기 인용·기각 `appeal.manage`
 */
export function useSanctionPermission() {
    const { can } = useAdminPermission();
    return {
        canRead: computed(() => can('sanction.read')),
        canManage: computed(() => can('sanction.manage')),
        canBan: computed(() => can('sanction.ban')),
        canAnswer: computed(() => can('appeal.manage')),
    };
}
