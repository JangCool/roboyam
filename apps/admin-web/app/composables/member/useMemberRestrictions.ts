import type { ApiError } from '~/composables/common/useApiClient';
import type { MemberRestriction, MemberRestrictionCreateRequest } from '~/composables/member/api/useMemberRestrictionApi.types';
import UtilMemberRestriction from '~/utils/util-member-restriction';

/**
 * 회원 상세 서랍의 「기능 제한」 구획이 보는 상태 — 한 회원의 기능 제한 목록과 걸기·해제(커머스 0-4, commerce.md 12-3).
 * 회원 관리 화면이 부를 때마다 새 상태가 생긴다(`useMemberDetail` 과 같은 방식 — 여러 화면이 공유하지 않는다). 서버 호출은 `useMemberRestrictionApi` 한 겹.
 * 다른 회원을 연 뒤에 늦게 도착한 앞 요청의 응답은 버린다(번호표).
 *
 * ⚠ 컴포넌트 setup 에서 부른다.
 *
 * @returns `items` 목록(아직 없으면 null) · `loading` · `error` · `open(memberId)` · `retry()` · `clear()` ·
 *          `create(body)`·`lift(id, reason)` — 성공하면 null, 실패하면 `ApiError`(대화상자가 칸 아래·폼 아래에 서버 문구를 보인다).
 */
export function useMemberRestrictions() {
    const api = useMemberRestrictionApi();
    const errorOf = useApiErrorOf();

    const items = ref<MemberRestriction[] | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let memberId: string | null = null;
    let ticket = 0;

    async function open(id: string): Promise<void> {
        if (memberId !== id) items.value = null;
        memberId = id;
        const mine = ++ticket;
        loading.value = true;
        error.value = null;
        try {
            const found = await api.list(id);
            if (mine === ticket) items.value = found;
        } catch (e) {
            if (mine === ticket) {
                items.value = null;
                error.value = errorOf(e);
            }
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function retry(): void {
        if (memberId) void open(memberId);
    }

    function clear(): void {
        ticket++;
        memberId = null;
        items.value = null;
        error.value = null;
        loading.value = false;
    }

    /** 제한 걸기 — 성공하면 목록에 끼운다 */
    async function create(body: MemberRestrictionCreateRequest): Promise<ApiError | null> {
        const id = memberId;
        if (!id) return null;
        try {
            const created = await api.create(id, body);
            if (memberId === id) items.value = UtilMemberRestriction.sorted([created, ...(items.value ?? [])]);
            return null;
        } catch (e) {
            return errorOf(e);
        }
    }

    /** 진행 중인 제한 해제 — 성공하면 그 줄을 바꾼다. 이미 끝났다(409)면 목록을 새로 받는다 */
    async function lift(restrictionId: string, reason: string): Promise<ApiError | null> {
        const id = memberId;
        if (!id) return null;
        try {
            const lifted = await api.lift(id, restrictionId, { reason });
            if (memberId === id) items.value = UtilMemberRestriction.sorted((items.value ?? []).map((r) => (r.id === lifted.id ? lifted : r)));
            return null;
        } catch (e) {
            const failure = errorOf(e);
            if (failure.code === 'RESTRICTION_NOT_ACTIVE' || failure.code === 'RESTRICTION_NOT_FOUND') void open(id);
            return failure;
        }
    }

    return { items, loading, error, open, retry, clear, create, lift };
}
