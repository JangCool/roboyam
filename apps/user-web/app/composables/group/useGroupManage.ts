import type { GroupDetail, GroupManageBlockedReason } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup, { type ActiveGroup } from '~/utils/util-group';

/**
 * 모임장 관리 화면(`/groups/{id}/manage/**`)의 틀 상태 — 상세를 받아 들어올 수 있는지 가르고, 관리용 값을 든다.
 * 서버 판정이 기준이다: 상세의 `permissions.canManage` 가 거짓이면 `denied`(서버도 쓰기에 `403 GROUP_LEADER_ONLY`), 모임장 고정 화면(`LEADER`)은 `canEditGroup` 까지 본다.
 * 매니저(3단계)의 「맡기지 않은 일」 안내는 아직 없다 — 체크형 권한 칸이 없는 서버는 `canManage` 로 채워 읽는다(`UtilGroup.permissionsOf`).
 * 정지·이메일 미인증(`manageBlockedReason`, 또는 쓰기의 `403 ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`)이면 화면은 열리고 쓰기 단추만 꺼진다(`block`).
 *
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */
export type GroupManageState = 'loading' | 'ready' | 'denied' | 'leader-only' | 'notfound' | 'inactive' | 'error';

/** 무엇이 있어야 들어오는가 — 관리 권한 누구나 · 모임장 고정 · 가입 신청 처리 · 멤버 강퇴 */
export type GroupManageNeed = 'ANY' | 'LEADER' | 'APPLICATIONS' | 'KICK';

/** 관리 화면이 쓰는 값 — 열린 소모임 + 지금 쓰기가 막힌 까닭 */
export interface ManagedGroup extends ActiveGroup {
    blockedReason: GroupManageBlockedReason | null;
}

function stateOf(detail: GroupDetail, need: GroupManageNeed): GroupManageState {
    if (detail.status === 'INACTIVE') return 'inactive';
    const active = UtilGroup.active(detail);
    if (!active) return 'error';
    const p = active.permissions;
    if (!p.canManage) return 'denied';
    if (need === 'LEADER' && !p.canEditGroup) return 'leader-only';
    if (need === 'APPLICATIONS' && !p.canHandleApplications) return 'denied';
    if (need === 'KICK' && !p.canKickMembers) return 'denied';
    return 'ready';
}

export function useGroupManage(groupId: string, need: GroupManageNeed = 'ANY') {
    const api = useGroupApi();
    const t = useTranslate();

    const state = ref<GroupManageState>('loading');
    const errorMessage = ref('');
    const group = ref<ManagedGroup | null>(null);

    let seq = 0;
    let abort: AbortController | null = null;
    onBeforeUnmount(() => {
        seq++;
        abort?.abort();
    });

    /** 상세 응답을 받아들인다(처음 받기·정보 수정 응답) */
    function apply(detail: GroupDetail): GroupManageState {
        const next = stateOf(detail, need);
        const active = next === 'ready' ? UtilGroup.active(detail) : null;
        group.value = active ? { ...active, blockedReason: active.permissions.manageBlockedReason } : null;
        errorMessage.value = '';
        state.value = next;
        return next;
    }

    /** 화면이 먼저 바꿔 보이는 값(모집 켜기·채팅 링크 저장) */
    function patch(values: Partial<ManagedGroup>): void {
        if (group.value) group.value = { ...group.value, ...values };
    }

    /** 쓰기가 막혔다는 답을 받았다 — 띠가 보이고 쓰기 단추가 꺼진다 */
    function block(reason: GroupManageBlockedReason): void {
        if (group.value) group.value = { ...group.value, blockedReason: reason };
    }

    async function load(): Promise<void> {
        const mine = ++seq;
        abort?.abort();
        abort = new AbortController();
        state.value = 'loading';
        errorMessage.value = '';
        try {
            const detail = await api.detail(groupId, abort.signal);
            if (mine !== seq) return;
            apply(detail);
        } catch (e) {
            if (mine !== seq) return;
            if (UtilGroup.isAborted(e) || UtilGroup.isSessionLost(e)) return; // 통로가 로그인 화면으로 보내는 중이다
            group.value = null;
            errorMessage.value = UtilBoardMessages.of(t, e);
            if (UtilGroup.isCode(e, 'GROUP_NOT_FOUND')) state.value = 'notfound';
            else if (UtilGroup.isCode(e, 'GROUP_INACTIVE')) state.value = 'inactive';
            else if (UtilGroup.isCode(e, 'GROUP_LEADER_ONLY')) state.value = 'denied';
            else state.value = 'error';
        }
    }

    void load();

    return { state, errorMessage, group, load, apply, patch, block };
}
