import type { AuditChange } from '~/composables/audit/api/useAuditApi.types';
import type { AuditRetentionItem } from '~/composables/audit/api/useAuditRetentionApi.types';

/** 보관 기간 칸 하나의 문제 — 비었나 · 정수인가 · 하한 이상인가 · 상한 이하인가 */
export type AuditRetentionProblem = 'REQUIRED' | 'NOT_INTEGER' | 'BELOW_MIN' | 'ABOVE_MAX';

/** 감사 로그 화면의 순수 규칙. */
export default class UtilAudit {
    /** 1년을 며칠로 치는가 — 「약 N년」 환산에 쓴다 */
    static readonly DAYS_PER_YEAR = 365;

    /**
     * 보관 일수 입력을 0 이상 정수로 읽는다 — 앞뒤 공백은 허용한다.
     * @returns 정수. 비었거나 정수가 아니면 null.
     */
    static parseDays(text: string): number | null {
        const cleaned = text.trim();
        return /^\d{1,9}$/.test(cleaned) ? Number(cleaned) : null;
    }

    /**
     * 보관 일수 칸 하나의 문제 — 범위는 서버가 준 분류의 하한·상한(`minDays`·`maxDays`)이다(판정은 서버, 서버 규칙 이름은 서버 단계에서 정한다 — 후보 `AuditRetentionPolicy`).
     * @param {string} text - 입력 글자.
     * @param {AuditRetentionItem} item - 분류(하한·상한).
     * @returns {AuditRetentionProblem | null} 문제가 없으면 null.
     */
    static retentionProblem(text: string, item: Pick<AuditRetentionItem, 'minDays' | 'maxDays'>): AuditRetentionProblem | null {
        if (!text.trim()) return 'REQUIRED';
        const value = UtilAudit.parseDays(text);
        if (value === null) return 'NOT_INTEGER';
        if (value < item.minDays) return 'BELOW_MIN';
        return value > item.maxDays ? 'ABOVE_MAX' : null;
    }

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
