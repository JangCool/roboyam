import type { AiSettingsView } from '~/composables/aimember/api/useAiSettingsApi.types';

/**
 * 내 AI 제외 설정 API — 서버 주소 `/api/me/ai-settings` 가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/ai-members-contract.md` 「회원의 AI 제외 설정」(서버 `MyAiSettingsController` — 쪽지 받기 설정과 같은 모양). 로그인 필요(401), `PUT` 은 CSRF.
 * 켜면 내 글은 AI 기자 뉴스 소재·AI 회원 첫 댓글 대상에서, 내 댓글은 AI 답글 대상·답글 때 함께 읽는 댓글에서 빠진다. 이미 게시된 AI 글·댓글은 그대로.
 * 오류: 불리언이 아니면 `400 VALIDATION fields.excludeFromAi`.
 */
const PATH = {
    settings: '/api/me/ai-settings',
};

export function useAiSettingsApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 AI 제외 설정 */
        settings: (signal?: AbortSignal): Promise<AiSettingsView> => client.get<AiSettingsView>(PATH.settings, { signal }),

        /** AI 제외 설정 바꾸기(멱등) */
        updateSettings: (excludeFromAi: boolean): Promise<AiSettingsView> => client.put<AiSettingsView>(PATH.settings, { excludeFromAi }),
    };
}
