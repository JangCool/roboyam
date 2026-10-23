import {
    mockCreationRules,
    mockGroupSettingHistory,
    mockManagerRules,
    mockPolicyRules,
    mockSaveCreationRules,
    mockSaveManagerRules,
    mockSavePolicyRules,
    mockSaveVisibilityRules,
    mockVisibilityRules,
} from '~/composables/group/api/mock/group-mock';
import type {
    GroupCreationRuleResponse,
    GroupCreationRuleValues,
    GroupManagerRuleResponse,
    GroupManagerRuleValues,
    GroupPolicyRuleResponse,
    GroupPolicyRuleValues,
    GroupSettingHistoryPage,
    GroupSettingHistoryQuery,
    GroupVisibilityRuleResponse,
    GroupVisibilityRuleValues,
} from '~/composables/group/api/useGroupSettingsApi.types';

/**
 * 소모임 설정 API — 서버 주소 문자열(`/api/admin/group-settings/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: community `docs/api-groups.md` 「개설 규칙」·5-1 「매니저 규칙」·11-10 「관리자 설정」·「운영 한도」 → 로보얌 `docs/features/group-contract.md` 「관리자 화면」. **서버에 아직 없다(목업)**.
 *
 * 권한(로보얌 코드): 조회 `group.read`, 저장 `group.settings`(community 는 최고 관리자 전용 — 로보얌은 권한 코드로 위임한다). 없으면 `403 PERMISSION_DENIED`. 응답 `Cache-Control: no-store`.
 * 구획마다 `GET` · `PUT`(그 구획의 칸 모두, 응답은 GET 과 같다). 틀린 칸은 모아 `400 VALIDATION fields.<칸>`. 서버는 값을 캐시하지 않는다(다음 요청부터 모든 서버에 적용).
 *   - `…/creation-rules`   `{ creationMode, minGrade, maxPerOwner }`              운영 로그 `GROUP_CREATION_RULE_UPDATE`
 *   - `…/manager-rules`    `{ managersEnabled, limitDefault, limitMax }`          기본 > 최대는 `fields.limitDefault`. 운영 로그 `GROUP_MANAGER_RULE_UPDATE`
 *   - `…/visibility-rules` `{ visibilityEnabled, cooldownDays, retryHours, rejectLimit, dailyLimit, textMax }` — 켜짐으로 저장하면 같은 요청에서 매니저 안내 알림이 1회(`noticeSent`). 운영 로그 `GROUP_VISIBILITY_RULE_UPDATE`
 *   - `…/policy-rules`     여덟 칸(`reapplyDays` … `eventMaxLengthDays`)            최소 > 최대 정원은 `fields.capacityMax`. 운영 로그 `GROUP_POLICY_RULE_UPDATE`
 *   - `GET …/history?section=&page=&size=` → 변경 이력 한 쪽(**로보얌 후보** — 공용 `SettingHistoryPage` 모양 + `section`)
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/group-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    creation: '/api/admin/group-settings/creation-rules',
    manager: '/api/admin/group-settings/manager-rules',
    visibility: '/api/admin/group-settings/visibility-rules',
    policy: '/api/admin/group-settings/policy-rules',
    history: '/api/admin/group-settings/history',
};

export function useGroupSettingsApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 모든 메서드가 아직 목업이라 주소를 쓰는 곳이 없다 — 서버가 붙으면 이 줄을 지운다

    return {
        creationRules: (): Promise<GroupCreationRuleResponse> =>
            // MOCK:
            mockCreationRules(),
        // REAL: client.get<GroupCreationRuleResponse>(PATH.creation),

        saveCreationRules: (body: GroupCreationRuleValues): Promise<GroupCreationRuleResponse> =>
            // MOCK:
            mockSaveCreationRules(body),
        // REAL: client.put<GroupCreationRuleResponse>(PATH.creation, body),

        managerRules: (): Promise<GroupManagerRuleResponse> =>
            // MOCK:
            mockManagerRules(),
        // REAL: client.get<GroupManagerRuleResponse>(PATH.manager),

        saveManagerRules: (body: GroupManagerRuleValues): Promise<GroupManagerRuleResponse> =>
            // MOCK:
            mockSaveManagerRules(body),
        // REAL: client.put<GroupManagerRuleResponse>(PATH.manager, body),

        visibilityRules: (): Promise<GroupVisibilityRuleResponse> =>
            // MOCK:
            mockVisibilityRules(),
        // REAL: client.get<GroupVisibilityRuleResponse>(PATH.visibility),

        saveVisibilityRules: (body: GroupVisibilityRuleValues): Promise<GroupVisibilityRuleResponse> =>
            // MOCK:
            mockSaveVisibilityRules(body),
        // REAL: client.put<GroupVisibilityRuleResponse>(PATH.visibility, body),

        policyRules: (): Promise<GroupPolicyRuleResponse> =>
            // MOCK:
            mockPolicyRules(),
        // REAL: client.get<GroupPolicyRuleResponse>(PATH.policy),

        savePolicyRules: (body: GroupPolicyRuleValues): Promise<GroupPolicyRuleResponse> =>
            // MOCK:
            mockSavePolicyRules(body),
        // REAL: client.put<GroupPolicyRuleResponse>(PATH.policy, body),

        /** 변경 이력 한 쪽 — 최신이 위(로보얌 후보) */
        history: (query: GroupSettingHistoryQuery): Promise<GroupSettingHistoryPage> =>
            // MOCK:
            mockGroupSettingHistory(query),
        // REAL: client.get<GroupSettingHistoryPage>(PATH.history, { query: { section: query.section ?? undefined, page: query.page, size: query.size } }),
    };
}
