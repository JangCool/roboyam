import type { AdBookingRow, AdBookingStatus, AdDaySlot, AdPlacementsRequest, AdPostInlinePosition, AdPostInlineType, AdTakedown, AdType, AdWingScreen } from '~/composables/ad/api/useAdApi.types';
import { BIZ_AD_TYPES } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilAdSense from '~/utils/util-adsense';
import UtilBizApplication from '~/utils/util-biz-application';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/**
 * 광고 일정표 화면 규칙 — 입력만 보고 답하는 순수 함수(서버 `AdOperatorService` 가 저장 때 다시 판정한다).
 */
export default class UtilAd {
    static readonly TYPES: readonly AdType[] = BIZ_AD_TYPES;

    /** 긴급 내림 뒤 다른 서버 사본이 비워지기까지(초) — 아이로빗 20초 사본 */
    static readonly CACHE_SECONDS = 20;

    /** 운영자 등록이 되는 유형 — 광고 종류(배너 넷·날개 배너·한 줄). 핫딜 노출 자리는 글이 있어야 해서 서버가 400 `fields.type`(서버 `BizProduct.adProduct()`) */
    static readonly MANUAL_TYPES: readonly AdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'WING_BANNER', 'TEXT_LINE'];

    /** 운영자 등록 서랍이 칸 아래에 붙이는 서버 오류 키(`fields.*`) — 나머지는 서랍 위쪽에 모은다 */
    static readonly MANUAL_FIELDS: readonly string[] = ['advertiserId', 'period', 'weight', 'supply', 'creative.imageFileId', 'creative.linkUrl', 'creative.altText', 'creative.text', 'memo', 'contractFileIds'];

    static isManualType(type: AdType): boolean {
        return UtilAd.MANUAL_TYPES.includes(type);
    }

    /** 「광고 심사에서 열기」 — 건의 도메인 심사 화면(AD → `/ad-reviews`, HOTDEAL → `/hotdeal-reviews`) */
    static reviewLink(row: Pick<AdBookingRow, 'id' | 'domain'>): { path: string; query: { id: string } } {
        return { path: UtilBizApplication.DOMAIN_SCREENS[row.domain].path, query: { id: row.id } };
    }

    /**
     * 상태 글자 키 — 광고주 요청 내림은 「정지 중」(`ADVERTISER_PAUSE`)·「중단」(`ADVERTISER_STOP`)으로 긴급 내림 「내림」과 가른다(biz-pause-contract)
     */
    static statusKey(row: Pick<AdBookingRow, 'status' | 'takedownCause' | 'period'>, now = Date.now()): string {
        const state = UtilBizApplication.pauseLabel(row.status, row.takedownCause, row.period, now);
        return state ? `bizApplication.pauseState.${state}` : `ad.status.${row.status}`;
    }

    /** 상태 Tag 색 — 정지 중 주황 · 중단 빨강 · 그 밖은 `statusSeverity` */
    static rowSeverity(row: Pick<AdBookingRow, 'status' | 'takedownCause' | 'period'>, now = Date.now()): Severity {
        const state = UtilBizApplication.pauseLabel(row.status, row.takedownCause, row.period, now);
        return state ? UtilBizApplication.pauseStateSeverity(state) : UtilAd.statusSeverity(row.status);
    }

    /** 되돌리기 — 긴급 내림만(정지 내림은 사업자 정지 해제가, 광고주 요청 정지는 광고주의 [다시 켜기]가 되돌린다. 광고주 요청 건에는 연장·일할 보상이 없다) */
    static canRestore(status: AdBookingStatus, takedown: AdTakedown | null): boolean {
        return status === 'TAKEN_DOWN' && takedown?.cause === 'EMERGENCY';
    }

    static isBanner(type: AdType): boolean {
        return type === 'WIDE_BANNER' || type === 'STRIP_BANNER' || type === 'SQUARE_BANNER' || type === 'SMALL_BANNER' || type === 'WING_BANNER';
    }

    /** 배너 규격(가로×세로 px) — user-web `business.adTypeSize` 와 같은 값 */
    static readonly BANNER_SIZE: Readonly<Partial<Record<AdType, readonly [number, number]>>> = {
        WIDE_BANNER: [1200, 300],
        STRIP_BANNER: [1200, 100],
        SQUARE_BANNER: [300, 250],
        SMALL_BANNER: [300, 100],
        WING_BANNER: [160, 600],
    };

    /** 배너 비율 허용 폭 ±3%(docs/plans/biz-ad-hotdeal/02-ad.md 미디어 업로드) — 공개 자리는 규격 틀에 맞춰 잘라 보인다 */
    static readonly BANNER_RATIO_TOLERANCE = 0.03;

    /**
     * 배너 그림이 그 유형의 규격 비율과 맞는지(±3%).
     * @param {AdType} type 광고 유형.
     * @param {number} width 그림 가로(px).
     * @param {number} height 그림 세로(px).
     * @returns {boolean} 맞거나 규격이 없는 유형(한 줄 등)이면 true.
     */
    static bannerRatioOk(type: AdType, width: number, height: number): boolean {
        const size = UtilAd.BANNER_SIZE[type];
        if (!size || width <= 0 || height <= 0) return true;
        return Math.abs(width / height / (size[0] / size[1]) - 1) <= UtilAd.BANNER_RATIO_TOLERANCE + 1e-9; // 경계(딱 3%)는 부동소수 오차 없이 통과
    }

    static statusSeverity(status: AdBookingStatus): Severity {
        switch (status) {
            case 'RUNNING':
                return 'success';
            case 'SCHEDULED':
                return 'contrast';
            case 'SUBMITTED':
            case 'AWAITING_PAYMENT':
            case 'AWAITING_DEPOSIT':
                return 'warn';
            case 'TAKEN_DOWN':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    /** 날짜 점 색 — 0 = 꽉 참(빨강), 1 = 거의(주황), 그 밖 = 여유(초록) */
    static dotClass(slot: AdDaySlot | undefined): string {
        if (!slot) return 'bg-transparent';
        if (slot.remaining <= 0) return 'bg-red-500';
        if (slot.remaining === 1 || slot.remaining / slot.limit <= 0.25) return 'bg-amber-500';
        return 'bg-emerald-500';
    }

    /** `YYYY-MM` — 달력이 보는 첫 달 */
    static monthOf(year: number, monthIndex: number): string {
        return `${year}-${String(monthIndex + 1).padStart(2, '0')}`;
    }

    static ymd(year: number, monthIndex: number, day: number): string {
        return `${year}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }

    /** 긴급 내림 가능(진행·예약) */
    static canTakedown(status: AdBookingStatus): boolean {
        return status === 'RUNNING' || status === 'SCHEDULED';
    }

    /* ── 광고 자리 설정(`/ads/placements`) — 서버 `PUT /api/admin/ads/placements` 와 같은 값 ── */

    static readonly INLINE_TYPES: readonly AdPostInlineType[] = ['TEXT_LINE', 'STRIP_BANNER', 'SQUARE_BANNER'];
    static readonly INLINE_POSITIONS: readonly AdPostInlinePosition[] = ['BETWEEN_CARDS', 'AFTER_BODY'];
    static readonly WING_SCREENS: readonly AdWingScreen[] = ['HOME', 'PAGE', 'BOARD', 'POST', 'FEED', 'GROUP_BUY', 'GROUPS'];
    /** 게시판 목록 한 줄 — 일반 글 몇 번째 뒤(서버 400 `fields.boardList.after`) */
    static readonly FEED_AFTER = { min: 1, max: 20 } as const;
    /** 게시판 목록 한 쪽 광고 수 · 두 광고 사이 글 수(서버 V155 — 400 `fields.boardList.count|gap`) */
    static readonly FEED_COUNT = { min: 1, max: 2 } as const;
    static readonly FEED_GAP = { min: 3, max: 20, def: 10 } as const;
    /** 중도 해지 수수료율(%) — 서버 `AdPlacementStore`·`ad_placement_settings.stop_fee_rate`(0~30 정수, 기본 10). 미리보기 예시의 남은 일할 금액 */
    static readonly STOP_FEE_RATE = { min: 0, max: 30, def: 10, example: 100_000 } as const;
    /** 칸 아래·묶음 위에 붙이는 서버 오류 키(`fields.*`) — 나머지는 저장 줄에 모은다 */
    static readonly PLACEMENT_FIELDS: readonly string[] = [
        'postInline',
        'postInline.enabled',
        'postInline.adType',
        'postInline.position',
        'wings',
        'wings.left',
        'wings.right',
        'wings.screens',
        'boardList',
        'boardList.enabled',
        'boardList.after',
        'boardList.count',
        'boardList.gap',
        'adult',
        'adult.enabled',
        'refund',
        'refund.stopFeeRate',
        // 구글 애드센스(shared V176)
        'adsense',
        'adsense.mode',
        'adsense.client',
        'adsense.adtest',
        'adsense.units.WIDE_BANNER',
        'adsense.units.STRIP_BANNER',
        'adsense.units.SQUARE_BANNER',
        'adsense.units.SMALL_BANNER',
        'adsense.units.WING_BANNER',
        'adsense.units.POST_INLINE',
        'adsense.units.BOARD_LIST',
        'adsense.units.BOARD_LIST.slot',
        'adsense.units.BOARD_LIST.layoutKey',
    ];

    /* ── 광고 자리 미리보기(공개 화면 짜임을 단순하게 그린 도식) — 값은 user-web `UtilAd`·`AdWings` 와 같다 ── */

    /** 날개 폭 · 본문과 날개 사이 · 날개와 창 끝 사이(px) — user-web `UtilAd.WING`. 한쪽 바깥 여백이 셋의 합(192px) 이상일 때만 그린다 */
    static readonly WING = { width: 160, gap: 16, edge: 16 } as const;
    /** 공개 셸의 본문 폭(`--content-max`, px) — 기본·네이버식 1100 · 다음식·아이로빗 1168 */
    static readonly PUBLIC_CONTENT = { base: 1100, wide: 1168 } as const;

    /** 날개가 보이기 시작하는 창 너비(px) — 본문 폭 + 양쪽 (날개 + 간격 + 끝 여백) */
    static wingMinViewport(content: number): number {
        return content + 2 * (UtilAd.WING.width + UtilAd.WING.gap + UtilAd.WING.edge);
    }

    /** 목록 한 줄 광고가 붙는 일반 글 차례(1부터, 그 줄 뒤) — user-web `UtilAd.feedIndex` 와 같다: 글이 적으면 맨 끝, 일반 글이 없으면 null */
    static feedSlot(rows: number, after: number): number | null {
        if (rows <= 0) return null;
        return Math.min(rows, after);
    }

    /**
     * 미리보기 목록 줄 — 짧으면 다 두고, 길면 앞 두 줄 · 광고 앞뒤 줄 · 마지막 줄만 두고 사이는 「⋯ N줄」로 접는다.
     * `slot` 은 광고가 붙는 일반 글 차례(없으면 null).
     */
    static previewFeedRows(rows: number, slot: number | null, keepAll = 8): ({ kind: 'row'; n: number } | { kind: 'gap'; count: number })[] {
        const out: ({ kind: 'row'; n: number } | { kind: 'gap'; count: number })[] = [];
        for (let n = 1; n <= rows; n++) {
            const keep = rows <= keepAll || n <= 2 || n === rows || (slot !== null && Math.abs(n - slot) <= 1);
            if (keep) {
                out.push({ kind: 'row', n });
                continue;
            }
            const last = out[out.length - 1];
            if (last?.kind === 'gap') last.count++;
            else out.push({ kind: 'gap', count: 1 });
        }
        return out;
    }

    /** 편집용 사본(서버 응답을 그대로 고치지 않게) — 날개 화면은 정해진 차례로. `adult` 가 없는 응답(옛 서버)은 끔으로, `adsense` 가 없는 응답은 애드센스 끔으로 읽는다 */
    static placementsForm(view: AdPlacementsRequest): AdPlacementsRequest {
        return {
            postInline: { ...view.postInline },
            wings: { left: view.wings.left, right: view.wings.right, screens: UtilAd.WING_SCREENS.filter((s) => view.wings.screens.includes(s)) },
            boardList: { ...view.boardList },
            adult: { enabled: view.adult?.enabled === true },
            refund: { stopFeeRate: view.refund?.stopFeeRate ?? UtilAd.STOP_FEE_RATE.def },
            adsense: UtilAdSense.form(view.adsense),
        };
    }

    /** 보낼 본문 — 편집용 사본에 애드센스 칸 다듬기(빈 글자 → null, `pub-…` → `ca-pub-…`)를 더한 새 객체(반응형 객체가 아니다) */
    static placementsBody(form: AdPlacementsRequest): AdPlacementsRequest {
        const copy = UtilAd.placementsForm(form);
        return { ...copy, adsense: UtilAdSense.body(copy.adsense) };
    }

    /**
     * 중도 해지 수수료 예시(서버 `BizPricing.stop` 의 수수료 식 — floor(일할 × 비율 / 100), 예상액 = 일할 − 수수료). 설정 탭 미리보기용.
     * @returns 수수료·환불 예상액.
     */
    static stopFeeExample(prorated: number, rate: number): { fee: number; amount: number } {
        const fee = Math.floor((prorated * Math.max(0, rate)) / 100);
        return { fee, amount: prorated - fee };
    }

    /** 묶음마다 저장된 값과 다른 칸 수 */
    static placementChanges(form: AdPlacementsRequest, saved: AdPlacementsRequest): { postInline: number; wings: number; boardList: number; adult: number; refund: number; adsense: number } {
        const screens = (a: readonly AdWingScreen[]) => UtilAd.WING_SCREENS.filter((s) => a.includes(s)).join(',');
        return {
            postInline: [form.postInline.enabled !== saved.postInline.enabled, form.postInline.adType !== saved.postInline.adType, form.postInline.position !== saved.postInline.position].filter(Boolean).length,
            wings: [form.wings.left !== saved.wings.left, form.wings.right !== saved.wings.right, screens(form.wings.screens) !== screens(saved.wings.screens)].filter(Boolean).length,
            boardList: [
                form.boardList.enabled !== saved.boardList.enabled,
                form.boardList.after !== saved.boardList.after,
                form.boardList.count !== saved.boardList.count,
                form.boardList.gap !== saved.boardList.gap,
            ].filter(Boolean).length,
            adult: [(form.adult?.enabled === true) !== (saved.adult?.enabled === true)].filter(Boolean).length,
            refund: [form.refund.stopFeeRate !== saved.refund.stopFeeRate].filter(Boolean).length,
            adsense: UtilAdSense.changes(form.adsense, saved.adsense),
        };
    }
}
