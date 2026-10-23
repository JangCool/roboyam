import type { AdminMeResponse } from '~/composables/auth/api/useAdminAuthApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import type { MfaEnrollmentView } from '~/composables/mfa/api/useMfaApi.types';
import UtilMfaMessages from '~/utils/util-mfa-messages';

/**
 * OTP 등록 흐름 하나 — ① 등록 시작(비밀키·QR) → ② 확인 코드 → ③ 복구 코드. 로그인 2단계(강제·유예 중 등록)와 내 정보 › 로그인 보안(자발 등록·다시 등록)이 같이 쓴다.
 * 세션 종류(`mode`)에 따라 주소가 다르다 — `pending`: 2차 대기 세션의 강제 등록(확정 응답의 `me` 로 로그인 완료) · `session`: 로그인 세션의 자발 등록(유예 중 [지금 등록] 포함)·다시 등록 확정.
 * 다시 등록 시작(`reenroll`, 비밀번호 확인)은 부르는 쪽이 하고 그 응답을 `use` 로 넘긴다 — 확정은 `session` 주소다.
 * 그리는 것은 `MfaEnrollPanel` 이고 여기는 서버 호출과 그 결과만 든다. **부를 때마다 새 상태**다(`useState` 아님 — 비밀키를 화면 밖에 남기지 않는다).
 * 비밀키·복구 코드는 이 메모리에만 있고 `reset()` 으로 비운다(로그·저장소 금지).
 *
 * ⚠ 컴포넌트·페이지 setup 에서 부른다(번역 함수를 그때 붙잡는다).
 */
export type MfaEnrollmentMode = 'pending' | 'session';

/**
 * @param {MaybeRefOrGetter<MfaEnrollmentMode>} mode - 지금 세션 종류. 로그인 화면처럼 같은 흐름에서 바뀔 수 있으면 getter 로 넘긴다(부르는 순간 읽는다).
 */
export function useMfaEnrollment(mode: MaybeRefOrGetter<MfaEnrollmentMode> = 'session') {
    const api = useMfaApi();
    const t = useTranslate();
    const toApiError = useApiErrorOf();

    const enrollment = ref<MfaEnrollmentView | null>(null);
    const recoveryCodes = ref<string[] | null>(null);
    /** 강제 등록(2차 대기 세션)이면 서버가 확정 응답에 실어 준 로그인 결과 — 부르는 쪽이 `adopt` 한다 */
    const me = ref<AdminMeResponse | null>(null);
    /** OTP 앱에 들어간 계정 이름(서버 `accountName` = 운영자 이메일) — 복구 코드 파일 머리말이 쓴다. 비밀키를 지운 뒤에도 남긴다 */
    const account = ref('');
    const starting = ref(false);
    /** 등록을 시작하지 못했을 때 서버 문구 */
    const startError = ref('');
    const confirming = ref(false);
    /** 확인 코드 칸 아래 글(서버 문구 + 남은 횟수) */
    const codeError = ref('');
    /** OTP 를 여러 번 틀려 잠긴 동안 */
    const lock = useCountdown(undefined, () => (codeError.value = ''));

    /**
     * 등록을 시작한다(비밀키·QR 받기). 다시 등록(비밀번호 확인)은 부르는 쪽이 `reenroll` 을 부르고 그 응답을 `use` 로 넘긴다.
     * @returns {Promise<ApiError | null>} 실패하면 그 오류(대기 만료 판정용), 성공이면 null.
     */
    async function start(): Promise<ApiError | null> {
        reset();
        starting.value = true;
        try {
            enrollment.value = toValue(mode) === 'pending' ? await api.startForcedEnrollment() : await api.startEnrollment();
            account.value = enrollment.value.accountName;
            return null;
        } catch (e) {
            const failure = toApiError(e);
            startError.value = failure.message;
            return failure;
        } finally {
            starting.value = false;
        }
    }

    /** 다른 요청(다시 등록 `reenroll`)이 돌려준 등록 시작 응답을 쓴다 */
    function use(view: MfaEnrollmentView): void {
        reset();
        enrollment.value = view;
        account.value = view.accountName;
    }

    /**
     * 확인 코드를 보낸다. 맞으면 복구 코드(와 강제 등록이면 `me`)가 채워진다.
     * @returns {Promise<ApiError | null>} 실패하면 그 오류(`MFA_PENDING_EXPIRED` 면 부르는 쪽이 처음으로 돌린다), 성공이면 null.
     */
    async function confirm(code: string): Promise<ApiError | null> {
        if (confirming.value || lock.active.value) return null;
        confirming.value = true;
        codeError.value = '';
        try {
            const res = toValue(mode) === 'pending' ? await api.confirmForcedEnrollment(code) : await api.confirmEnrollment(code);
            recoveryCodes.value = res.recoveryCodes;
            me.value = res.me ?? null;
            // 비밀키는 더 쓰지 않는다 — 복구 코드 단계로 넘어가면 지운다
            enrollment.value = null;
            return null;
        } catch (e) {
            const failure = e instanceof ApiError ? e : toApiError(e);
            if (failure.code === 'MFA_LOCKED') lock.start(failure.retryAfterSeconds ?? 600);
            codeError.value = UtilMfaMessages.codeError(t, failure);
            return failure;
        } finally {
            confirming.value = false;
        }
    }

    /** 모두 비운다 — 끝났거나 그만둘 때 */
    function reset(): void {
        enrollment.value = null;
        recoveryCodes.value = null;
        me.value = null;
        account.value = '';
        startError.value = '';
        codeError.value = '';
    }

    return { enrollment, recoveryCodes, me, account, starting, startError, confirming, codeError, lock, start, use, confirm, reset };
}
