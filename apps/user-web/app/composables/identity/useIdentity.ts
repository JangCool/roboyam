import { ApiError } from '~/composables/common/useApiClient';
import type { IdentityFakeInput, IdentityStatus } from '~/composables/identity/api/useIdentityVerificationApi.types';
import type { IdentityFakePrompt, IdentityLoadStatus, IdentityVerifyOutcome } from '~/composables/identity/useIdentity.types';
import UtilAuthMessages from '~/utils/util-auth-messages';

/**
 * 내 본인인증 — 상태(공유 `useState`)와 인증 흐름 한 번(`verify`).
 * 설정의 본인인증 화면(`/settings/identity`)·결제 직전 확인(`useIdentityGate`)·개발용 시연 화면(`/dev/identity`)이 같이 쓴다.
 *
 * 인증 흐름(계약 「0-4」 — `useIdentityVerificationApi`):
 *  ① `start()` — 서버가 인증 번호를 발급하고 지금 쓰는 구현(`PORTONE` | `FAKE`)을 알려 준다.
 *  ② `PORTONE` 이면 포트원 브라우저 SDK `requestIdentityVerification` 을 부를 자리다 — SDK 는 새 의존성이라 아직 넣지 않았다(사용자 승인 전, 아래 자리 주석).
 *     지금은 「포트원 인증창은 아직 연결되지 않았어요」 실패로 끝난다(발급된 인증 번호는 서버에서 30분 뒤 만료).
 *     `FAKE` 면 「가짜 본인인증(개발용)」 대화상자(`IdentityFakeDialog`)를 띄워 생년월일·사람 번호를 받는다.
 *  ③ `confirm()` — 성공하면 상태를 바꾸고 `VERIFIED`. 가짜 입력이 틀리면(400 VALIDATION) 대화상자를 연 채 칸 아래에 서버 문구를 보인다.
 *
 * - **로그인한 사람마다 따로다** — 받아 둔 상태의 주인이 지금 로그인한 사람과 다르면 버린다(로그아웃·계정 전환).
 * - 생년월일·CI·DI 는 화면에 오지 않는다 — 만 19세 이상 여부와 19세 생일만.
 * ⚠ 컴포넌트·페이지 setup 에서 부른다(Nuxt 문맥). 돌려받은 함수는 `await` 뒤에 써도 된다.
 */

/** 요청 번호 — 늦게 온 옛 응답을 버린다(모듈에 둔다 — 여러 화면이 같은 순서를 본다) */
let request = 0;
/** 가짜 대화상자가 답을 기다리는 약속과 그 인증 번호 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다 */
let fakePending: ((outcome: IdentityVerifyOutcome) => void) | null = null;
let fakeVerificationId: string | null = null;
/** 인증 흐름이 이미 진행 중이면 같은 약속을 돌려준다(단추를 두 번 눌러도 대화상자가 하나) */
let running: Promise<IdentityVerifyOutcome> | null = null;

export function useIdentity() {
    const api = useIdentityVerificationApi();
    const { user } = useAuth();
    const t = useTranslate();

    const status = useState<IdentityStatus | null>('identity-status', () => null);
    /** 받아 둔 상태의 주인(로그인한 회원 ID) */
    const ownerId = useState<string | null>('identity-status-owner', () => null);
    const loadStatus = useState<IdentityLoadStatus>('identity-load-status', () => 'idle');
    /** 상태를 받지 못한 이유(서버 문구 그대로) */
    const loadError = useState<string>('identity-load-error', () => '');
    const fake = useState<IdentityFakePrompt>('identity-fake', () => ({ open: false, busy: false, fieldErrors: {} }));

    function messageOf(e: unknown): string {
        return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
    }

    function failed(e: unknown): IdentityVerifyOutcome {
        if (e instanceof ApiError) return { kind: 'FAILED', code: e.code, message: e.message, retryAfter: e.extraText('retryAfter') ?? null };
        return { kind: 'FAILED', code: 'NETWORK', message: messageOf(e), retryAfter: null };
    }

    /** 서버가 「이미 인증함」(409 `IDENTITY_ALREADY_VERIFIED` — 다른 탭에서 마친 경우 등)이라 하면 상태를 새로 받아 인증 완료로 넘긴다. 아니면 null */
    async function alreadyVerified(e: unknown): Promise<IdentityVerifyOutcome | null> {
        if (!(e instanceof ApiError) || e.code !== 'IDENTITY_ALREADY_VERIFIED') return null;
        const found = await load();
        return found?.verified ? { kind: 'VERIFIED', status: found } : null;
    }

    function reset(): void {
        request++;
        status.value = null;
        ownerId.value = null;
        loadStatus.value = 'idle';
        loadError.value = '';
    }

    /** 서버에서 받는다. 받아 둔 값이 있으면 그대로 보이면서 바꾼다 */
    async function load(): Promise<IdentityStatus | null> {
        const me = user.value?.id ?? null;
        if (!me) {
            reset();
            return null;
        }
        if (ownerId.value !== me) reset();
        const mine = ++request;
        if (!status.value) loadStatus.value = 'loading';
        loadError.value = '';
        try {
            const found = await api.me();
            if (mine !== request) return status.value;
            status.value = found;
            ownerId.value = me;
            loadStatus.value = 'ready';
            return found;
        } catch (e) {
            if (mine === request && !status.value) {
                loadStatus.value = 'error';
                loadError.value = messageOf(e);
            }
            return status.value;
        }
    }

    /** 아직 안 받았거나(다른 사람 것이면) 받는다. 이미 받았으면 그 값 */
    async function ensureLoaded(): Promise<IdentityStatus | null> {
        const me = user.value?.id ?? null;
        if (me && ownerId.value === me && loadStatus.value === 'ready') return status.value;
        return load();
    }

    /** 가짜 대화상자를 연다 — 답(완료·취소)이 올 때까지 기다린다 */
    function askFake(verificationId: string): Promise<IdentityVerifyOutcome> {
        fakePending?.({ kind: 'CANCELLED' });
        fakeVerificationId = verificationId;
        fake.value = { open: true, busy: false, fieldErrors: {} };
        return new Promise<IdentityVerifyOutcome>((resolve) => {
            fakePending = resolve;
        });
    }

    function settleFake(outcome: IdentityVerifyOutcome): void {
        fake.value = { open: false, busy: false, fieldErrors: {} };
        const done = fakePending;
        fakePending = null;
        fakeVerificationId = null;
        done?.(outcome);
    }

    /** 인증 완료 요청 — 성공하면 상태를 바꾼다 */
    async function confirmWith(verificationId: string, fakeInput: IdentityFakeInput | undefined): Promise<IdentityStatus> {
        const found = await api.confirm(verificationId, fakeInput ? { fake: fakeInput } : {});
        status.value = found;
        ownerId.value = user.value?.id ?? null;
        loadStatus.value = 'ready';
        return found;
    }

    /** (IdentityFakeDialog 전용) [인증 완료] — 입력이 틀리면(400 VALIDATION) 대화상자를 연 채 칸 아래에 서버 문구, 그 밖의 결과는 닫고 답한다 */
    async function submitFake(input: IdentityFakeInput): Promise<void> {
        const verificationId = fakeVerificationId;
        if (!verificationId || fake.value.busy) return;
        fake.value = { ...fake.value, busy: true, fieldErrors: {} };
        try {
            const found = await confirmWith(verificationId, input);
            settleFake({ kind: 'VERIFIED', status: found });
        } catch (e) {
            if (e instanceof ApiError && e.code === 'VALIDATION' && (e.field('fake.birthDate') || e.field('fake.personKey'))) {
                fake.value = { ...fake.value, busy: false, fieldErrors: { birthDate: e.field('fake.birthDate'), personKey: e.field('fake.personKey') } };
                return;
            }
            settleFake((await alreadyVerified(e)) ?? failed(e));
        }
    }

    /** (IdentityFakeDialog 전용) [취소]·닫기 — 사용자가 인증창을 닫은 것과 같다 */
    function cancelFake(): void {
        if (fake.value.busy) return;
        settleFake({ kind: 'CANCELLED' });
    }

    async function run(): Promise<IdentityVerifyOutcome> {
        let started;
        try {
            started = await api.start();
        } catch (e) {
            return (await alreadyVerified(e)) ?? failed(e);
        }
        if (started.provider === 'FAKE') return askFake(started.verificationId);

        // TODO(포트원 SDK): 브라우저 SDK(`@portone/browser-sdk/v2`)로 인증창을 연다 — 새 의존성이라 사용자 승인 뒤에 더한다. 자리:
        //   const response = await PortOne.requestIdentityVerification({ storeId: started.storeId, identityVerificationId: started.verificationId, channelKey: started.channelKey });
        //   if (response?.code === 'IDENTITY_VERIFICATION_CANCELLED' 등 사용자가 닫음) return { kind: 'CANCELLED' };
        //   if (response?.code) return { kind: 'FAILED', code: 'IDENTITY_PROVIDER_FAILED', message: response.message, retryAfter: null };
        //   try { return { kind: 'VERIFIED', status: await confirmWith(started.verificationId, undefined) }; } catch (e) { return failed(e); }
        // SDK 가 아직 없다 — 화면 전용 코드로 끝낸다(실패 안내는 기본 제목 + 이 문구 — `UtilIdentityMessages.failure` 의 그 밖의 코드)
        return { kind: 'FAILED', code: 'PORTONE_SDK_MISSING', message: t('identity.portoneUnavailable'), retryAfter: null };
    }

    /** 본인인증 한 번 — 결과를 돌려준다(던지지 않는다). 이미 진행 중이면 그 결과를 같이 기다린다 */
    function verify(): Promise<IdentityVerifyOutcome> {
        if (!running) running = run().finally(() => (running = null));
        return running;
    }

    // 로그아웃·계정 전환 — 다른 사람의 인증 상태가 남아 보이지 않게 비운다
    watch(
        () => user.value?.id ?? null,
        (id) => {
            if (id !== ownerId.value && ownerId.value !== null) reset();
        },
    );

    return { status, loadStatus, loadError, fake, load, ensureLoaded, verify, submitFake, cancelFake };
}
