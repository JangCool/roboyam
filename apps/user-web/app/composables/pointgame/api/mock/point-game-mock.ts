// MOCK: 포인트 게임(룰렛 · 로또 번호 뽑기) — 서버가 없어(화면 확정 전, 2026-11-16) 이 탭의 메모리에 회원별로 둔다. 새로 고치면 처음 값으로 돌아간다.
// 적립 포인트 지갑도 목업이다(처음 1,000P — 헤더·포인트 화면의 실제 잔액과 따로 논다). 서버 API 가 생기면 이 파일과 `usePointGameApi` 의 MOCK 줄을 지운다.
// 개발 스위치(`?mock=`, 쉼표로 여러 개): `game-poor`(처음 잔액 30P) · `game-used`(오늘 룰렛을 다 돌린 상태) · `lotto-full`(내 번호 보관함이 가득) · `game-fail`(읽기 500).
// 문구는 서버 몫이라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import type { LottoCheckLink, LottoDraw, LottoDrawRequest, LottoDrawResult, LottoStatus, RoulettePrize, RouletteSpinResult, RouletteStatus } from '~/composables/pointgame/api/usePointGameApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';

/** 룰렛 상품표 — 평균 돌려받는 양 9.8P(= 50P 의 19.6%, 약 20%) */
const PRIZES: RoulettePrize[] = [
    { key: 'MISS', points: 0, chanceBp: 7000 },
    { key: 'P10', points: 10, chanceBp: 1200 },
    { key: 'P20', points: 20, chanceBp: 800 },
    { key: 'P30', points: 30, chanceBp: 500 },
    { key: 'P50', points: 50, chanceBp: 300 },
    { key: 'P100', points: 100, chanceBp: 150 },
    { key: 'P500', points: 500, chanceBp: 50 },
];
const ROULETTE_COST = 50;
const ROULETTE_DAILY_LIMIT = 5;

const LOTTO_SET_COST = 20;
const LOTTO_BUNDLE_SIZE = 5;
const LOTTO_BUNDLE_COST = 80;
/** 한 회원 보관 한도(가정) — 5세트씩 10번 */
const LOTTO_STORAGE_LIMIT = 50;
/** 당첨 확인 링크 — 관리자 설정의 목업 기본값(admin-web 목업과 같은 값. 두 앱의 목업은 서로 닿지 않는다) */
const CHECK_LINK: LottoCheckLink = { providerName: '동행복권', url: 'https://dhlottery.co.kr' };

const START_BALANCE = 1000;
const POOR_BALANCE = 30;
const KST_MS = 9 * 3600_000;

/** 회원 한 명의 목업 상태 */
interface Wallet {
    freeBalance: number;
    /** 룰렛을 센 날(한국 시간 `YYYY-MM-DD`) */
    day: string;
    usedToday: number;
    draws: LottoDraw[];
}

const wallets = new Map<string, Wallet>();
let seq = 0;

/** 한국 시간의 오늘 */
function kstDay(now = Date.now()): string {
    return new Date(now + KST_MS).toISOString().slice(0, 10);
}

/** 다음 한국 자정(UTC ISO) */
function nextKstMidnight(now = Date.now()): string {
    const k = new Date(now + KST_MS);
    return new Date(Date.UTC(k.getUTCFullYear(), k.getUTCMonth(), k.getUTCDate() + 1) - KST_MS).toISOString();
}

/** 0 이상 max 미만의 정수 */
function randomInt(max: number): number {
    const buf = new Uint32Array(1);
    crypto.getRandomValues(buf);
    return (buf[0] ?? 0) % max;
}

function wallet(owner: string): Wallet {
    const flags = mockFlags();
    let w = wallets.get(owner);
    if (!w) {
        w = { freeBalance: flags.has('game-poor') ? POOR_BALANCE : START_BALANCE, day: kstDay(), usedToday: flags.has('game-used') ? ROULETTE_DAILY_LIMIT : 0, draws: [] };
        if (flags.has('lotto-full')) w.draws = Array.from({ length: LOTTO_STORAGE_LIMIT }, (_, i) => ({ id: `mock-lotto-${++seq}`, numbers: pickNumbers([], []), drawnAt: new Date(Date.now() - (i + 1) * 3600_000).toISOString() }));
        wallets.set(owner, w);
    }
    // 날이 바뀌면 룰렛 횟수가 다시 찬다
    const today = kstDay();
    if (w.day !== today) {
        w.day = today;
        w.usedToday = 0;
    }
    return w;
}

/** 한 세트 — 고정 번호 + 나머지에서 모자란 수만큼 무작위(작은 수부터) */
function pickNumbers(fixed: readonly number[], excluded: readonly number[]): number[] {
    const banned = new Set([...fixed, ...excluded]);
    const pool: number[] = [];
    for (let n = 1; n <= 45; n++) if (!banned.has(n)) pool.push(n);
    for (let i = pool.length - 1; i > 0; i--) {
        const j = randomInt(i + 1);
        [pool[i], pool[j]] = [pool[j] as number, pool[i] as number];
    }
    return [...fixed, ...pool.slice(0, 6 - fixed.length)].sort((a, b) => a - b);
}

function conflict(code: string, message: string): ApiError {
    return new ApiError({ status: 409, code, message });
}

/** 번호 조건 검사 — 서버가 할 검사(화면도 `UtilPointGame.pickProblem` 으로 미리 막는다) */
function validate(body: LottoDrawRequest): void {
    const inRange = (n: number) => Number.isInteger(n) && n >= 1 && n <= 45;
    const fields: Record<string, string> = {};
    if (body.sets !== 1 && body.sets !== LOTTO_BUNDLE_SIZE) fields.sets = `1세트 또는 ${LOTTO_BUNDLE_SIZE}세트만 뽑을 수 있어요.`;
    if (!body.fixed.every(inRange) || new Set(body.fixed).size !== body.fixed.length || body.fixed.length > 5) fields.fixed = '고정 번호는 1~45 중 다섯 개까지 고를 수 있어요.';
    if (!body.excluded.every(inRange) || new Set(body.excluded).size !== body.excluded.length) fields.excluded = '제외 번호를 확인해 주세요.';
    else if (body.excluded.some((n) => body.fixed.includes(n))) fields.excluded = '같은 번호를 고정과 제외에 함께 둘 수 없어요.';
    else if (45 - body.fixed.length - body.excluded.length < 6 - body.fixed.length) fields.excluded = '제외 번호가 너무 많아 번호를 뽑을 수 없어요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
}

export default class PointGameMock {
    static async rouletteStatus(owner: string): Promise<RouletteStatus> {
        await mockDelay();
        if (mockFlags().has('game-fail')) throw MOCK_ERRORS.internal();
        const w = wallet(owner);
        return mockClone({ cost: ROULETTE_COST, dailyLimit: ROULETTE_DAILY_LIMIT, usedToday: w.usedToday, prizes: PRIZES, freeBalance: w.freeBalance, resetsAt: nextKstMidnight() });
    }

    static async spin(owner: string): Promise<RouletteSpinResult> {
        await mockDelay();
        const w = wallet(owner);
        if (w.usedToday >= ROULETTE_DAILY_LIMIT) throw conflict('POINT_GAME_DAILY_LIMIT', '오늘 룰렛은 다 돌렸어요. 내일 다시 돌릴 수 있어요.');
        if (w.freeBalance < ROULETTE_COST) throw conflict('POINT_INSUFFICIENT', '적립 포인트가 모자라요.');
        // 만분율 가중 추첨
        let roll = randomInt(10_000);
        const prize = PRIZES.find((p) => (roll -= p.chanceBp) < 0) ?? (PRIZES[0] as RoulettePrize);
        w.freeBalance += prize.points - ROULETTE_COST;
        w.usedToday += 1;
        return { spinId: `mock-spin-${++seq}`, prizeKey: prize.key, won: prize.points, freeBalance: w.freeBalance, usedToday: w.usedToday };
    }

    static async lottoStatus(owner: string): Promise<LottoStatus> {
        await mockDelay();
        if (mockFlags().has('game-fail')) throw MOCK_ERRORS.internal();
        const w = wallet(owner);
        return mockClone({
            setCost: LOTTO_SET_COST,
            bundleSize: LOTTO_BUNDLE_SIZE,
            bundleCost: LOTTO_BUNDLE_COST,
            freeBalance: w.freeBalance,
            storageLimit: LOTTO_STORAGE_LIMIT,
            draws: w.draws,
            checkLink: CHECK_LINK,
        });
    }

    static async draw(owner: string, body: LottoDrawRequest): Promise<LottoDrawResult> {
        await mockDelay();
        validate(body);
        const w = wallet(owner);
        const cost = body.sets === 1 ? LOTTO_SET_COST : LOTTO_BUNDLE_COST;
        if (w.draws.length + body.sets > LOTTO_STORAGE_LIMIT) throw conflict('LOTTO_STORAGE_FULL', `내 번호는 ${LOTTO_STORAGE_LIMIT}세트까지 남길 수 있어요. 지난 번호를 지운 뒤 뽑아 주세요.`);
        if (w.freeBalance < cost) throw conflict('POINT_INSUFFICIENT', '적립 포인트가 모자라요.');
        // 한 번에 뽑은 세트끼리는 겹치지 않게 다시 뽑는다(조건이 빡빡해 겹칠 수밖에 없으면 20번 만에 그대로 둔다)
        const now = new Date().toISOString();
        const seen = new Set<string>();
        const draws: LottoDraw[] = [];
        for (let i = 0; i < body.sets; i++) {
            let numbers = pickNumbers(body.fixed, body.excluded);
            for (let tries = 0; seen.has(numbers.join(',')) && tries < 20; tries++) numbers = pickNumbers(body.fixed, body.excluded);
            seen.add(numbers.join(','));
            draws.push({ id: `mock-lotto-${++seq}`, numbers, drawnAt: now });
        }
        w.freeBalance -= cost;
        w.draws = [...draws, ...w.draws];
        return mockClone({ draws, freeBalance: w.freeBalance });
    }

    static async removeDraw(owner: string, id: string): Promise<void> {
        await mockDelay();
        const w = wallet(owner);
        // 이미 없는 줄을 지우는 것은 성공으로 본다(두 번 누름·다른 탭)
        w.draws = w.draws.filter((d) => d.id !== id);
    }
}
