import type { AuditChange } from '~/composables/audit/api/useAuditApi.types';

/** 감사 로그 화면의 순수 규칙. */
export default class UtilAudit {
    /**
     * 바뀐 내용(JSON 글자)을 펼칠 때만 연다. 못 열어도 화면이 멈추면 안 된다 — 옛 기록의 모양이 지금과 다를 수 있다.
     * @param {string | null} changes - 서버가 준 `changes` JSON 글자. 보기만 한 줄은 null.
     * @returns {AuditChange[]} 바뀐 칸 목록. 비었거나 읽을 수 없으면 빈 배열.
     */
    static parseChanges(changes: string | null): AuditChange[] {
        if (!changes) return [];
        try {
            const parsed: unknown = JSON.parse(changes);
            return Array.isArray(parsed) ? (parsed as AuditChange[]) : [];
        } catch {
            return [];
        }
    }
}
