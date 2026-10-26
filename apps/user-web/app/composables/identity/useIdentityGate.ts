import type { IdentityStatus } from '~/composables/identity/api/useIdentityVerificationApi.types';
import type { IdentityGateOptions, IdentityGateState } from '~/composables/identity/useIdentityGate.types';

/**
 * 결제 직전 본인인증 확인 — 포인트 충전·공동구매 참여 화면이 결제를 시작하기 전에 부른다(commerce.md 12-1 「첫 충전·첫 공구 참여 직전에 인증 화면」).
 * 그리는 것은 `IdentityGateDialog`(레이아웃에 한 번 놓는다)이고, 여기는 "묻고 답을 기다리는" 것만 한다(`useConfirmDialog` 와 같은 방식).
 *
 * ```ts
 * const gate = useIdentityGate();
 * if (!(await gate.ensure({ purpose: 'CHARGE' }))) return;   // 미인증·미성년·닫음이면 false
 * // …결제 시작
 * ```
 *
 * - 이미 인증했고(만 19세 이상이 필요하면 성인이고) → 대화상자 없이 곧바로 true.
 * - 미인증 → 「포인트 충전하려면 본인인증이 필요해요」(용도별) + [본인인증 하기] → `useIdentity().verify()` → 성공 & 조건 충족이면 닫고 true.
 *   인증했는데 만 19세 미만이면 같은 대화상자가 막는 안내로 바뀐다. 실패는 대화상자 안 한 줄, 사용자가 인증창을 닫으면 조용히 그대로.
 * - 만 19세 미만 → 막는 안내(19세 생일이 지나면 다시 인증하지 않아도 된다) → 닫으면 false.
 * - 서버가 최종 판정한다 — 결제 API 도 본인인증·나이를 다시 검사한다(화면 검사는 편의).
 * ⚠ 컴포넌트·페이지 setup 에서 부른다.
 */

/** 답을 기다리는 약속 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다. 이미 떠 있는데 또 부르면 앞의 것은 false 로 닫힌다 */
let pending: ((ok: boolean) => void) | null = null;

export function useIdentityGate() {
    const identity = useIdentity();
    const state = useState<IdentityGateState>('identity-gate', () => ({
        open: false,
        purpose: 'CHARGE',
        requireAdult: true,
        mode: 'NEED',
        busy: false,
        failure: null,
        adultFrom: null,
        loadError: '',
    }));

    function passes(status: IdentityStatus | null, requireAdult: boolean): boolean {
        return status?.verified === true && (!requireAdult || status.adult === true);
    }

    function modeOf(status: IdentityStatus | null): IdentityGateState['mode'] {
        if (!status) return identity.loadStatus.value === 'error' ? 'LOAD_ERROR' : 'NEED';
        return status.verified ? 'MINOR' : 'NEED';
    }

    function settle(ok: boolean): void {
        state.value = { ...state.value, open: false, busy: false };
        const done = pending;
        pending = null;
        done?.(ok);
    }

    /** 결제를 시작해도 되는가 — 필요하면 대화상자를 띄우고 답을 기다린다 */
    async function ensure(options: IdentityGateOptions): Promise<boolean> {
        const requireAdult = options.requireAdult ?? true;
        const status = await identity.ensureLoaded();
        if (passes(status, requireAdult)) return true;
        pending?.(false);
        return new Promise<boolean>((resolve) => {
            pending = resolve;
            state.value = {
                open: true,
                purpose: options.purpose,
                requireAdult,
                mode: modeOf(status),
                busy: false,
                failure: null,
                adultFrom: status?.adultFrom ?? null,
                loadError: identity.loadError.value,
            };
        });
    }

    /** (IdentityGateDialog 전용) [본인인증 하기] */
    async function startVerify(): Promise<void> {
        if (state.value.busy) return;
        state.value = { ...state.value, busy: true, failure: null };
        const outcome = await identity.verify();
        if (!state.value.open) return;
        if (outcome.kind === 'CANCELLED') {
            state.value = { ...state.value, busy: false };
            return;
        }
        if (outcome.kind === 'FAILED') {
            state.value = { ...state.value, busy: false, failure: outcome };
            return;
        }
        if (passes(outcome.status, state.value.requireAdult)) return settle(true);
        state.value = { ...state.value, busy: false, mode: 'MINOR', adultFrom: outcome.status.adultFrom };
    }

    /** (IdentityGateDialog 전용) 상태를 받지 못했을 때 [다시 시도] */
    async function retryLoad(): Promise<void> {
        if (state.value.busy) return;
        state.value = { ...state.value, busy: true };
        const status = await identity.load();
        if (!state.value.open) return;
        if (passes(status, state.value.requireAdult)) return settle(true);
        state.value = { ...state.value, busy: false, mode: modeOf(status), adultFrom: status?.adultFrom ?? null, loadError: identity.loadError.value };
    }

    /** (IdentityGateDialog 전용) 닫기·[취소]·[확인] — 결제를 시작하지 않는다 */
    function close(): void {
        if (state.value.busy) return;
        settle(false);
    }

    return { state, ensure, startVerify, retryLoad, close };
}
