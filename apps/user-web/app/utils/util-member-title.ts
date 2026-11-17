import type { MyTitleProgress, TitleChipColor } from '~/composables/title/api/useTitleApi.types';

/**
 * 회원 칭호 화면의 순수 규칙(member-title-contract) — 주소·비율 글자·진행 퍼센트·기간 모양. 문구가 낀 것은 `UtilMemberTitleMessages`.
 * 판정(누가 무엇을 받는가·대표 칭호 기본값)은 서버다. 그림은 `@irobit/member-title` 의 `TitleIcon`.
 */
export default class UtilMemberTitle {
    /** 내 정보 › 칭호 */
    static readonly MY_PATH = '/me/titles';

    /** 명예의 전당 · 칭호 도감(누구나) */
    static readonly HALL_PATH = '/hall-of-fame';

    /** 게시판 스텝 「칭호 신청」 탭 */
    static staffPath(slug: string): string {
        return `/boards/${encodeURIComponent(slug)}/titles`;
    }

    /** 심사 사유 최대 글자(서버 `TitleReviewService` — 500자) */
    static readonly REASON_MAX = 500;

    /**
     * 보유 비율(0~1)을 퍼센트 글자로 — 1% 이상은 정수, 그 아래는 소수 한 자리, 0.1% 미만이면 「0.1」(문구가 「미만」을 붙인다).
     * @param {number} ratio - 0~1.
     * @returns {{ value: string; under: boolean }} 퍼센트 숫자 글자와 0.1% 미만 여부.
     */
    static percent(ratio: number): { value: string; under: boolean } {
        const p = Math.max(0, Math.min(1, Number.isFinite(ratio) ? ratio : 0)) * 100;
        if (p > 0 && p < 0.1) return { value: '0.1', under: true };
        if (p >= 1) return { value: String(Math.round(p)), under: false };
        return { value: p.toFixed(1).replace(/\.0$/, ''), under: false };
    }

    /**
     * 진행 막대의 채움(0~100) — 목표를 넘지 않게 자른다.
     * @param {MyTitleProgress} p - 진행.
     * @returns {number} 퍼센트.
     */
    static progressPercent(p: Pick<MyTitleProgress, 'current' | 'target'>): number {
        if (p.target <= 0) return 100;
        return Math.max(0, Math.min(100, Math.round((p.current / p.target) * 100)));
    }

    /**
     * 기간 글자를 해·달로 — 이달의 작가 `YYYY-MM`, 올해의 작가 `YYYY`. 모양이 다르면 null.
     * @param {string} period - 서버 `periods[]` 한 칸.
     * @returns {{ year: string; month: number | null } | null} 해와 달(해만이면 달 null).
     */
    static parsePeriod(period: string): { year: string; month: number | null } | null {
        const m = /^(\d{4})(?:-(\d{2}))?$/.exec(period);
        if (!m) return null;
        return { year: m[1]!, month: m[2] ? Number(m[2]) : null };
    }

    /**
     * 칩 색 클래스 — 보라 = 수여·심사(시안 `#EEEDFE`/`#3C3489`) · 초록 = 자동(`#E1F5EE`/`#085041`). 모르는 값은 보라.
     * @param {TitleChipColor} chip - 서버 `chip`.
     * @returns {string} Tailwind 클래스.
     */
    static chipClass(chip: TitleChipColor | string): string {
        if (chip === 'GREEN') return 'bg-[#E1F5EE] text-[#085041] dark:bg-emerald-900/40 dark:text-emerald-200';
        return 'bg-[#EEEDFE] text-[#3C3489] dark:bg-violet-900/40 dark:text-violet-200';
    }
}
