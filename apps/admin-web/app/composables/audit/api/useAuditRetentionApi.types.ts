/**
 * 감사 기록 보관 기간 API 가 주고받는 모양 — 서버 DTO `AuditRetentionView`·`AuditRetentionSaveRequest`(server/admin `audit.dto`).
 * 정책 원천: `docs/domain/commerce.md` 13-4 「보관 기간」. 시각은 UTC ISO 다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 보관 분류 — 감사 줄을 남길 때 행동마다 붙는다 */
export type AuditRetentionCategory = 'GENERAL' | 'PERSONAL_DATA' | 'MONEY';

/** 고정 차례(일반 운영 · 개인정보 열람·내려받기 · 돈 관련) — 타입에 딸린 상수 */
export const AUDIT_RETENTION_CATEGORIES: readonly AuditRetentionCategory[] = ['GENERAL', 'PERSONAL_DATA', 'MONEY'];

export interface AuditRetentionItem {
    category: AuditRetentionCategory;
    /** 보관 일수 — 이보다 오래된 기록을 매일 정리 배치가 지운다 */
    days: number;
    /** 처음 값(GENERAL 365 · PERSONAL_DATA 730 · MONEY 1825) */
    defaultDays: number;
    /** 하한 — 이보다 짧게는 못 줄인다(GENERAL 1 · PERSONAL_DATA 365 · MONEY 1825) */
    minDays: number;
    /** 상한(3650) */
    maxDays: number;
}

/** `GET`·`PUT /api/admin/audit-logs/retention` 응답 */
export interface AuditRetentionView {
    items: AuditRetentionItem[];
    /** 지금 운영자가 바꿀 수 있는가 — 최고 관리자(루트)만 true(권한 코드로 위임하지 않는다) */
    canEdit: boolean;
    /** 마지막 저장 시각(UTC ISO). 저장한 적 없으면 null(기본값) */
    updatedAt: string | null;
    /** 마지막으로 저장한 운영자 이름. 저장한 적 없으면 null */
    updatedBy: string | null;
}

/** `PUT /api/admin/audit-logs/retention` 요청 — 세 분류 모두 보낸다 */
export interface AuditRetentionSaveRequest {
    days: Record<AuditRetentionCategory, number>;
}
