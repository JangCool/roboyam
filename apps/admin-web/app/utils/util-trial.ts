import type { AdminTrialApplication, AdminTrialDetail, AdminTrialFormOptions, AdminTrialParticipant, AdminTrialRequest, TrialAction, TrialPhoto, TrialPhotoKind, TrialSettings, TrialStatus } from '~/composables/trial/api/useTrialApi.types';

/** 서랍 칸 문제 — 칸 이름 → 문제 코드(문구는 화면이 `trial.problem.<코드>` 로) */
export type TrialFormProblems = Partial<Record<keyof AdminTrialRequest, 'required' | 'range' | 'giveLimit' | 'order'>>;

/**
 * 체험단 관리 화면의 순수 규칙(trial-group-contract — 관리자). 판정은 서버다(DB CHECK `ck_trials_*`·`ck_trial_settings_values` 와 같은 범위로 미리 알려 주기만).
 */
export default class UtilTrial {
    static readonly PATHS = {
        list: '/ads/trials',
        review: '/ads/trials/review',
        settings: '/ads/trials/settings',
    } as const;

    /** 칸 범위(서버 V241 CHECK) */
    static readonly LIMITS = {
        titleMax: 100,
        productNameMax: 100,
        descriptionMax: 5000,
        recruitMax: 1000,
        hoursMax: 168,
        daysMax: 90,
        textMax: 1000,
        disclosureMax: 300,
    } as const;

    static readonly STATUSES = ['DRAFT', 'RECRUITING', 'SELECTING', 'RUNNING', 'CLOSED', 'CANCELLED'] as const;
    static readonly VERDICTS = ['NORMAL_WEAR', 'COURIER', 'MEMBER_FAULT', 'INTENTIONAL'] as const;

    static detailPath(id: string): string {
        return `${UtilTrial.PATHS.list}/${encodeURIComponent(id)}`;
    }

    /** 리뷰어 몫·일반 몫 인원(반올림 — 서버 `TrialSelection` 과 같다고 가정) */
    static split(recruitCount: number, reviewerShare: number): { reviewer: number; general: number } {
        const reviewer = Math.round((recruitCount * reviewerShare) / 100);
        return { reviewer, general: Math.max(0, recruitCount - reviewer) };
    }

    /** 고른 신청자의 몫별 수 */
    static countPicked(apps: readonly AdminTrialApplication[], picked: ReadonlySet<string>): { reviewer: number; general: number } {
        let reviewer = 0;
        let general = 0;
        for (const a of apps) {
            if (!picked.has(a.id)) continue;
            if (a.quota === 'REVIEWER') reviewer += 1;
            else general += 1;
        }
        return { reviewer, general };
    }

    /** 새 모집 처음 값 — 신청 시작 내일 10시, 7일 모집, 리뷰어 몫 60%, 금 48·은 24시간, 사용기 기한은 설정값 */
    static emptyForm(options: AdminTrialFormOptions | null, now = Date.now()): AdminTrialRequest {
        const start = new Date(now + 86_400_000);
        start.setHours(10, 0, 0, 0);
        const end = new Date(start.getTime() + 7 * 86_400_000);
        return {
            advertiserId: options?.advertisers[0]?.id ?? '',
            title: '',
            productName: '',
            productPrice: 0,
            form: 'GIVE',
            reviewBoardId: options?.reviewBoards[0]?.id ?? '',
            recruitCount: 5,
            reviewerShare: 60,
            reviewerGoldHours: 48,
            reviewerSilverHours: 24,
            applyStartAt: start.toISOString(),
            applyEndAt: end.toISOString(),
            reviewDays: options?.defaultReviewDays ?? 14,
            returnDays: null,
            shipper: 'ADVERTISER',
            description: '',
            imageKey: null,
        };
    }

    /** 상세 → 서랍 값 */
    static formOf(d: AdminTrialDetail): AdminTrialRequest {
        return {
            advertiserId: d.advertiser.id,
            title: d.title,
            productName: d.productName,
            productPrice: d.productPrice,
            form: d.form,
            reviewBoardId: d.reviewBoard.id,
            recruitCount: d.recruitCount,
            reviewerShare: d.reviewerShare,
            reviewerGoldHours: d.reviewerGoldHours,
            reviewerSilverHours: d.reviewerSilverHours,
            applyStartAt: d.applyStartAt,
            applyEndAt: d.applyEndAt,
            reviewDays: d.reviewDays,
            returnDays: d.returnDays,
            shipper: d.shipper,
            description: d.description,
            imageKey: d.imageKey,
        };
    }

    /** 제공형을 고를 수 있나 — 제품값이 설정 상한 이하(넘으면 대여형으로만) */
    static giveAllowed(productPrice: number, givePriceLimit: number): boolean {
        return productPrice <= givePriceLimit;
    }

    /** 서랍 화면 검사(서버 CHECK 와 같은 범위) */
    static problems(f: AdminTrialRequest, givePriceLimit: number): TrialFormProblems {
        const out: TrialFormProblems = {};
        const L = UtilTrial.LIMITS;
        if (!f.advertiserId) out.advertiserId = 'required';
        if (!f.title.trim()) out.title = 'required';
        else if (f.title.trim().length > L.titleMax) out.title = 'range';
        if (!f.productName.trim()) out.productName = 'required';
        else if (f.productName.trim().length > L.productNameMax) out.productName = 'range';
        if (!Number.isInteger(f.productPrice) || f.productPrice < 0) out.productPrice = 'range';
        if (f.form === 'GIVE' && !UtilTrial.giveAllowed(f.productPrice, givePriceLimit)) out.form = 'giveLimit';
        if (!f.reviewBoardId) out.reviewBoardId = 'required';
        if (!(f.recruitCount >= 1 && f.recruitCount <= L.recruitMax)) out.recruitCount = 'range';
        if (!(f.reviewerShare >= 0 && f.reviewerShare <= 100)) out.reviewerShare = 'range';
        if (!(f.reviewerSilverHours >= 0 && f.reviewerSilverHours <= L.hoursMax)) out.reviewerSilverHours = 'range';
        if (!(f.reviewerGoldHours >= f.reviewerSilverHours && f.reviewerGoldHours <= L.hoursMax)) out.reviewerGoldHours = 'order';
        if (!f.applyStartAt) out.applyStartAt = 'required';
        if (!f.applyEndAt) out.applyEndAt = 'required';
        else if (f.applyStartAt && Date.parse(f.applyEndAt) <= Date.parse(f.applyStartAt)) out.applyEndAt = 'order';
        if (!(f.reviewDays >= 1 && f.reviewDays <= L.daysMax)) out.reviewDays = 'range';
        if (f.form === 'LEND' && !(f.returnDays !== null && f.returnDays >= 1 && f.returnDays <= L.daysMax)) out.returnDays = 'range';
        if (f.description.length > L.descriptionMax) out.description = 'range';
        return out;
    }

    /** 서버로 보낼 값 — 앞뒤 공백 · 제공형이면 반납 기한 null */
    static toRequest(f: AdminTrialRequest): AdminTrialRequest {
        const { action: _action, ...fields } = f;
        return { ...fields, title: f.title.trim(), productName: f.productName.trim(), description: f.description.trim(), returnDays: f.form === 'LEND' ? f.returnDays : null };
    }

    /** 설정 화면 검사(서버 `ck_trial_settings_values`) — 문제 칸 이름 목록 */
    static settingsProblems(s: TrialSettings): (keyof TrialSettings)[] {
        const out: (keyof TrialSettings)[] = [];
        const between = (v: number, min: number, max: number) => Number.isInteger(v) && v >= min && v <= max;
        if (!between(s.givePriceLimit, 0, 100_000_000)) out.push('givePriceLimit');
        if (!s.disclosureText.trim() || s.disclosureText.trim().length > UtilTrial.LIMITS.disclosureMax) out.push('disclosureText');
        if (!s.sponsorDisclosureText.trim() || s.sponsorDisclosureText.trim().length > UtilTrial.LIMITS.disclosureMax) out.push('sponsorDisclosureText');
        if (!between(s.returnRemindFirstDays, 0, 60)) out.push('returnRemindFirstDays');
        if (!(s.returnRemindSecondDays > s.returnRemindFirstDays)) out.push('returnRemindSecondDays');
        if (!(s.unreturnedDays > s.returnRemindSecondDays && s.unreturnedDays <= 120)) out.push('unreturnedDays');
        if (!between(s.reviewDays, 1, 90)) out.push('reviewDays');
        if (!between(s.lateBanDays, 1, 3650)) out.push('lateBanDays');
        if (!between(s.lateRevokeStrikes, 1, 10)) out.push('lateRevokeStrikes');
        if (!between(s.proofBonusPoints, 0, 10000)) out.push('proofBonusPoints');
        if (!between(s.proofPhotoDays, 1, 365)) out.push('proofPhotoDays');
        if (!between(s.stockKeepDays, 1, 365)) out.push('stockKeepDays');
        return out;
    }

    /** 표시 문구 미리보기 — `{광고주}`·`{제공/대여}` 자리표시를 채운다 */
    static disclosurePreview(text: string, advertiser: string, lend: boolean, give: string, loan: string): string {
        return text.replaceAll('{광고주}', advertiser).replaceAll('{제공/대여}', lend ? loan : give);
    }

    /** 고치기에서 할 수 있는 상태 바꾸기(서버 `TrialService.transition`) — 준비 → 모집 열기 · 모집 중 → 신청 마감 · 진행 중 → 끝내기 · 뽑기 전 → 취소 */
    static actionsFor(status: TrialStatus, selected: boolean): TrialAction[] {
        const out: TrialAction[] = [];
        if (status === 'DRAFT') out.push('PUBLISH');
        if (status === 'RECRUITING') out.push('CLOSE_APPLY');
        if (status === 'RUNNING') out.push('CLOSE');
        if (!selected && (status === 'DRAFT' || status === 'RECRUITING' || status === 'SELECTING')) out.push('CANCEL');
        return out;
    }

    /** 그 줄의 한 종류 사진 */
    static photosOf(p: Pick<AdminTrialParticipant, 'photos'>, kind: TrialPhotoKind): TrialPhoto[] {
        return p.photos.filter((x) => x.kind === kind);
    }

    /** 이미지 파일 검사 */
    static photoProblem(file: File): 'type' | 'size' | null {
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return 'type';
        if (file.size > 10 * 1024 * 1024) return 'size';
        return null;
    }
}
