import { ApiError } from '~/composables/common/useApiClient';
import type { Grade, GradeBadgeColor, GradeKey, GradeManualPolicy, GradeRequest, GradesSaveRequest } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 설정 화면(`/grades`)과 역할 관리·회원 조회의 등급 표시(`GradeBadge`)가 쓰는 순수 로직 — **등급은 순서 있는 목록**이다(4단계 고정이 아니다).
 * 행(`GradeRowForm`) 만들기·추가·순서 이동 · 입력(글자)↔값 바꾸기 · 이름·범위·순서 검사 · 저장된 값과의 차이 · 요청 만들기 · 서버 `400 VALIDATION` 의 칸별 오류 읽기 · 뱃지 색·아이콘의 고정 목록과 색 단계 · 등급 역할 아이디 파생.
 * 서버 규칙(community `docs/api-grade.md` "승급 기준", V33 `grade_rules` CHECK)을 미리 알려 주는 용도다 — 값은 정수, 범위는 일수 0~3650 · 글·댓글 0~100000, 이름은 고유·최대 8자,
 * **위 등급일수록 같거나 커야 한다**(목록 순서를 따른다). 판정은 늘 서버이고, 서버가 거절하면 그 문구를 칸 아래에 그대로 보인다.
 * 입력은 **글자**로 든다(입력 도중의 빈 칸을 칸이 되돌려 쓰지 않게). 화면에 보이는 문구는 `util-grade-messages.ts` 와 `i18n/locales/{ko,en}/grade.ts` 에 있다.
 */

/** 승급 조건 칸의 이름 — 서버 응답·요청의 키와 같다 */
export type GradeField = 'minDays' | 'minActivity';

/** 서버 오류가 붙는 칸 — 이름 · 승급 조건 · 뱃지 */
export type GradeCellField = 'label' | GradeField | 'badgeColor' | 'badgeIcon';

/** 입력칸의 문제 — 비어 있다 / 정수가 아니다 / 범위를 벗어났다 / 아래 등급보다 작다(순서) / 이름이 없다 / 이름이 길다 / 이름이 겹친다 */
export type GradeProblem = 'REQUIRED' | 'NOT_INTEGER' | 'OUT_OF_RANGE' | 'ORDER' | 'LABEL_REQUIRED' | 'LABEL_LENGTH' | 'LABEL_DUPLICATE';

/** 등급 한 줄의 입력값 — 승급 조건·이름은 글자, 뱃지 아이콘은 PrimeIcons 클래스(빈 글자 = 아이콘 없음) */
export interface GradeRowForm {
    /** 화면 안 식별자 — 이미 있는 등급은 `gradeKey`, 새 등급은 `new-<번호>` */
    id: string;
    /** 서버 식별자. `null` = 새 등급(저장하면 서버가 발급한다) */
    gradeKey: GradeKey | null;
    label: string;
    minDays: string;
    minActivity: string;
    color: GradeBadgeColor;
    icon: string;
    /** 이 등급인 회원 수(새 등급은 0) */
    memberCount: number;
}

/** 화면이 미리 검사한 칸별 문제 — 행 id → 칸 → 문제 */
export type GradeProblems = Record<string, Partial<Record<'label' | GradeField, GradeProblem>>>;

/** 서버가 거절한 칸별 문구(행 id → 칸 → 문구), 수동 상향 회원 옵션의 문구(`policy`), 칸에 붙지 않는 문구(`form`) */
export interface GradeFieldErrors {
    cells: Record<string, Partial<Record<GradeCellField, string>>>;
    policy: string;
    form: string;
}

/** 저장된 값과의 차이 — `rules`(이름·승급 조건·추가·삭제·순서) 와 `badge`(뱃지 색·아이콘) 칸 수. 구역 내비의 "바뀜" 이 이 구분을 쓴다 */
export interface GradeChangeSummary {
    rules: number;
    badge: number;
}

/** 뱃지 색 하나 — 색 이름과 라이트에서의 글자 단계. 값은 색 토큰으로 푼다(`badgeStyle`) */
export interface GradeBadgeColorDef {
    name: GradeBadgeColor;
    /** 라이트의 글자 단계 — 연한 100 바탕 위에서 4.5:1 이상이 되도록 고른다 */
    fgLight: 700 | 800;
}

const FIELDS: readonly GradeField[] = ['minDays', 'minActivity'];

/** 칸별 허용 범위 [최소, 최대] — community V33 의 CHECK 와 같다 */
const LIMITS: Record<GradeField, readonly [number, number]> = {
    minDays: [0, 3650],
    minActivity: [0, 100_000],
};

/**
 * 뱃지 색 목록 — 고정(자유 입력 금지). 단계는 Aura 팔레트 값으로 잰 WCAG 대비 4.5:1 이상을 기준으로 골랐다.
 * 라이트(바탕 100): 700 은 gray 9.4 · blue 5.5 · indigo 6.4 · violet 6.0 · pink 5.1 · red 5.3 이고, orange 4.52 · amber 4.51 · green 4.57 · teal 4.86 · cyan 4.79 는 여유가 적어 800(6.4~6.7)을 쓴다.
 * 다크(바탕 950, 글자 300): 8.0(indigo) ~ 13.7(gray) 이다.
 */
const BADGE_COLORS: readonly GradeBadgeColorDef[] = [
    { name: 'gray', fgLight: 700 },
    { name: 'blue', fgLight: 700 },
    { name: 'indigo', fgLight: 700 },
    { name: 'violet', fgLight: 700 },
    { name: 'pink', fgLight: 700 },
    { name: 'red', fgLight: 700 },
    { name: 'orange', fgLight: 800 },
    { name: 'amber', fgLight: 800 },
    { name: 'green', fgLight: 800 },
    { name: 'teal', fgLight: 800 },
    { name: 'cyan', fgLight: 800 },
];

/** 뱃지 아이콘 목록 — PrimeIcons(설치된 primeicons 에 있는 클래스만). 이름 문구는 `grade.icon.<이름>` */
const BADGE_ICONS: readonly string[] = [
    'circle', 'circle-fill', 'star', 'star-fill', 'trophy', 'crown', 'heart', 'heart-fill', 'bolt', 'flag', 'bookmark', 'shield',
    'verified', 'sparkles', 'megaphone', 'thumbs-up', 'check-circle', 'gift', 'key', 'lightbulb', 'face-smile', 'user',
].map((name) => `pi pi-${name}`);

/** 등급 역할 아이디의 접두어 — 역할 아이디는 `ROLE_GRADE_<gradeKey>` 로 파생한다 */
const ROLE_PREFIX = 'ROLE_GRADE_';

/** 서버 칸 이름 읽기 — `grades[1].minDays`(번호는 요청 `grades` 의 차례) · `LOYAL.minDays`(등급 키) */
const INDEXED = /^grades\[(\d+)\]\.(label|minDays|minActivity|badgeColor|badgeIcon)$/;
const CODED = /^([A-Za-z0-9_]+)\.(label|minDays|minActivity|badgeColor|badgeIcon)$/;

export default class UtilGrade {
    /** 등급 수의 하한·상한 — 최소 2단계 · 최대 10단계(확정) */
    static readonly MIN_GRADES = 2;
    static readonly MAX_GRADES = 10;

    /** 등급 이름의 최대 길이 — community `label varchar(8)` */
    static readonly LABEL_MAX = 8;

    /** 승급 조건 칸 둘 */
    static readonly FIELDS: readonly GradeField[] = FIELDS;

    /** 뱃지 색 목록 — 고정 */
    static readonly BADGE_COLORS: readonly GradeBadgeColorDef[] = BADGE_COLORS;

    /** 뱃지 아이콘 목록(PrimeIcons 클래스) — 고정. "아이콘 없음"은 목록에 넣지 않고 빈 글자(`''`)·`null` 로 나타낸다 */
    static readonly BADGE_ICONS: readonly string[] = BADGE_ICONS;

    /** 로딩 전·실패·목록에 없는 등급 키일 때의 뱃지 — 중립(gray)에 아이콘 없음 */
    static readonly FALLBACK_BADGE: { color: GradeBadgeColor; icon: string | null } = { color: 'gray', icon: null };

    /** 수동 상향 회원 옵션의 두 값 — 보이는 차례. 기본값은 `CONTINUE_AUTO`(가정) */
    static readonly POLICIES: readonly GradeManualPolicy[] = ['CONTINUE_AUTO', 'STOP_AUTO'];
    static readonly DEFAULT_POLICY: GradeManualPolicy = 'CONTINUE_AUTO';

    /**
     * 칸의 허용 범위.
     * @param {GradeField} field - 칸 이름.
     * @returns {readonly [number, number]} [최소, 최대].
     */
    static limitsOf(field: GradeField): readonly [number, number] {
        return LIMITS[field];
    }

    /**
     * 숫자를 천 단위 쉼표로 쓴다(언어와 무관하게 같은 모양).
     * @param {number} value - 수.
     * @returns {string} "3,650".
     */
    static format(value: number): string {
        return value.toLocaleString('en-US');
    }

    /**
     * 등급 키로 파생한 등급 역할 아이디 — `ROLE_GRADE_<gradeKey>`.
     * @param {GradeKey} gradeKey - 등급 키.
     * @returns {string} 역할 아이디.
     */
    static roleIdOf(gradeKey: GradeKey): string {
        return `${ROLE_PREFIX}${gradeKey}`;
    }

    /**
     * 등급 역할의 등급 키 — 등급 유형(`memberGrade`)의 역할만 값이 있다.
     * @param {string} roleId - 역할 아이디.
     * @param {string} typeId - 역할 유형 id.
     * @returns {GradeKey | null} 등급 키. 등급 역할이 아니면 null.
     */
    static gradeKeyOfRole(roleId: string, typeId: string): GradeKey | null {
        return typeId === 'memberGrade' && roleId.startsWith(ROLE_PREFIX) ? roleId.slice(ROLE_PREFIX.length) : null;
    }

    /**
     * 서버가 준 목록에서 한 등급을 찾는다.
     * @param {readonly Grade[]} grades - 서버가 준 등급 목록.
     * @param {GradeKey} key - 등급 키.
     * @returns {Grade | undefined} 그 등급.
     */
    static gradeOf(grades: readonly Grade[], key: GradeKey): Grade | undefined {
        return grades.find((grade) => grade.gradeKey === key);
    }

    /**
     * 서버가 준 목록으로 입력 행을 채운다 — 서열(`level`) 순서로.
     * @param {readonly Grade[]} grades - 서버가 준 등급 목록.
     * @returns {GradeRowForm[]} 낮은 등급부터 행.
     */
    static formOf(grades: readonly Grade[]): GradeRowForm[] {
        return [...grades]
            .sort((a, b) => a.level - b.level)
            .map((g) => ({ id: g.gradeKey, gradeKey: g.gradeKey, label: g.label, minDays: String(g.minDays), minActivity: String(g.minActivity), color: g.badgeColor, icon: g.badgeIcon ?? '', memberCount: g.memberCount }));
    }

    /**
     * 새 등급 행 — 이름은 비우고, 승급 조건은 마지막 등급과 같게(위 등급일수록 같거나 커야 해서), 뱃지는 아직 안 쓴 색과 별 아이콘으로 채운다.
     * @param {readonly GradeRowForm[]} rows - 지금 행들.
     * @param {number} seq - 새 행 번호(화면 안 식별자용).
     * @returns {GradeRowForm} 새 행.
     */
    static newRow(rows: readonly GradeRowForm[], seq: number): GradeRowForm {
        const last = rows[rows.length - 1];
        const used = new Set(rows.map((r) => r.color));
        const color = BADGE_COLORS.find((c) => !used.has(c.name))?.name ?? 'gray';
        return { id: `new-${seq}`, gradeKey: null, label: '', minDays: last?.minDays ?? '0', minActivity: last?.minActivity ?? '0', color, icon: 'pi pi-star', memberCount: 0 };
    }

    /**
     * 칸의 글자를 정수로 읽는다 — 앞뒤 공백과 천 단위 쉼표는 무시하고 숫자만 있어야 한다.
     * @param {string} text - 입력한 글자.
     * @returns {number | null} 읽은 정수. 읽을 수 없으면 null.
     */
    static parse(text: string): number | null {
        const cleaned = text.trim().replace(/,/g, '');
        return /^\d{1,9}$/.test(cleaned) ? Number(cleaned) : null;
    }

    /**
     * 모든 행을 검사한다 — 이름(필수·최대 길이·고유), 승급 조건(비어 있음·정수·범위, 첫 등급은 조건이 없어 건너뛴다), 그다음 **위 등급일수록 같거나 커야** 하는 순서.
     * 순서 문제는 더 높은(목록에서 아래) 등급의 칸에 붙는다(그 칸을 고치거나 순서를 바꾸면 풀린다).
     * @param {readonly GradeRowForm[]} rows - 입력 행(낮은 등급부터).
     * @returns {GradeProblems} 문제가 있는 칸만.
     */
    static validate(rows: readonly GradeRowForm[]): GradeProblems {
        const out: GradeProblems = {};
        const put = (id: string, field: 'label' | GradeField, problem: GradeProblem) => {
            out[id] = { ...out[id], [field]: problem };
        };
        const seen = new Set<string>();
        rows.forEach((row, i) => {
            const name = row.label.trim();
            if (!name) put(row.id, 'label', 'LABEL_REQUIRED');
            else if ([...name].length > UtilGrade.LABEL_MAX) put(row.id, 'label', 'LABEL_LENGTH');
            else if (seen.has(name.toLowerCase())) put(row.id, 'label', 'LABEL_DUPLICATE');
            if (name) seen.add(name.toLowerCase());
            if (i === 0) return; // 첫 등급은 기본 등급이라 조건이 없다
            for (const field of FIELDS) {
                const text = row[field];
                if (text.trim() === '') {
                    put(row.id, field, 'REQUIRED');
                    continue;
                }
                const n = UtilGrade.parse(text);
                if (n === null) {
                    put(row.id, field, 'NOT_INTEGER');
                    continue;
                }
                const [min, max] = LIMITS[field];
                if (n < min || n > max) put(row.id, field, 'OUT_OF_RANGE');
            }
        });
        for (let i = 2; i < rows.length; i++) {
            const lower = rows[i - 1]!;
            const upper = rows[i]!;
            for (const field of FIELDS) {
                if (out[upper.id]?.[field] || out[lower.id]?.[field]) continue; // 읽을 수 없는 칸이 있으면 순서는 따지지 않는다
                if (UtilGrade.parse(upper[field])! < UtilGrade.parse(lower[field])!) put(upper.id, field, 'ORDER');
            }
        }
        return out;
    }

    /**
     * 문제가 있는 칸이 몇 개인가.
     * @param {GradeProblems} problems - `validate` 의 결과.
     * @returns {number} 칸 수.
     */
    static problemCount(problems: GradeProblems): number {
        return Object.values(problems).reduce((sum, row) => sum + Object.keys(row ?? {}).length, 0);
    }

    /**
     * 순서 문제 문구에서 비교하는 바로 아래 등급 — 첫 등급(기본)과의 비교는 문제가 되지 않으므로 둘째 등급 아래는 값이 없다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {string} id - 문제가 붙은 행 id.
     * @param {GradeField} field - 칸 이름.
     * @returns {{ label: string; value: number } | null} 아래 등급의 이름과 값.
     */
    static lowerOf(rows: readonly GradeRowForm[], id: string, field: GradeField): { label: string; value: number } | null {
        const at = rows.findIndex((row) => row.id === id);
        const lower = at > 1 ? rows[at - 1] : undefined;
        const value = lower ? UtilGrade.parse(lower[field]) : null;
        return lower && value !== null ? { label: lower.label.trim() || '—', value } : null;
    }

    /**
     * 저장된 값과의 차이를 센다 — 이름·승급 조건의 바뀐 칸, 추가한 등급, 지운 등급, 순서 바뀜(1칸)은 `rules`, 기존 등급의 뱃지 색·아이콘은 `badge`.
     * 읽을 수 없는 승급 조건 칸은 바뀐 것으로 센다. 새 등급의 뱃지는 "추가"에 포함한다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {readonly Grade[]} saved - 저장된 값.
     * @returns {GradeChangeSummary} `rules`·`badge` 칸 수.
     */
    static changes(rows: readonly GradeRowForm[], saved: readonly Grade[]): GradeChangeSummary {
        const out: GradeChangeSummary = { rules: 0, badge: 0 };
        rows.forEach((row, i) => {
            const before = row.gradeKey === null ? undefined : UtilGrade.gradeOf(saved, row.gradeKey);
            if (!before) {
                out.rules++; // 추가
                return;
            }
            if (row.label.trim() !== before.label) out.rules++;
            if (i > 0) for (const field of FIELDS) if (UtilGrade.parse(row[field]) !== before[field]) out.rules++;
            if (row.color !== before.badgeColor) out.badge++;
            if (row.icon !== (before.badgeIcon ?? '')) out.badge++;
        });
        const kept = new Set(rows.map((r) => r.gradeKey).filter((key): key is GradeKey => key !== null));
        out.rules += saved.filter((g) => !kept.has(g.gradeKey)).length; // 삭제
        const order = (list: readonly (GradeKey | null)[]) => list.filter((key): key is GradeKey => key !== null && saved.some((g) => g.gradeKey === key)).join('|');
        const before = [...saved].sort((a, b) => a.level - b.level).filter((g) => kept.has(g.gradeKey)).map((g) => g.gradeKey).join('|');
        if (order(rows.map((r) => r.gradeKey)) !== before) out.rules++;
        return out;
    }

    /**
     * 승급 조건 칸 하나가 저장된 값과 다른가 — 글자가 아니라 수로 견준다. 새 등급은 다르다고 본다.
     * @param {GradeRowForm} row - 그 등급의 행.
     * @param {readonly Grade[]} saved - 저장된 값.
     * @param {GradeField} field - 칸 이름.
     * @returns {boolean} 다르면 true.
     */
    static cellChanged(row: GradeRowForm, saved: readonly Grade[], field: GradeField): boolean {
        const before = row.gradeKey === null ? undefined : UtilGrade.gradeOf(saved, row.gradeKey);
        return before === undefined || UtilGrade.parse(row[field]) !== before[field];
    }

    /**
     * 기준이 저장된 값보다 **낮아진** 조건이 있는가 — 낮아지면 이미 새 조건을 채운 회원이 소급 승급되므로 저장 전에 영향을 확인한다.
     * 이미 있는 등급(첫 등급 제외)만 견주고, 읽을 수 없는 칸은 따지지 않는다(그 전에 입력 검사가 막는다).
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {readonly Grade[]} saved - 저장된 값.
     * @returns {Record<GradeField, boolean>} 일수·글·댓글 중 어느 조건이 낮아졌는가.
     */
    static lowered(rows: readonly GradeRowForm[], saved: readonly Grade[]): Record<GradeField, boolean> {
        const out: Record<GradeField, boolean> = { minDays: false, minActivity: false };
        rows.forEach((row, i) => {
            if (i === 0 || row.gradeKey === null) return;
            const before = UtilGrade.gradeOf(saved, row.gradeKey);
            if (!before) return;
            for (const field of FIELDS) {
                const now = UtilGrade.parse(row[field]);
                if (now !== null && now < before[field]) out[field] = true;
            }
        });
        return out;
    }

    /**
     * 새로 추가한 등급이 있는가 — 이미 조건을 채운 회원이 그 등급이 될 수 있어 저장 전에 영향을 확인한다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @returns {boolean} 있으면 true.
     */
    static hasAdded(rows: readonly GradeRowForm[]): boolean {
        return rows.some((row) => row.gradeKey === null);
    }

    /**
     * 저장하면 지워지는 기존 등급이 있는가 — 지우면 그 등급의 회원이 바로 아래 등급으로 이동하므로 저장 전에 확인한다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {readonly Grade[]} saved - 저장된 값.
     * @returns {boolean} 있으면 true.
     */
    static hasRemoved(rows: readonly GradeRowForm[], saved: readonly Grade[]): boolean {
        const kept = new Set(rows.map((row) => row.gradeKey));
        return saved.some((grade) => !kept.has(grade.gradeKey));
    }

    /**
     * 저장 요청을 만든다 — 목록 순서가 곧 서열이다. 새 등급은 `gradeKey` 를 빼고, 첫 등급은 승급 조건을 보내지 않는다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {GradeManualPolicy} manualPolicy - 수동 상향 회원 옵션.
     * @returns {GradesSaveRequest | null} 요청. 입력이 맞지 않으면 null.
     */
    static toRequest(rows: readonly GradeRowForm[], manualPolicy: GradeManualPolicy): GradesSaveRequest | null {
        if (UtilGrade.problemCount(UtilGrade.validate(rows)) > 0) return null;
        const grades: GradeRequest[] = rows.map((row, i) => {
            const base: GradeRequest = { label: row.label.trim(), badgeColor: row.color, badgeIcon: row.icon ? row.icon : null };
            if (row.gradeKey !== null) base.gradeKey = row.gradeKey;
            if (i > 0) {
                base.minDays = UtilGrade.parse(row.minDays)!;
                base.minActivity = UtilGrade.parse(row.minActivity)!;
            }
            return base;
        });
        return { grades, manualPolicy };
    }

    /**
     * 위로·아래로 옮길 수 있는가 — 첫 등급(기본)은 옮길 수 없고, 다른 등급도 첫 등급 위로는 올라갈 수 없다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {number} index - 옮길 행의 위치.
     * @param {-1 | 1} step - 위로(-1)·아래로(1).
     * @returns {boolean} 옮길 수 있으면 true.
     */
    static canMove(rows: readonly GradeRowForm[], index: number, step: -1 | 1): boolean {
        if (index < 1) return false;
        const to = index + step;
        return to >= 1 && to < rows.length;
    }

    /**
     * 행을 한 칸 옮긴 새 목록.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {number} index - 옮길 행의 위치.
     * @param {-1 | 1} step - 위로(-1)·아래로(1).
     * @returns {GradeRowForm[]} 옮긴 목록(옮길 수 없으면 그대로).
     */
    static moved(rows: readonly GradeRowForm[], index: number, step: -1 | 1): GradeRowForm[] {
        if (!UtilGrade.canMove(rows, index, step)) return [...rows];
        const next = [...rows];
        const [row] = next.splice(index, 1);
        next.splice(index + step, 0, row!);
        return next;
    }

    /**
     * 지울 수 있는 자리인가 — 첫 등급(기본)은 지울 수 없고, 등급은 최소 개수 아래로 줄일 수 없다.
     * @param {readonly GradeRowForm[]} rows - 입력 행.
     * @param {number} index - 지울 행의 위치.
     * @returns {boolean} 지울 수 있으면 true.
     */
    static canRemove(rows: readonly GradeRowForm[], index: number): boolean {
        return index >= 1 && rows.length > UtilGrade.MIN_GRADES;
    }

    /**
     * 뱃지의 색 변수 — 연한 색 바탕 + 진한 글자·아이콘(라이트), 어두운 면 + 밝은 글자(다크). 값은 모두 PrimeVue 색 토큰이고 `light-dark()` 로 테마를 따른다(색을 코드에 적지 않는다).
     * 알 수 없는 색 이름은 중립(gray)으로 본다.
     * @param {string} color - 색 이름.
     * @returns {Record<string, string>} `--gb-bg` · `--gb-fg` 를 담은 스타일 객체.
     */
    static badgeStyle(color: string): Record<string, string> {
        const def = BADGE_COLORS.find((c) => c.name === color) ?? BADGE_COLORS[0]!;
        return {
            '--gb-bg': `light-dark(var(--p-${def.name}-100), var(--p-${def.name}-950))`,
            '--gb-fg': `light-dark(var(--p-${def.name}-${def.fgLight}), var(--p-${def.name}-300))`,
        };
    }

    /**
     * 서버가 거절한 칸별 문구를 읽는다(`400 VALIDATION` 의 `fields`) — `grades[i].칸` · `등급키.칸` 은 그 행·칸 아래에, `manualPolicy` 는 옵션 아래에, 그 밖의 문구는 칸에 붙지 않는 문구(`form`)로 모은다.
     * @param {unknown} e - 잡은 오류.
     * @param {readonly GradeRowForm[]} rows - 요청을 보낼 때의 입력 행(요청 `grades` 의 차례와 같다).
     * @returns {GradeFieldErrors | null} 칸별 문구. 입력 오류가 아니면 null.
     */
    static fieldErrorsOf(e: unknown, rows: readonly GradeRowForm[]): GradeFieldErrors | null {
        if (!(e instanceof ApiError) || e.status !== 400 || !e.fields) return null;
        const cells: GradeFieldErrors['cells'] = {};
        const rest: string[] = [];
        let policy = '';
        for (const [name, message] of Object.entries(e.fields)) {
            if (name === 'manualPolicy') {
                policy = message;
                continue;
            }
            const indexed = INDEXED.exec(name);
            const coded = indexed ? null : CODED.exec(name);
            const row = indexed ? rows[Number(indexed[1])] : coded ? rows.find((r) => r.gradeKey === coded[1]) : undefined;
            const field = (indexed?.[2] ?? coded?.[2]) as GradeCellField | undefined;
            if (row && field) cells[row.id] = { ...cells[row.id], [field]: message };
            else rest.push(message);
        }
        if (Object.keys(cells).length === 0 && rest.length === 0 && !policy) return null;
        return { cells, policy, form: rest.join(' ') };
    }
}
