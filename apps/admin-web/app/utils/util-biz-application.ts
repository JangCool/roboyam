import type { BizAmount, BizApplicationStatus, BizDealPlacement, BizDealPostStatus, BizDomain, BizPause, BizPeriod, BizTakedownCause } from '~/composables/bizapplication/api/useBizApplicationApi.types';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/** 입금 확인 입력 중인 값(화면 폼) */
export interface BizDepositDraft {
    amount: number | null;
    depositedOn: string;
    depositorName: string;
    mismatchReason: string;
}

const DAY_MS = 86_400_000;
const KST_OFFSET_MS = 9 * 3_600_000;

/**
 * 사업자 신청 건(광고·핫딜) 화면 규칙 — 입력만 보고 답하는 순수 함수.
 * 기한·파일 규칙은 가정(00-overview.md ★-6)이며 서버가 다시 판정한다. 요금(하루 단가·한도)은 서버 응답(`quote`)을 쓴다.
 */
export default class UtilBizApplication {
    /**
     * 강조 글자 — 받는 돈 주 색 굵게 · 돌려줄 돈 빨강 · 수수료·대기 주황 · 승인·진행 초록 · 일자 하늘색(광고 심사 가운데 칸 `BizAppReviewPanel` 과 같은 값, 6e8c560f).
     * 광고주 관리(`/ad-advertisers`)가 쓴다.
     */
    static readonly EMPH = {
        money: 'font-bold text-(--p-primary-color)',
        refund: 'font-bold text-red-600 dark:text-red-400',
        fee: 'font-semibold text-amber-700 dark:text-amber-400',
        ok: 'font-semibold text-green-700 dark:text-green-400',
        date: 'font-semibold text-sky-700 dark:text-sky-400',
    } as const;

    /** 부가세율 10% */
    static readonly VAT_RATE = 0.1;
    /** 온라인 결제 기한(설정값 가정) */
    static readonly PAYMENT_DEADLINE_HOURS = 72;
    /** 수동 결제 입금 기한 기본(설정값 가정) */
    static readonly DEPOSIT_DEADLINE_DAYS = 7;
    /** 계약서 사본 — PDF·JPG·PNG, 한 장 10MB */
    static readonly CONTRACT_ACCEPT = 'application/pdf,image/jpeg,image/png';
    static readonly CONTRACT_MAX_BYTES = 10 * 1024 * 1024;
    /** 사유 글 길이 */
    static readonly REASON = { min: 5, max: 200 } as const;
    /** 심사 점검표 — 다 체크해야 [승인] */
    static readonly CHECKLIST = ['spec', 'link', 'policy', 'period', 'price'] as const;
    /** 도메인별 심사 화면 주소·조회 권한·처리 권한(04-ia-review 1-2 — 메뉴 `util-admin-menu.ts` 와 서버 `AdminScreenRegistry` 와 같게) */
    static readonly DOMAIN_SCREENS: Readonly<Record<BizDomain, { path: string; read: string; manage: string }>> = {
        AD: { path: '/ad-reviews', read: 'ad.read', manage: 'ad.manage' },
        HOTDEAL: { path: '/hotdeal-reviews', read: 'hotdeal.read', manage: 'hotdeal.manage' },
    };
    /** 환불 승인·거절·수동 송금 표시 — 두 도메인 공통(1차, 04-ia-review Q2) */
    static readonly REFUND_PERMISSION = 'deal-order.refund';

    /** 기간의 날 수(반열림, 하루 미만은 하루로) */
    static days(period: BizPeriod): number {
        const ms = Date.parse(period.endAt) - Date.parse(period.startAt);
        return Math.max(1, Math.ceil(ms / DAY_MS));
    }

    static amountOf(supply: number): BizAmount {
        const s = Math.max(0, Math.round(supply));
        const vat = Math.floor(s * UtilBizApplication.VAT_RATE);
        return { supply: s, vat, total: s + vat };
    }

    /** 시작이 이미 지났나(F6 — 그대로는 승인 불가) */
    static started(period: BizPeriod, now = Date.now()): boolean {
        return Date.parse(period.startAt) <= now;
    }

    /** 같은 길이로 뒤로 옮긴 기간 — 시작은 다음 날 00:00(KST) */
    static shifted(period: BizPeriod, now = Date.now()): BizPeriod {
        const length = Date.parse(period.endAt) - Date.parse(period.startAt);
        const start = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(UtilBizApplication.kstDate(new Date(now).toISOString()), 1));
        return { startAt: start, endAt: new Date(Date.parse(start) + length).toISOString() };
    }

    /** 일할 환불 제안 — 남은 날 / 전체 날 × 결제 금액(원 단위 내림) */
    static prorate(paid: number, usedDays: number, totalDays: number): number {
        if (totalDays <= 0) return 0;
        return Math.floor((paid * Math.max(0, totalDays - usedDays)) / totalDays);
    }

    // ── 광고주 요청 일시 정지·중단(biz-pause-contract — 서버가 저장 때 다시 판정한다) ──

    /** 운영자 대신 처리 메모 길이(필수) */
    static readonly PAUSE_MEMO = { min: 1, max: 200 } as const;

    /** 광고주 요청(정지·중단) 까닭인가 — 이 건에는 긴급 내림 [되돌리기](연장·일할 보상)를 쓰지 않는다 */
    static isAdvertiserCause(cause: BizTakedownCause | null | undefined): boolean {
        return cause === 'ADVERTISER_PAUSE' || cause === 'ADVERTISER_STOP';
    }

    /** 내림 상태를 광고주 요청으로 가른다 — 정지 중 · 중단 · 그 밖(null — 긴급 내림·사업자 정지·내림 아님) */
    static pauseState(status: string, cause: BizTakedownCause | null | undefined): 'PAUSED' | 'STOPPED' | null {
        if (status !== 'TAKEN_DOWN') return null;
        if (cause === 'ADVERTISER_PAUSE') return 'PAUSED';
        if (cause === 'ADVERTISER_STOP') return 'STOPPED';
        return null;
    }

    /**
     * 목록·머리 Tag 로 보일 광고주 요청 상태 — 정지 중 · 중단 · 정지한 채 끝남(`PAUSE_ENDED` — 끝 시각이 지난 정지 건, 「환불 없이 종료」). 그 밖이면 null(기존 상태 Tag)
     */
    static pauseLabel(status: string, cause: BizTakedownCause | null | undefined, period: BizPeriod, now = Date.now()): 'PAUSED' | 'STOPPED' | 'PAUSE_ENDED' | null {
        const state = UtilBizApplication.pauseState(status, cause);
        if (state === 'PAUSED' && UtilBizApplication.ended(period, now)) return 'PAUSE_ENDED';
        return state;
    }

    /** 「정지 중」은 주황(다시 켤 수 있음), 「중단」은 빨강(되돌리기 없음), 「정지한 채 종료」는 회색 */
    static pauseStateSeverity(state: 'PAUSED' | 'STOPPED' | 'PAUSE_ENDED'): Severity {
        return state === 'PAUSED' ? 'warn' : state === 'STOPPED' ? 'danger' : 'secondary';
    }

    /** 끝 시각이 지났나 — 정지한 채 끝났으면 「환불 없이 종료」(서버가 상태를 바꾸든 아니든 화면이 같은 문구) */
    static ended(period: BizPeriod, now = Date.now()): boolean {
        return Date.parse(period.endAt) <= now;
    }

    /** 정지 횟수를 다 썼나 */
    static pauseLimitReached(pause: Pick<BizPause, 'used' | 'limit'>): boolean {
        return pause.used >= pause.limit;
    }

    /**
     * 광고주 요청 중단 환불 예상(서버 `BizPricing.stop` 과 같은 식 — biz-pause-contract 규칙 5, 법무 확정 전 가정):
     * 총 일수 = 기간의 한국 날짜 수, 쓴 날 = 시작 날짜부터 기준 시각의 한국 날짜까지(그날 포함), 일할 = floor(대상 × 남은 날 / 총 일수) →
     * `min(일할, 결제액 − 이미 승인한 환불)`(`cap` — 모르면 null 로 생략) → 중도 해지 수수료 = floor(일할 × 비율 / 100) → 예상액 = 일할 − 수수료.
     * 기준 시각 = 정지 중 전환이면 멈춘 시각, 아니면 중단(지금) 시각. 대상 = `pause.proratableTotal`(광고 결제액 · 핫딜 글은 노출 자리 몫 — 게시비 제외).
     * @param {number} feeRate - 그 건에 고정된 중도 해지 수수료율(% — `pause.feeRate`).
     * @param {number | null} cap - 돌려줄 수 있는 남은 금액(결제액 − 이미 승인한 환불). 모르면 null.
     */
    static stopRefund(
        total: number,
        period: BizPeriod,
        fromIso: string,
        feeRate: number,
        cap: number | null = null,
    ): { prorated: number; fee: number; amount: number; usedDays: number; totalDays: number } {
        const start = UtilBizApplication.kstDate(period.startAt);
        const end = UtilBizApplication.kstDate(new Date(Date.parse(period.endAt) - 1).toISOString());
        const totalDays = Math.max(1, UtilBizApplication.dayDiff(start, end) + 1);
        const left = Math.min(totalDays, Math.max(0, UtilBizApplication.dayDiff(UtilBizApplication.kstDate(fromIso), end)));
        const raw = total > 0 ? Math.floor((total * left) / totalDays) : 0;
        const prorated = Math.max(0, cap === null ? raw : Math.min(raw, cap));
        const fee = Math.floor((prorated * Math.max(0, feeRate)) / 100);
        return { prorated, fee, amount: prorated - fee, usedDays: totalDays - left, totalDays };
    }

    /** 두 달력 날짜(`YYYY-MM-DD`)의 날 차이(b − a) */
    static dayDiff(a: string, b: string): number {
        return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / DAY_MS);
    }

    /** 계약서 파일 문제 — 형식·크기 */
    static contractProblem(file: File): 'TYPE' | 'SIZE' | null {
        if (!UtilBizApplication.CONTRACT_ACCEPT.split(',').includes(file.type)) return 'TYPE';
        if (file.size > UtilBizApplication.CONTRACT_MAX_BYTES) return 'SIZE';
        return null;
    }

    static reasonProblem(text: string, min: number = UtilBizApplication.REASON.min): 'REQUIRED' | 'TOO_SHORT' | 'TOO_LONG' | null {
        const v = text.trim();
        if (!v) return 'REQUIRED';
        if (v.length < min) return 'TOO_SHORT';
        if (v.length > UtilBizApplication.REASON.max) return 'TOO_LONG';
        return null;
    }

    /**
     * 입금 확인 칸 문제 — 비면 해당 칸 키, 입금액이 계약 금액과 다른데 사유가 없으면 `mismatchReason`.
     * (서버 `BizDepositConfirm` 검사와 같은 규칙 — 다르면 저장은 되지만 [예약 확정] 전에 사유가 필요하다)
     */
    static depositProblems(draft: BizDepositDraft, contractTotal: number): Partial<Record<keyof BizDepositDraft, 'REQUIRED'>> {
        const out: Partial<Record<keyof BizDepositDraft, 'REQUIRED'>> = {};
        if (!draft.amount || draft.amount <= 0) out.amount = 'REQUIRED';
        if (!draft.depositedOn) out.depositedOn = 'REQUIRED';
        if (!draft.depositorName.trim()) out.depositorName = 'REQUIRED';
        if (draft.amount && draft.amount !== contractTotal && !draft.mismatchReason.trim()) out.mismatchReason = 'REQUIRED';
        return out;
    }

    static emptyDeposit(): BizDepositDraft {
        return { amount: null, depositedOn: '', depositorName: '', mismatchReason: '' };
    }

    static statusSeverity(status: BizApplicationStatus): Severity {
        switch (status) {
            case 'SUBMITTED':
                return 'info';
            case 'AWAITING_PAYMENT':
            case 'AWAITING_DEPOSIT':
                return 'warn';
            case 'SCHEDULED':
                return 'contrast';
            case 'RUNNING':
                return 'success';
            case 'TAKEN_DOWN':
            case 'REJECTED':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    /** 사업자 핫딜 게시 상태 색(2차) — 게시 전 · 게시됨 · 실패 · 숨김 */
    static postSeverity(status: BizDealPostStatus): Severity {
        if (status === 'POSTED') return 'success';
        if (status === 'FAILED') return 'danger';
        if (status === 'HIDDEN') return 'secondary';
        return 'contrast';
    }

    /** 노출 줄 상태 색 — 대기 · 노출 중 · 멈춤 · 끝남 */
    static placementSeverity(status: BizDealPlacement['status']): Severity {
        if (status === 'SHOWING') return 'success';
        if (status === 'STOPPED') return 'warn';
        if (status === 'WAITING') return 'contrast';
        return 'secondary';
    }

    /** 금액 글자(원, 단위 없이 자릿수만) */
    static won(amount: number, locale: string): string {
        return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'ko-KR').format(amount);
    }

    static fileSize(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
        return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    }

    /** 남은 시간(초) → 「2일 3시간」 같은 짧은 글자에 쓸 조각 */
    static remainParts(deadlineAt: string, now = Date.now()): { days: number; hours: number; minutes: number; overdue: boolean } {
        const diff = Date.parse(deadlineAt) - now;
        const abs = Math.abs(diff);
        return { days: Math.floor(abs / DAY_MS), hours: Math.floor((abs % DAY_MS) / 3_600_000), minutes: Math.floor((abs % 3_600_000) / 60_000), overdue: diff < 0 };
    }

    // ── 날짜(KST 달력 날짜 글자 `YYYY-MM-DD`) — 목업·달력이 같이 쓴다 ──

    static kstDate(iso: string): string {
        return new Date(Date.parse(iso) + KST_OFFSET_MS).toISOString().slice(0, 10);
    }

    static kstToday(now = Date.now()): string {
        return UtilBizApplication.kstDate(new Date(now).toISOString());
    }

    static kstMidnight(ymd: string): string {
        const [y, m, d] = ymd.split('-').map(Number);
        return new Date(Date.UTC(y!, m! - 1, d!) - KST_OFFSET_MS).toISOString();
    }

    static addDays(ymd: string, n: number): string {
        const [y, m, d] = ymd.split('-').map(Number);
        return new Date(Date.UTC(y!, m! - 1, d! + n)).toISOString().slice(0, 10);
    }

    /** 기간이 그날(KST 00:00~24:00)과 겹치나 */
    static overlapsDay(period: BizPeriod, ymd: string): boolean {
        const dayStart = Date.parse(UtilBizApplication.kstMidnight(ymd));
        return Date.parse(period.startAt) < dayStart + DAY_MS && Date.parse(period.endAt) > dayStart;
    }
}
