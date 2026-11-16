import type { LottoPickMode, RoulettePrize } from '~/composables/pointgame/api/usePointGameApi.types';

/**
 * 포인트 게임(룰렛 · 로또 번호 뽑기) 규칙 — 입구 주소, 룰렛 판의 칸 각도, 로또 번호 조건 검사, 공 색을 계산하는 순수 함수. 상태도 요청도 없다.
 * 판정(차감·결과·횟수·보관 한도)은 서버다(후보 `PointGameService` — 아직 없음, 목업 `composables/pointgame/api/mock/point-game-mock.ts`). 화면은 미리 알리고 단추를 끈다.
 * 사이트는 돈·실물 상금을 주지 않는다 — 룰렛은 적립 포인트로 돌려 적립 포인트로 받고, 로또는 번호만 뽑아 준다(당첨 번호를 만들거나 비교하지 않는다).
 */

/** 룰렛 판의 칸 하나 — 시계 방향 각도(0° = 맨 위 바늘 자리) */
export interface RouletteSlice {
    prize: RoulettePrize;
    /** 시작 각도(도) */
    start: number;
    /** 끝 각도(도) */
    end: number;
}

/** 로또 번호 조건의 문제 — 반자동인데 고정이 없음 · 고정이 너무 많음 · 제외가 너무 많아 뽑을 번호가 모자람 */
export type LottoPickProblem = 'FIXED_REQUIRED' | 'FIXED_TOO_MANY' | 'POOL_TOO_SMALL';

/** 공 색 묶음 — 흔한 로또 관례(1~10 노랑 · 11~20 파랑 · 21~30 빨강 · 31~40 회색 · 41~45 초록) */
export type LottoBallTone = 'yellow' | 'blue' | 'red' | 'gray' | 'green';

/** 만분율 합 — 룰렛 확률은 만분율(`chanceBp`)로 오고 다 더하면 이 값이다 */
const BP_TOTAL = 10_000;

export default class UtilPointGame {
    /** 룰렛 화면 */
    static readonly ROULETTE_PATH = '/games/roulette';
    /** 로또 번호 뽑기 화면 */
    static readonly LOTTO_PATH = '/games/lotto';

    /** 로또 번호 범위와 한 세트의 개수 */
    static readonly LOTTO_MIN = 1;
    static readonly LOTTO_MAX = 45;
    static readonly LOTTO_PICK = 6;
    /** 고정 번호는 다섯 개까지 — 여섯이면 뽑을 것이 없다 */
    static readonly LOTTO_FIXED_MAX = 5;
    /** 제외 번호는 서른아홉 개까지 — 고정이 몇 개든 45 − 고정 − 제외 ≥ 6 − 고정, 곧 제외 ≤ 39 */
    static readonly LOTTO_EXCLUDED_MAX = 39;

    /** 1~45 */
    static lottoNumbers(): number[] {
        return Array.from({ length: UtilPointGame.LOTTO_MAX - UtilPointGame.LOTTO_MIN + 1 }, (_, i) => i + UtilPointGame.LOTTO_MIN);
    }

    /**
     * 판의 칸 — 칸 크기는 실제 확률이다(꽝 70% 면 판의 70%). 받은 차례대로 시계 방향으로 이어 붙인다.
     * @param {readonly RoulettePrize[]} prizes - 상품(서버 차례).
     * @returns {RouletteSlice[]} 칸들. 합이 만분율 10,000 이 아니어도 비율로 나눈다.
     */
    static slices(prizes: readonly RoulettePrize[]): RouletteSlice[] {
        const total = prizes.reduce((sum, p) => sum + Math.max(0, p.chanceBp), 0) || BP_TOTAL;
        let at = 0;
        return prizes.map((prize) => {
            const size = (Math.max(0, prize.chanceBp) / total) * 360;
            const slice = { prize, start: at, end: at + size };
            at += size;
            return slice;
        });
    }

    /**
     * 칸 하나의 SVG 경로(부채꼴) — 중심 (c, c), 반지름 r
     * @param {number} c - 중심 좌표.
     * @param {number} r - 반지름.
     * @param {number} start - 시작 각도(도, 0° = 위, 시계 방향).
     * @param {number} end - 끝 각도.
     * @returns {string} `d` 값. 한 바퀴 전부면 원 두 반쪽.
     */
    static slicePath(c: number, r: number, start: number, end: number): string {
        if (end - start >= 359.999) return `M ${c} ${c - r} A ${r} ${r} 0 1 1 ${c} ${c + r} A ${r} ${r} 0 1 1 ${c} ${c - r} Z`;
        const [x1, y1] = UtilPointGame.point(c, r, start);
        const [x2, y2] = UtilPointGame.point(c, r, end);
        const large = end - start > 180 ? 1 : 0;
        return `M ${c} ${c} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`;
    }

    /**
     * 원 위의 점 — 0° 가 위, 시계 방향
     * @param {number} c - 중심 좌표.
     * @param {number} r - 반지름.
     * @param {number} deg - 각도(도).
     * @returns {[number, number]} x, y(소수 둘째 자리).
     */
    static point(c: number, r: number, deg: number): [number, number] {
        const rad = ((deg - 90) * Math.PI) / 180;
        return [Math.round((c + r * Math.cos(rad)) * 100) / 100, Math.round((c + r * Math.sin(rad)) * 100) / 100];
    }

    /**
     * 결과 칸에 바늘(맨 위)이 오도록 판을 더 돌릴 각도 — 지금 각도에서 최소 `turns` 바퀴 더 돈다.
     * 칸 안 어디에 멈출지는 `jitter`(0~1)로 정하되 칸 끝 15% 는 피한다(경계에 멈춰 다른 칸으로 보이지 않게). 아주 좁은 칸(500P 0.5%)은 가운데에 멈춘다.
     * @param {number} current - 지금까지 돈 누적 각도.
     * @param {RouletteSlice} slice - 결과 칸.
     * @param {number} jitter - 0~1 난수.
     * @param {number} turns - 더 돌 최소 바퀴 수.
     * @returns {number} 새 누적 각도(지금보다 크다).
     */
    static targetRotation(current: number, slice: RouletteSlice, jitter: number, turns = 6): number {
        const width = slice.end - slice.start;
        const margin = width * 0.15;
        const at = width < 4 ? slice.start + width / 2 : slice.start + margin + (width - margin * 2) * Math.min(1, Math.max(0, jitter));
        // 판을 R 만큼 돌리면 바늘 아래는 (at + R) mod 360 = 0 이어야 한다
        const wanted = (360 - (at % 360)) % 360;
        const now = ((current % 360) + 360) % 360;
        const delta = (wanted - now + 360) % 360;
        return current + turns * 360 + delta;
    }

    /**
     * 평균 돌려받는 비율 — Σ(상품 × 확률) ÷ 한 번 값
     * @param {readonly RoulettePrize[]} prizes - 상품.
     * @param {number} cost - 한 번 값.
     * @returns {number} 0~1 이상(예: 0.196). 값이 0 이면 0.
     */
    static expectedReturn(prizes: readonly RoulettePrize[], cost: number): number {
        if (cost <= 0) return 0;
        const expected = prizes.reduce((sum, p) => sum + (p.points * p.chanceBp) / BP_TOTAL, 0);
        return expected / cost;
    }

    /**
     * 만분율 → 퍼센트 글자(소수는 있을 때만 한 자리) — `7000` → `70`, `150` → `1.5`
     * @param {number} bp - 만분율.
     * @returns {string} 숫자 글자(퍼센트 기호 없음).
     */
    static percent(bp: number): string {
        const value = bp / 100;
        return Number.isInteger(value) ? String(value) : value.toFixed(1).replace(/\.0$/, '');
    }

    /**
     * 번호 조건 검사 — 자동은 조건이 없어 늘 통과. 반자동은 고정 1~5개, 고정·제외를 뺀 나머지에서 모자란 수만큼 뽑을 수 있어야 한다(서버도 같은 검사 — 후보 `LottoPickRule`).
     * 같은 번호가 고정과 제외에 함께 있을 수는 없다(화면이 한쪽만 고르게 한다).
     * @param {LottoPickMode} mode - 자동 · 반자동.
     * @param {readonly number[]} fixed - 꼭 넣을 번호.
     * @param {readonly number[]} excluded - 뺄 번호.
     * @returns {LottoPickProblem | null} 문제. 없으면 null.
     */
    static pickProblem(mode: LottoPickMode, fixed: readonly number[], excluded: readonly number[]): LottoPickProblem | null {
        if (mode === 'AUTO') return null;
        if (fixed.length === 0) return 'FIXED_REQUIRED';
        if (fixed.length > UtilPointGame.LOTTO_FIXED_MAX) return 'FIXED_TOO_MANY';
        const pool = UtilPointGame.LOTTO_MAX - fixed.length - excluded.length;
        if (pool < UtilPointGame.LOTTO_PICK - fixed.length) return 'POOL_TOO_SMALL';
        return null;
    }

    /**
     * 공 색 — 흔한 로또 관례
     * @param {number} n - 번호(1~45).
     * @returns {LottoBallTone} 색 묶음.
     */
    static ballTone(n: number): LottoBallTone {
        if (n <= 10) return 'yellow';
        if (n <= 20) return 'blue';
        if (n <= 30) return 'red';
        if (n <= 40) return 'gray';
        return 'green';
    }

    /**
     * 공 모양 클래스 — 꽉 찬 색 공이라 라이트·다크 두 테마에서 같은 색이다(바탕이 아니라 공이 대비를 가진다).
     * 노랑·초록은 진한 글자, 나머지는 흰 글자(글자와 공의 대비 4.5:1 이상이 되게 어두운 쪽 색을 골랐다).
     * @param {number} n - 번호.
     * @returns {string} Tailwind 클래스.
     */
    static ballClass(n: number): string {
        switch (UtilPointGame.ballTone(n)) {
            case 'yellow':
                return 'bg-[#f5b700] text-[#3a2c00]';
            case 'blue':
                return 'bg-[#1f6fd1] text-white';
            case 'red':
                return 'bg-[#d23c3c] text-white';
            case 'gray':
                return 'bg-[#5f6672] text-white';
            default:
                return 'bg-[#7cc243] text-[#173300]';
        }
    }

    /**
     * 복사할 글자 — 작은 수부터 쉼표로 `3, 11, 19, 27, 38, 44`
     * @param {readonly number[]} numbers - 번호들.
     * @returns {string} 글자.
     */
    static copyText(numbers: readonly number[]): string {
        return [...numbers].sort((a, b) => a - b).join(', ');
    }

    /**
     * 열어도 되는 당첨 확인 주소인가 — `https://` 절대 주소만(관리자 설정 검사와 같은 규칙). 아니면 링크를 그리지 않는다
     * @param {string | null | undefined} url - 주소.
     * @returns {boolean} 열어도 되면 true.
     */
    static safeLink(url: string | null | undefined): boolean {
        if (!url) return false;
        try {
            return new URL(url).protocol === 'https:';
        } catch {
            return false;
        }
    }
}
