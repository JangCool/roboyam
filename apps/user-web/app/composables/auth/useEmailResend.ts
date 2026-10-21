/**
 * 인증 메일 다시 보내기 + 쿨다운(60초에 한 번).
 *
 * 안내 배너(`EmailVerifyBanner`)와 `/verify-email` 화면이 **같은 카운트다운**을 본다 — 끝나는 시각을
 * `useState` 에 두므로 한쪽에서 보내면 다른 쪽 버튼도 같이 잠긴다.
 *
 * 서버의 응답으로 갈린다.
 *  - `202` 보냈다 → 60초 쿨다운
 *  - `429 RESEND_COOLDOWN` → 서버가 준 `retryAfterSeconds` 로 쿨다운
 *  - `409 ALREADY_VERIFIED` → 이미 인증됨. 로그인 상태를 새로 받아 배너가 사라지게 한다
 */
const DEFAULT_COOLDOWN_SECONDS = 60;

export type ResendOutcome = 'sent' | 'cooldown' | 'already' | 'error';

export function useEmailResend() {
    const api = useAuthApi();
    const { user, refresh } = useAuth();

    const endsAt = useState<number>('email-resend-ends-at', () => 0);
    const countdown = useCountdown(endsAt);

    const sending = ref(false);
    /** 마지막 시도가 실패했을 때의 문구 */
    const errorMessage = ref('');

    // 로그아웃하거나 다른 계정으로 바뀌면 쿨다운도 끝낸다
    watch(
        () => user.value?.id,
        () => {
            if (!user.value) countdown.stop();
        },
    );

    /** 방금 인증 메일을 보냈다(가입 직후처럼 화면이 직접 보낸 게 아닐 때) */
    function markSent(seconds = DEFAULT_COOLDOWN_SECONDS) {
        countdown.start(seconds);
    }

    async function resend(): Promise<ResendOutcome> {
        if (sending.value || countdown.active.value) return 'cooldown';
        sending.value = true;
        errorMessage.value = '';
        try {
            await api.resendVerification();
            countdown.start(DEFAULT_COOLDOWN_SECONDS);
            return 'sent';
        } catch (e) {
            if (!(e instanceof ApiError)) throw e;
            if (e.code === 'RESEND_COOLDOWN') {
                countdown.start(e.retryAfterSeconds ?? DEFAULT_COOLDOWN_SECONDS);
                return 'cooldown';
            }
            if (e.code === 'ALREADY_VERIFIED') {
                try {
                    await refresh();
                } catch {
                    /* 다음에 다시 받는다 */
                }
                return 'already';
            }
            errorMessage.value = e.message;
            return 'error';
        } finally {
            sending.value = false;
        }
    }

    return { sending, errorMessage, cooldownRemaining: countdown.remaining, coolingDown: countdown.active, markSent, resend };
}
