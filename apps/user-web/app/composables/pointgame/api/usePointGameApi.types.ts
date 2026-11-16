/**
 * 포인트 게임 API 모양 — ⚠ 목업(화면 확정 전, 2026-11-16). 서버 계약이 없어 화면이 바라는 모양을 먼저 적었다(이름은 서버 DTO 후보).
 * 포인트는 모두 **적립(무상) 포인트**다 — 충전(유상) 포인트는 쓰지 않는다. 잔액도 `freeBalance`(적립 잔액)만 온다.
 */

/** 룰렛 상품 하나 — 꽝은 `points: 0` */
export interface RoulettePrize {
    /** 상품 식별자 — `MISS` · `P10` · `P20` … */
    key: string;
    /** 받는 적립 포인트(꽝 0) */
    points: number;
    /** 확률(만분율 — 7000 = 70%). 다 더하면 10,000. 판의 칸 크기가 이 값이다 */
    chanceBp: number;
}

/** `GET /api/me/point-games/roulette` — 오늘 남은 횟수·상품표·적립 잔액 */
export interface RouletteStatus {
    /** 한 번 돌리는 값(적립 포인트) */
    cost: number;
    /** 하루 최대 횟수 */
    dailyLimit: number;
    /** 오늘(한국 시간) 돌린 횟수 */
    usedToday: number;
    /** 상품표 — 판에 이 차례로 시계 방향으로 놓는다 */
    prizes: RoulettePrize[];
    /** 적립 포인트 잔액 */
    freeBalance: number;
    /** 횟수가 다시 차는 시각(다음 한국 자정, UTC ISO) */
    resetsAt: string;
}

/** `POST /api/me/point-games/roulette/spins` — 결과는 서버가 정한다(화면은 그 칸에 판을 멈출 뿐) */
export interface RouletteSpinResult {
    /** 한 번의 기록 ID */
    spinId: string;
    /** 맞힌 상품의 `key` */
    prizeKey: string;
    /** 받은 적립 포인트(꽝 0) */
    won: number;
    /** 차감·지급 뒤 적립 잔액 */
    freeBalance: number;
    /** 이번을 포함해 오늘 돌린 횟수 */
    usedToday: number;
}

/** 당첨 확인 링크 — 관리자 설정값(복권 사업자가 바뀔 수 있다). 사이트는 당첨 번호를 만들거나 비교하지 않는다 */
export interface LottoCheckLink {
    /** 복권 사업자 이름(예: 동행복권) */
    providerName: string;
    /** 당첨 확인 주소 — `https://` 만 */
    url: string;
}

/** 뽑는 방법 — 자동(6개 모두 무작위) · 반자동(고정 번호 1~5개 + 나머지 무작위, 제외 번호는 선택) */
export type LottoPickMode = 'AUTO' | 'SEMI_AUTO';

/** 뽑은 번호 한 세트 — 회원별로 서버에 남는다 */
export interface LottoDraw {
    id: string;
    /** 작은 수부터 6개(1~45) */
    numbers: number[];
    /** 어떻게 뽑았나 */
    mode: LottoPickMode;
    /** 뽑은 시각(UTC ISO) */
    drawnAt: string;
}

/** `GET /api/me/point-games/lotto` — 값·보관 한도·내 번호(최신이 위)·당첨 확인 링크 */
export interface LottoStatus {
    /** 1세트 값 */
    setCost: number;
    /** 한 번에 뽑는 묶음 크기(5세트) */
    bundleSize: number;
    /** 묶음 값(5세트 80P) */
    bundleCost: number;
    /** 적립 포인트 잔액 */
    freeBalance: number;
    /** 한 회원이 남겨 둘 수 있는 세트 수 */
    storageLimit: number;
    /** 내 번호 — 최신이 위, `storageLimit` 개까지 */
    draws: LottoDraw[];
    /** 당첨 확인 링크 — 설정이 없으면 null(링크를 그리지 않는다) */
    checkLink: LottoCheckLink | null;
}

/** `POST /api/me/point-games/lotto/draws` — 세트 수는 1 또는 묶음 크기. 자동은 고정·제외가 비어야 하고, 반자동은 고정 1~5개(제외는 선택, 1~45, 서로 겹치지 않음) */
export interface LottoDrawRequest {
    sets: number;
    mode: LottoPickMode;
    /** 꼭 넣을 번호 — 자동 0개, 반자동 1~5개 */
    fixed: number[];
    /** 뺄 번호 — 자동 0개, 반자동 선택 */
    excluded: number[];
}

/** 뽑기 결과 — 이번에 뽑은 세트(위에 보일 것)와 새 잔액 */
export interface LottoDrawResult {
    draws: LottoDraw[];
    freeBalance: number;
}
