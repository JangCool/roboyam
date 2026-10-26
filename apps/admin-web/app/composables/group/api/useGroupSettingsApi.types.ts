import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';

/**
 * 소모임 설정 API 가 주고받는 모양 — community `docs/api-groups.md` 「개설 규칙」·5-1 「매니저 규칙」·11-10 「관리자 설정」(공개 범위)·4절 「운영 한도」와 같은 이름이다.
 * 네 구획 모두 `{ values, defaults, limits, fromDefaults, updatedAt, updatedBy }` 꼴이고 `PUT` 은 그 구획의 **칸 모두**를 보낸다(응답은 `GET` 과 같다).
 * `values` = 지금 적용되는 값, `defaults` = 저장된 값이 없을 때 쓰는 서버 설정 파일 값(「기본값으로」 기준), `fromDefaults` = 저장된 값이 없어 기본값으로 도는 중.
 * 서버(`server/admin`)가 이 모양 그대로 준다. 변경 이력(`history`)은 로보얌에서 더했다(community 는 운영 로그에만 남긴다).
 */

/** 숫자 칸의 범위 */
export interface GroupRuleRange {
    min: number;
    max: number;
}

/** 고르는 값 하나 — 이름(`label`)은 서버가 요청 언어로 준다 */
export interface GroupRuleOption<V extends string = string> {
    value: V;
    label: string;
}

/** 네 구획이 같이 갖는 칸 */
export interface GroupRuleResponseBase<V> {
    values: V;
    defaults: V;
    fromDefaults: boolean;
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름 */
    updatedBy: string | null;
}

// ── 개설 규칙 `GET·PUT /api/admin/group-settings/creation-rules` ─

/** 개설 방식 — 승인제(운영자가 확인한 뒤 열린다) · 자유 개설(바로 열린다) */
export type GroupCreationMode = 'APPROVAL' | 'OPEN';

export interface GroupCreationRuleValues {
    creationMode: GroupCreationMode;
    /** 개설할 수 있는 최소 등급 — 등급 설정의 등급 키 */
    minGrade: string;
    /** 한 사람이 모임장일 수 있는 소모임 수(신청 중·열림·비활성 합) */
    maxPerOwner: number;
}

export interface GroupCreationRuleResponse extends GroupRuleResponseBase<GroupCreationRuleValues> {
    limits: {
        creationModes: GroupRuleOption<GroupCreationMode>[];
        /** 등급(서열 순) — 등급 설정의 등급들 */
        grades: GroupRuleOption[];
        maxPerOwner: GroupRuleRange;
    };
    /** 승인 대기 신청 수 — 승인제 → 자유 개설 확인 창에 쓴다 */
    pendingRequestCount: number;
}

// ── 매니저 규칙 `GET·PUT /api/admin/group-settings/manager-rules` ─

export interface GroupManagerRuleValues {
    managersEnabled: boolean;
    /** 새 소모임의 기본 인원 */
    limitDefault: number;
    /** 모임장이 정할 수 있는 최대 인원 */
    limitMax: number;
}

export interface GroupManagerRuleResponse extends GroupRuleResponseBase<GroupManagerRuleValues> {
    limits: { limitDefault: GroupRuleRange; limitMax: GroupRuleRange };
    /** 열린 소모임 기준 영향 숫자 — `limitCounts` 는 소모임에 정해 둔 상한별 소모임 수(상한 오름차순) */
    usage: { managerCount: number; groupCount: number; limitCounts: { limit: number; groupCount: number }[] };
}

// ── 공개 범위 `GET·PUT /api/admin/group-settings/visibility-rules` ─

export interface GroupVisibilityRuleValues {
    visibilityEnabled: boolean;
    /** 비공개→공개 변경 쿨다운(일) */
    cooldownDays: number;
    /** 거절 뒤 재요청 대기(시간) */
    retryHours: number;
    /** 같은 글의 거절 한도(회) */
    rejectLimit: number;
    /** 하루 공개 요청 수(건) */
    dailyLimit: number;
    /** 요청 한마디·거절 사유 글자 수(자) */
    textMax: number;
}

export interface GroupVisibilityRuleResponse extends GroupRuleResponseBase<GroupVisibilityRuleValues> {
    limits: Record<Exclude<keyof GroupVisibilityRuleValues, 'visibilityEnabled'>, GroupRuleRange>;
    usage: {
        publicGroupCount: number;
        privateGroupCount: number;
        pendingRequestCount: number;
        /** 매니저 안내 알림을 보낸 시각(안 보냈으면 null) */
        managerNoticeSentAt: string | null;
        /** 지금 「글 공개 처리·되돌리기」를 맡은 매니저 수 */
        managerNoticeTargetCount: number;
    };
    /** 이 요청이 보낸 안내 알림 수(GET·변경 없음은 0) */
    noticeSent: number;
}

// ── 운영 한도 `GET·PUT /api/admin/group-settings/policy-rules` ─

export interface GroupPolicyRuleValues {
    reapplyDays: number;
    capacityMin: number;
    capacityMax: number;
    trendingSize: number;
    trendingActiveDays: number;
    eventMaxUpcoming: number;
    eventMaxDaysAhead: number;
    eventMaxLengthDays: number;
}

export interface GroupPolicyRuleResponse extends GroupRuleResponseBase<GroupPolicyRuleValues> {
    limits: Record<keyof GroupPolicyRuleValues, GroupRuleRange>;
}

// ── 변경 이력 `GET /api/admin/group-settings/history?section=&page=&size=` (로보얌 후보) ─

/** 이력을 거르는 구획 */
export type GroupSettingSection = 'CREATION' | 'MANAGER' | 'VISIBILITY' | 'POLICY';

/** 이력 한 줄 — 공용 이력 모양에 구획 칸을 더한다 */
export interface GroupSettingHistoryItem extends SettingHistoryItem {
    section: GroupSettingSection;
}

export type GroupSettingHistoryPage = SettingHistoryPage<GroupSettingHistoryItem>;

export interface GroupSettingHistoryQuery {
    section?: GroupSettingSection | null;
    page: number;
    size: number;
}
