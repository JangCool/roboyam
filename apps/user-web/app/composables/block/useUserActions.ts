import type { Role } from '~/composables/auth/api/useAuthApi.types';
import UtilBlock from '~/utils/util-block';
import UtilBlockMessages from '~/utils/util-block-messages';

/**
 * 회원 ⋯ 메뉴가 하는 일 — **이 사용자 차단 · 차단 해제 · 메모 남기기**(community `useUserActions`). 프로필의 ⋯ 시트·쪽지 대화방의 ⋯ 시트·차단 관리가 같은 흐름을 쓴다.
 *
 *  - 차단: 확인 대화상자(효과 안내) → 서버 → 완료 토스트 + **실행 취소**. 비회원은 로그인 유도(`prompt('block')`). 운영진·운영자는 막는다(서버도 403).
 *  - 차단 해제: 바로 풀고 토스트 + **실행 취소**(다시 차단).
 *  - 메모: 셸에 하나 있는 메모 시트(`useMemoSheet` · `MemoSheet`)를 연다.
 * 바꾸면 이 기기에서 방금 바꾼 것으로 적어 둔다(`useBlockOverrides` — 떠 있는 줄이 바로 접히고 펴진다). 서버와 말하는 것은 `useBlockApi` 다.
 *
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다 — 흐름 안에서는 부를 때 잡아 둔 것만 쓴다).
 */

/** ⋯ 메뉴가 다루는 회원 — 메모·차단 여부는 **방금 바꾼 것까지 겹친** 값이다 */
export interface UserActionTarget {
    id: string;
    nickname: string;
    role: Role;
    /** 내가 남긴 메모(없으면 null) */
    memo: string | null;
    /** 내가 차단했는가 */
    blocked: boolean;
}

export type UserActionKind = 'block' | 'unblock' | 'memo';

/** 메뉴 항목을 눌렀다 — 무엇을 누구에게 */
export interface UserAction {
    kind: UserActionKind;
    target: UserActionTarget;
}

/** `targetOf` 가 읽는 회원 정보 — 작성자 표시와 같은 모양 */
export interface UserActionSource {
    id: string | null;
    nickname: string;
    role: Role | null;
    memo?: string | null;
    withdrawn?: boolean;
}

export interface UserActionsOptions {
    /** 차단 상태가 바뀐 뒤(차단·해제·실행 취소) 부른다 */
    onBlockChanged?: (userId: string, blocked: boolean) => void;
}

export function useUserActions(options: UserActionsOptions = {}) {
    const api = useBlockApi();
    const { user } = useAuth();
    const { prompt } = useLoginPrompt();
    const { confirm } = useConfirmDialog();
    const toast = useAppToast();
    const overrides = useBlockOverrides();
    const memoSheet = useMemoSheet();
    const t = useTranslate();

    /** 요청이 가는 중 — 같은 흐름이 겹쳐 두 번 가지 않게 */
    let busy = false;

    /**
     * 메뉴가 다룰 회원 — 탈퇴한 회원(id 없음)·나 자신이면 null. 메모·차단 여부에는 방금 바꾼 것을 겹친다.
     * @param source 작성자 표시·프로필을 그 모양으로 바꾼 것
     * @param blocked 서버가 준 "내가 차단했다"(프로필의 `blockedByMe`)
     */
    function targetOf(source: UserActionSource | null | undefined, blocked: boolean): UserActionTarget | null {
        if (!source || source.withdrawn || !source.id) return null;
        if (source.id === user.value?.id) return null;
        return {
            id: source.id,
            nickname: source.nickname,
            role: source.role ?? 'USER',
            memo: overrides.memoOf(source.id, source.memo ?? null),
            blocked: overrides.userBlocked(source.id, blocked),
        };
    }

    function applyBlocked(userId: string, blocked: boolean): void {
        overrides.setBlocked(userId, blocked);
        options.onBlockChanged?.(userId, blocked);
    }

    /** 확인을 거쳐 차단한다 — 차단했으면 true */
    async function block(target: UserActionTarget): Promise<boolean> {
        if (!user.value) {
            prompt('block');
            return false;
        }
        if (busy || !UtilBlock.canBlock(target.role)) return false;
        const ok = await confirm({
            title: t('block.confirmTitle', { nickname: target.nickname }),
            message: t('block.confirmMessage'),
            confirmLabel: t('block.confirmLabel'),
            tone: 'danger',
        });
        if (!ok) return false;
        busy = true;
        try {
            await api.block(target.id);
        } catch (e) {
            toast.error(UtilBlockMessages.failure(t, t('block.blockFailed'), e), 6000);
            return false;
        } finally {
            busy = false;
        }
        applyBlocked(target.id, true);
        toast.show(t('block.blockedToast', { nickname: target.nickname }), {
            life: UtilBlock.UNDO_LIFE_MS,
            action: { label: t('block.undo'), run: () => void undoBlock(target) },
        });
        return true;
    }

    async function undoBlock(target: UserActionTarget): Promise<void> {
        try {
            await api.unblock(target.id);
        } catch (e) {
            toast.error(UtilBlockMessages.failure(t, t('block.undoBlockFailed'), e), 6000);
            return;
        }
        applyBlocked(target.id, false);
        toast.show(t('block.blockUndone', { nickname: target.nickname }));
    }

    /** 차단을 푼다 — 확인 없이 바로 풀고 토스트의 「실행 취소」로 되돌린다 */
    async function unblock(target: UserActionTarget): Promise<boolean> {
        if (!user.value) {
            prompt('block');
            return false;
        }
        if (busy) return false;
        busy = true;
        try {
            await api.unblock(target.id);
        } catch (e) {
            toast.error(UtilBlockMessages.failure(t, t('block.unblockFailed'), e), 6000);
            return false;
        } finally {
            busy = false;
        }
        applyBlocked(target.id, false);
        toast.show(t('block.unblockedToast', { nickname: target.nickname }), {
            life: UtilBlock.UNDO_LIFE_MS,
            action: { label: t('block.undo'), run: () => void redoBlock(target) },
        });
        return true;
    }

    async function redoBlock(target: UserActionTarget): Promise<void> {
        try {
            await api.block(target.id);
        } catch (e) {
            toast.error(UtilBlockMessages.failure(t, t('block.reblockFailed'), e), 6000);
            return;
        }
        applyBlocked(target.id, true);
        toast.show(t('block.unblockUndone', { nickname: target.nickname }));
    }

    /** 메모 시트를 연다 — 비회원은 로그인 유도 */
    function openMemo(target: UserActionTarget, onSaved?: (userId: string, memo: string | null) => void): void {
        if (!user.value) {
            prompt('memo');
            return;
        }
        memoSheet.open({ id: target.id, nickname: target.nickname, memo: target.memo }, onSaved);
    }

    /** 메뉴 항목 하나를 실행한다 — `UserActionItems` 의 `action` 을 그대로 받는다 */
    async function run(action: UserAction): Promise<void> {
        if (action.kind === 'block') await block(action.target);
        else if (action.kind === 'unblock') await unblock(action.target);
        else openMemo(action.target);
    }

    return { targetOf, block, unblock, openMemo, run };
}
