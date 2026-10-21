/**
 * 감사 로그 API 가 주고받는 모양 — 후보 계약(`GET /api/admin/audit-logs` 등)이고 확정 전이다. 칸 이름은 hamonica3 `useAuditApi` 의 것을 그대로 쓴다.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다.
 * ⚠ hamonica3 의 `workspaceNo`·`workspaceName` 은 뺐다 — 이 제품에는 워크스페이스가 없다.
 */

export interface AuditLogView {
    auditNo: number;
    /** 일어난 시각 — UTC ISO */
    occurredAt: string;
    actorId: string;
    actorEmail: string | null;
    /** 그 사람 이름. 서버가 댄다 — 시스템이 한 일은 "시스템", 탈퇴한 사람은 null(화면이 메일·아이디로 대신한다) */
    actorName: string | null;
    actorIp: string | null;
    /** 작업 코드 */
    action: string;
    /** 사람이 읽는 작업 이름. 코드 값만 보이면 못 읽는다 */
    actionName: string;
    targetType: string | null;
    targetTypeName: string | null;
    targetId: string | null;
    /** 그때의 이름. 지워진 것도 이것으로 읽는다 */
    targetName: string | null;
    /** `OK` · `FAIL` */
    result: string;
    message: string | null;
    /** 무엇이 어떻게 바뀌었나 — `AuditChange[]` 의 JSON 문자열 그대로 온다(펼칠 때만 연다). 보기만 한 줄은 null */
    changes: string | null;
}

export interface AuditPage {
    items: AuditLogView[];
    total: number;
    /** 0 부터 */
    page: number;
    size: number;
}

export interface AuditOption {
    value: string;
    label: string;
}

/** 거르기 고르개가 쓰는 목록 */
export interface AuditOptions {
    actions: AuditOption[];
    targetTypes: AuditOption[];
}

/** 바뀐 칸 하나. 만들기는 `before` 가 없고 지우기는 `after` 가 없다 */
export interface AuditChange {
    field: string;
    before: string | null;
    after: string | null;
}

export interface AuditQuery {
    /** 이 시각 이후 — UTC ISO */
    from?: string;
    /** 이 시각 이전 — UTC ISO */
    to?: string;
    /** 아이디·이메일·이름의 일부 */
    actor?: string;
    action?: string;
    targetType?: string;
    /** 대상 아이디·이름의 일부 */
    target?: string;
    result?: string;
    /** 0 부터 */
    page?: number;
    size?: number;
}
