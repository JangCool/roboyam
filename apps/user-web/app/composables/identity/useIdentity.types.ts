import type { IdentityFakeInput, IdentityStatus } from '~/composables/identity/api/useIdentityVerificationApi.types';

/** 내 본인인증 상태를 받는 상태 — `idle` 아직 안 물어봄 · `loading` 받는 중 · `ready` · `error` */
export type IdentityLoadStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * 본인인증 한 번(`useIdentity().verify()`)의 결과.
 * - `VERIFIED`: 인증 완료(받은 상태 — 만 19세 미만일 수도 있다)
 * - `CANCELLED`: 사용자가 인증창(가짜 대화상자·포트원 창)을 닫았다 — 화면은 조용히 넘어간다
 * - `FAILED`: 실패 — `code` 로 문구를 고르고(`UtilIdentityMessages.failure`), 모르는 코드는 서버 `message` 를 그대로 보인다
 */
export type IdentityVerifyOutcome =
    | { kind: 'VERIFIED'; status: IdentityStatus }
    | { kind: 'CANCELLED' }
    | { kind: 'FAILED'; code: string; message: string; retryAfter: string | null };

/** 가짜 본인인증(개발용) 대화상자의 상태 — 그리는 것은 `IdentityFakeDialog`(레이아웃에 한 번) */
export interface IdentityFakePrompt {
    open: boolean;
    /** 인증 완료 요청을 보내는 중 */
    busy: boolean;
    /** 서버가 거절한 칸 문구 — 키는 `birthDate` · `personKey` */
    fieldErrors: Partial<Record<keyof IdentityFakeInput, string>>;
}
