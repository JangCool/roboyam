/**
 * 감사 로그 API 가 주고받는 모양 — 서버 `com.community.audit.dto.*`(`AuditLogView` 등)와 같다(계약: `docs/features/roles-contract.md` 「4단계 구현 결과」).
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다.
 * 작업·대상 종류·사유의 **이름은 서버가 요청 언어(`Accept-Language`)로 풀어 준다** — 화면이 i18n 키로 다시 풀지 않는다.
 */

/** 기록의 결과 — 성공 `OK` · 실패 `FAIL` · 권한이 없어 거부된 `DENIED` */
export type AuditResult = 'OK' | 'FAIL' | 'DENIED';

/** 행위자 종류 — 운영자 `OPERATOR` · 시스템(배치 등) `SYSTEM` · 로그인 전 행위 `ANONYMOUS` */
export type AuditActorType = 'OPERATOR' | 'SYSTEM' | 'ANONYMOUS';

export interface AuditLogView {
    /** 기록 번호 — 문자열이다(Snowflake 64비트라 숫자로 받으면 자리가 깨진다) */
    auditNo: string;
    /** 일어난 시각 — UTC ISO */
    occurredAt: string;
    /** 행위자 운영자 ID. 시스템·로그인 전 행위는 null */
    actorId: string | null;
    actorType: AuditActorType;
    /** 늘 null — 조회 응답은 이메일을 내지 않는다(행위자는 이름으로 보인다) */
    actorEmail: string | null;
    /** 행위자의 지금 이름. 시스템은 「시스템」, 로그인 전 행위는 「로그인하지 않은 사용자」, 지워진 운영자는 null(화면이 ID 로 대신한다) */
    actorName: string | null;
    actorIp: string | null;
    /** 작업 코드(예: `LOGIN_SUCCESS`) */
    action: string;
    /** 사람이 읽는 작업 이름(요청 언어). 모르는 코드는 코드 그대로 온다 */
    actionName: string;
    targetType: string | null;
    targetTypeName: string | null;
    targetId: string | null;
    /** 대상의 **지금** 이름(운영자·역할·회원·배치·사이트·등급 설정). 모르거나 지워졌으면 null — 기록에는 이름을 남기지 않는다 */
    targetName: string | null;
    result: AuditResult;
    /** 실패·거부 사유 문구(요청 언어). 없으면 null */
    message: string | null;
    /** 무엇이 어떻게 바뀌었나 — `AuditChange[]` 의 JSON 문자열(펼칠 때만 연다). 바뀐 값이 없는 줄(로그인·거부·조회 등)은 null */
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
    /** 요청 언어의 이름 */
    label: string;
}

/** 거르기 고르개가 쓰는 목록 — 모두 서버가 준다(작업 코드 목록은 서버 `AuditActions` 가 원천이라 화면에 고정 목록을 두지 않는다) */
export interface AuditOptions {
    actions: AuditOption[];
    targetTypes: AuditOption[];
    results: AuditOption[];
}

/** 바뀐 칸 하나. 만들기는 `before` 가 없고 지우기는 `after` 가 없다. `field`·`before`·`after` 는 서버 값 그대로다(번역하지 않는다) */
export interface AuditChange {
    field: string;
    before: string | null;
    after: string | null;
}

/** 거르기 조건의 칸 이름 — 서버 400 `fields.<칸>` 과 같다 */
export type AuditFilterField = 'from' | 'to' | 'actor' | 'action' | 'targetType' | 'target' | 'result';

export interface AuditQuery {
    /** 이 시각 이후 — UTC ISO */
    from?: string;
    /** 이 시각 이전 — UTC ISO. `from` 보다 앞서면 400 `fields.to` */
    to?: string;
    /** 운영자 ID 가 같거나 이름의 일부(이메일로는 찾지 않는다) */
    actor?: string;
    action?: string;
    targetType?: string;
    /** 대상 ID 의 일부, 또는 대상의 지금 이름의 일부 */
    target?: string;
    result?: string;
    /** 0 부터 */
    page?: number;
    /** 1~100, 기본 20 */
    size?: number;
}
