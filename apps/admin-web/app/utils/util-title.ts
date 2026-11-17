import type { TitleTier } from '#layers/member-title/utils/util-title-icon';
import type { AdminTitle, AdminTitleRequest, TitleChipColor, TitleCondition, TitleConditionType, TitleLook, TitleMethod, TitleMetric } from '~/composables/title/api/useTitleApi.types';

/**
 * 칭호 편집 서랍의 입력 값 — 요청(`AdminTitleRequest`)과 같은 칸(서버 `TitleSpec` 이름 그대로). 키는 입력하지 않는다(만들 때 비워 보내면 서버가 발급).
 */
export type TitleForm = Omit<AdminTitleRequest, 'key'>;

/** 칭호 편집 서랍의 입력 문제 — 화면이 미리 알려 주는 용도(판정은 서버 — 이름 6자·등급 이름 겹침) */
export type TitleFormProblem = 'NAME_REQUIRED' | 'NAME_TOO_LONG' | 'TIERS_REQUIRED' | 'TIERS_ORDER' | 'CONDITION_VALUE';

/**
 * 회원 칭호 관리 화면의 순수 규칙(member-title-contract) — 기본 칭호 키 · 칩 색 클래스 · 최근 달 목록 · 편집 서랍의 빈 값·검사·요청 만들기.
 * 판정은 서버다(서버 `AdminTitleService` 검사·`BuiltinTitles`). 문구가 낀 것은 `UtilTitleMessages`.
 */
export default class UtilTitle {
    /** 관리자 화면 주소 — 네 탭 */
    static readonly PATHS = {
        titles: '/members/titles',
        review: '/members/titles/review',
        monthly: '/members/titles/monthly',
        grants: '/members/titles/grants',
    } as const;

    /** 기본 칭호 키(서버 `BuiltinTitles`) — 작가 칭호는 키로 특별 규칙이 붙는다 */
    static readonly MONTHLY_KEY = 'monthly-writer';
    static readonly YEARLY_KEY = 'yearly-writer';
    static readonly HONORARY_KEY = 'honorary-writer';

    /** 이름 최대 글자(서버 규칙 — 공백 뺀 6자) */
    static readonly NAME_MAX = 6;
    /** 이달의 작가 한 달 최대 인원 범위 */
    static readonly MONTHLY_MAX_RANGE = { min: 1, max: 20 } as const;
    /** 명예 작가가 되는 이달의 작가 횟수 범위 */
    static readonly HONORARY_RANGE = { min: 1, max: 24 } as const;
    /** 사유 최대 글자(서버 `AdminTitleAwardService.reason` — 500자) */
    static readonly REASON_MAX = 500;

    /** 조건 종류 차례(편집 서랍의 「조건 더하기」) */
    static readonly CONDITION_TYPES: readonly TitleConditionType[] = ['GRADE_AT_LEAST', 'POSTS', 'POPULAR_DAYS', 'ATTENDANCE_DAYS', 'NO_REPORT_HIDDEN'];

    /** 받는 방법 차례 */
    static readonly METHODS: readonly TitleMethod[] = ['AUTO', 'APPLY', 'AWARD'];

    /**
     * 칩 색 클래스 — 보라 = 수여·심사(시안 `#EEEDFE`/`#3C3489`) · 초록 = 자동(`#E1F5EE`/`#085041`).
     * @param {TitleChipColor | string} chip - 서버 `chip`.
     * @returns {string} Tailwind 클래스.
     */
    static chipClass(chip: TitleChipColor | string): string {
        if (chip === 'GREEN') return 'bg-[#E1F5EE] text-[#085041] dark:bg-emerald-900/40 dark:text-emerald-200';
        return 'bg-[#EEEDFE] text-[#3C3489] dark:bg-violet-900/40 dark:text-violet-200';
    }

    /**
     * 받는 방법의 기본 칩 색 — 자동은 초록, 수여·심사는 보라(계약 「관리자 칭호 만들기·고치기 서랍」).
     * @param {TitleMethod} method - 받는 방법.
     * @returns {TitleChipColor} 칩 색.
     */
    static chipOf(method: TitleMethod): TitleChipColor {
        return method === 'AUTO' ? 'GREEN' : 'PURPLE';
    }

    /**
     * 칭호 줄 → 칩 모양(목록·고르개). 단계 칭호는 금색 그림으로 보인다.
     * @param {Pick<AdminTitle, 'key' | 'name' | 'nameEn' | 'icon' | 'chip' | 'tierMetric'>} title - 칭호.
     * @param {TitleTier | null} tier - 보일 단계(없으면 단계 칭호는 금).
     * @returns {TitleLook} 칩 모양.
     */
    static lookOf(title: Pick<AdminTitle, 'key' | 'name' | 'nameEn' | 'icon' | 'chip' | 'tierMetric'>, tier: TitleTier | null = null): TitleLook {
        return { key: title.key, label: title.name, labelEn: title.nameEn, icon: title.icon, tier: title.tierMetric ? (tier ?? 'GOLD') : null, chip: title.chip };
    }

    /**
     * 최근 달 목록 — 지금 달부터 거꾸로 `count` 개(`YYYY-MM`). 이달의 작가는 보통 지난달을 뽑으므로 화면은 둘째 칸을 기본으로 고른다.
     * @param {Date} now - 기준 시각(브라우저 달 — 달 경계 하루 차이는 운영자가 고른다).
     * @param {number} count - 개수.
     * @returns {string[]} 달 목록(최근 것이 먼저).
     */
    static recentMonths(now: Date, count: number): string[] {
        const out: string[] = [];
        let y = now.getFullYear();
        let m = now.getMonth() + 1;
        for (let i = 0; i < count; i += 1) {
            out.push(`${y}-${String(m).padStart(2, '0')}`);
            m -= 1;
            if (m === 0) {
                m = 12;
                y -= 1;
            }
        }
        return out;
    }

    /**
     * 새 칭호의 빈 입력 — 수여형 · 보라 · 범용 메달 · 켜짐 · 소급 지급 켬.
     * @param {number} sortOrder - 맨 뒤 차례.
     * @returns {TitleForm} 입력 값.
     */
    static emptyForm(sortOrder: number): TitleForm {
        return {
            name: '',
            nameEn: null,
            icon: 'medal',
            chip: 'PURPLE',
            method: 'AWARD',
            reviewer: 'OPERATOR',
            boardId: null,
            conditions: [],
            tierMetric: null,
            tierBronze: null,
            tierSilver: null,
            tierGold: null,
            benefitTrialPriority: false,
            benefitMagazine: false,
            benefitAvatarFrame: false,
            dormantHide: false,
            enabled: true,
            sortOrder,
            retroactive: true,
        };
    }

    /**
     * 목록 줄 → 편집 입력(깊은 사본 — 서랍에서 고쳐도 목록이 따라 바뀌지 않게). 소급 지급은 켠 채로 연다(자동 칭호만 의미).
     * @param {AdminTitle} title - 목록 줄.
     * @returns {TitleForm} 입력 값.
     */
    static formOf(title: AdminTitle): TitleForm {
        return {
            name: title.name,
            nameEn: title.nameEn,
            icon: title.icon,
            chip: title.chip,
            method: title.method,
            reviewer: title.reviewer,
            boardId: title.boardId,
            conditions: title.conditions.map((c) => ({ type: c.type, value: c.value, boardId: c.boardId, boardSlug: c.boardSlug, gradeKey: c.gradeKey })),
            tierMetric: title.tierMetric,
            tierBronze: title.tierBronze,
            tierSilver: title.tierSilver,
            tierGold: title.tierGold,
            benefitTrialPriority: title.benefitTrialPriority,
            benefitMagazine: title.benefitMagazine,
            benefitAvatarFrame: title.benefitAvatarFrame,
            dormantHide: title.dormantHide,
            enabled: title.enabled,
            sortOrder: title.sortOrder,
            retroactive: true,
        };
    }

    /**
     * 조건 한 칸의 빈 값.
     * @param {TitleConditionType} type - 종류.
     * @returns {TitleCondition} 조건.
     */
    static emptyCondition(type: TitleConditionType): TitleCondition {
        return { type, value: UtilTitle.counted(type) ? 1 : null, boardId: null, boardSlug: null, gradeKey: null };
    }

    /**
     * 수를 세는 조건인가(값 1 이상이 필요 — 서버 `TitleConditionType.counted`).
     * @param {TitleConditionType} type - 종류.
     * @returns {boolean} 세는 조건이면 true.
     */
    static counted(type: TitleConditionType): boolean {
        return type === 'POSTS' || type === 'POPULAR_DAYS' || type === 'ATTENDANCE_DAYS';
    }

    /**
     * 이름 글자 수 — 서버와 같이 공백을 뺀 글자 수(기본 칭호 「출석 100일」이 6자).
     * @param {string} name - 이름.
     * @returns {number} 공백 뺀 글자 수.
     */
    static nameLength(name: string): number {
        return Array.from(name.replace(/\s+/g, '')).length;
    }

    /**
     * 입력 문제(칸 → 문제) — 빈 칸은 [저장]을 한 번 누른 뒤에 탓한다(부르는 쪽). 서버와 같은 규칙만(칸 경로 키도 서버와 같다).
     * @param {TitleForm} form - 입력 값.
     * @returns {Partial<Record<string, TitleFormProblem>>} 칸 경로(`name`·`tiers`·`conditions[i].value`) → 문제.
     */
    static problems(form: TitleForm): Partial<Record<string, TitleFormProblem>> {
        const out: Partial<Record<string, TitleFormProblem>> = {};
        const nameLength = UtilTitle.nameLength(form.name);
        if (nameLength === 0) out.name = 'NAME_REQUIRED';
        else if (nameLength > UtilTitle.NAME_MAX) out.name = 'NAME_TOO_LONG';
        if (form.method !== 'AWARD' && form.tierMetric) {
            const tiers = [form.tierBronze, form.tierSilver, form.tierGold];
            if (tiers.some((v) => v === null || v < 1)) out.tiers = 'TIERS_REQUIRED';
            else if (!(form.tierBronze! < form.tierSilver! && form.tierSilver! < form.tierGold!)) out.tiers = 'TIERS_ORDER';
        }
        if (form.method !== 'AWARD') {
            form.conditions.forEach((c, i) => {
                if (UtilTitle.counted(c.type) && (c.value === null || c.value < 1)) out[`conditions[${i}].value`] = 'CONDITION_VALUE';
            });
        }
        return out;
    }

    /**
     * 입력 → 요청(가공 없이 다듬기만 — 앞뒤 공백, 단계가 없으면 문턱 비우기, 세지 않는 조건의 값 비우기, 등급 조건이 아니면 등급 키 비우기, 소급은 자동 칭호만).
     * @param {TitleForm} form - 입력 값.
     * @returns {AdminTitleRequest} 요청.
     */
    static toRequest(form: TitleForm): AdminTitleRequest {
        const awarded = form.method === 'AWARD';
        const metric: TitleMetric | null = awarded ? null : form.tierMetric;
        return {
            name: form.name.trim(),
            nameEn: form.nameEn?.trim() || null,
            icon: form.icon,
            chip: form.chip,
            method: form.method,
            reviewer: form.method === 'APPLY' ? form.reviewer : 'OPERATOR',
            boardId: form.method === 'APPLY' && form.reviewer === 'OPERATOR_AND_BOARD_MANAGER' ? form.boardId : null,
            conditions: awarded
                ? []
                : form.conditions.map((c) => ({
                      type: c.type,
                      value: UtilTitle.counted(c.type) ? c.value : null,
                      boardId: c.type === 'POSTS' ? c.boardId : null,
                      boardSlug: c.type === 'POSTS' && !c.boardId ? c.boardSlug : null,
                      gradeKey: c.type === 'GRADE_AT_LEAST' ? c.gradeKey : null,
                  })),
            tierMetric: metric,
            tierBronze: metric ? form.tierBronze : null,
            tierSilver: metric ? form.tierSilver : null,
            tierGold: metric ? form.tierGold : null,
            benefitTrialPriority: form.benefitTrialPriority,
            benefitMagazine: form.benefitMagazine,
            benefitAvatarFrame: form.benefitAvatarFrame,
            dormantHide: form.dormantHide,
            enabled: form.enabled,
            sortOrder: form.sortOrder,
            retroactive: form.method === 'AUTO' && form.retroactive,
        };
    }

    /**
     * 수여 대화창의 기간 칸 — 이달의 작가는 달(`YYYY-MM`), 올해의 작가는 해(`YYYY`), 그 밖은 없음.
     * @param {string} key - 칭호 키.
     * @returns {'MONTH' | 'YEAR' | null} 기간 종류.
     */
    static periodKind(key: string): 'MONTH' | 'YEAR' | null {
        if (key === UtilTitle.MONTHLY_KEY) return 'MONTH';
        if (key === UtilTitle.YEARLY_KEY) return 'YEAR';
        return null;
    }

    /**
     * 단계 칭호의 기본 단계(수여 대화창) — 단계 없는 칭호는 null.
     * @param {Pick<AdminTitle, 'tierMetric'>} title - 칭호.
     * @returns {TitleTier | null} 단계.
     */
    static defaultTier(title: Pick<AdminTitle, 'tierMetric'>): TitleTier | null {
        return title.tierMetric ? 'BRONZE' : null;
    }
}
