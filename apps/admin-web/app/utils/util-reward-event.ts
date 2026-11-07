import type {
    RewardEventBulkConfig,
    RewardEventCandidateSource,
    RewardEventCatalog,
    RewardEventCommentDrawConfig,
    RewardEventDetail,
    RewardEventEditScope,
    RewardEventFirstComeConfig,
    RewardEventMode,
    RewardEventSaveRequest,
    RewardEventStatus,
    RewardKind,
    RewardLine,
    RewardLineResultStatus,
    RewardRecipientStatus,
    RewardRunKind,
    RewardTier,
} from '~/composables/rewardevent/api/useRewardEventAdminApi.types';

/**
 * 보상 이벤트 관리 화면의 순수 규칙 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 판정: shared `RewardEventEditService`(칸 검사 `validate*` · 고치기 범위 `lockedChange`) · 한도 `RewardEventRules`(아래 숫자와 같게 둔다).
 */

/** 만들기·고치기 화면의 입력 상태 — 방식마다 설정을 다 들고 있다가(방식을 바꿔도 안 지워진다) 보낼 때 고른 방식 것만 싣는다 */
export interface RewardEventFormModel {
    name: string;
    memo: string;
    startAt: string;
    endAt: string;
    noticeUrl: string;
    mode: RewardEventMode;
    excludeOperators: boolean;
    firstCome: RewardEventFirstComeConfig;
    selectionSources: RewardEventCandidateSource[];
    commentDraw: RewardEventCommentDrawConfig;
    /** 댓글 기간 = 이벤트 기간(기본 켬) */
    commentSameAsPeriod: boolean;
    bulk: RewardEventBulkConfig;
    /** 등수 — 당첨자 선정이 아니면 첫 등수만 쓴다 */
    tiers: RewardTier[];
}

/** 고칠 수 있는가를 묻는 칸 묶음 */
export type RewardEventFormField = 'name' | 'memo' | 'noticeUrl' | 'startAt' | 'endAt' | 'mode' | 'config' | 'limit' | 'quota' | 'tiers' | 'rewards';

export type RewardEventProblem = 'REQUIRED' | 'TOO_LONG' | 'OUT_OF_RANGE' | 'END_BEFORE_START' | 'URL_FORMAT' | 'SOURCE_REQUIRED' | 'NOT_SELECTABLE';

const EDITABLE: Record<RewardEventEditScope, readonly RewardEventFormField[]> = {
    FULL: ['name', 'memo', 'noticeUrl', 'startAt', 'endAt', 'mode', 'config', 'limit', 'quota', 'tiers', 'rewards'],
    ACTIVE: ['name', 'memo', 'noticeUrl', 'endAt', 'limit', 'quota'],
    CLOSED: ['memo', 'noticeUrl', 'quota'],
    MEMO: ['memo'],
};

let tempSeq = 0;

export default class UtilRewardEvent {
    static readonly NAME_MAX = 60;
    static readonly MEMO_MAX = 500;
    static readonly TIER_NAME_MAX = 20;
    static readonly MANUAL_NAME_MAX = 40;
    static readonly MANUAL_METHOD_MAX = 200;
    static readonly REASON_MIN = 5;
    static readonly REASON_MAX = 200;
    static readonly DELIVERED_MEMO_MAX = 200;
    static readonly POINT_MAX = 100_000;
    static readonly LIMIT_MAX = 100_000;
    static readonly QUANTITY_MAX = 10;
    /** 등수 수(서버 `TIERS_MAX`) · 등수마다 보상 줄 수(서버 `LINES_MAX`) */
    static readonly TIER_MAX = 20;
    static readonly REWARD_MAX = 10;
    static readonly ACTIVE_DAYS_MAX = 365;
    /** 붙여 넣기·당첨자 넣기·회수 한 번에 받는 수(서버 `BATCH_MAX`) */
    static readonly PASTE_MAX = 500;
    static readonly BATCH_MAX = 500;

    /** 화면 안에서만 쓰는 임시 번호 — 서버가 저장할 때 새 번호를 준다 */
    static tempId(prefix: string): string {
        tempSeq += 1;
        return `new-${prefix}-${tempSeq}`;
    }

    static statusSeverity(status: RewardEventStatus): 'secondary' | 'success' | 'warn' | 'info' | 'danger' {
        if (status === 'ACTIVE') return 'success';
        if (status === 'CLOSED') return 'warn';
        if (status === 'DONE') return 'info';
        if (status === 'CANCELED') return 'danger';
        return 'secondary';
    }

    static recipientSeverity(status: RewardRecipientStatus): 'secondary' | 'success' | 'warn' | 'info' | 'danger' {
        if (status === 'GRANTED') return 'success';
        if (status === 'PARTIAL') return 'warn';
        if (status === 'FAILED') return 'danger';
        if (status === 'REVOKED') return 'info';
        return 'secondary';
    }

    static lineSeverity(status: RewardLineResultStatus): 'secondary' | 'success' | 'warn' | 'info' | 'danger' {
        if (status === 'SUCCESS' || status === 'DELIVERED') return 'success';
        if (status === 'FAILED') return 'danger';
        if (status === 'DELIVERY_PENDING') return 'warn';
        if (status === 'REVOKED') return 'info';
        return 'secondary';
    }

    static runSeverity(kind: RewardRunKind): 'secondary' | 'success' | 'warn' | 'info' {
        if (kind === 'GRANT') return 'success';
        if (kind === 'RETRY') return 'warn';
        if (kind === 'REVOKE') return 'info';
        return 'secondary';
    }

    static kindIcon(kind: RewardKind): string {
        if (kind === 'POINT') return 'pi pi-star';
        if (kind === 'SHOP_ITEM') return 'pi pi-shopping-bag';
        if (kind === 'EMOTICON_PACK') return 'pi pi-face-smile';
        return 'pi pi-send';
    }

    /** 이 범위에서 이 칸을 고칠 수 있는가 */
    static editable(scope: RewardEventEditScope, field: RewardEventFormField): boolean {
        return EDITABLE[scope].includes(field);
    }

    static emptyLine(kind: RewardKind): RewardLine {
        return {
            id: UtilRewardEvent.tempId('line'),
            kind,
            points: kind === 'POINT' ? 1000 : null,
            itemId: null,
            itemName: null,
            itemKind: null,
            itemUsage: null,
            itemDurationDays: null,
            quantity: kind === 'SHOP_ITEM' ? 1 : null,
            packId: null,
            packName: null,
            manualName: kind === 'MANUAL' ? '' : null,
            manualMethod: kind === 'MANUAL' ? '' : null,
        };
    }

    static emptyTier(name: string, quota: number): RewardTier {
        return { id: UtilRewardEvent.tempId('tier'), name, quota, rewards: [UtilRewardEvent.emptyLine('POINT')] };
    }

    /**
     * 새 보상 이벤트의 처음 값 — 시작은 다음 정각, 끝은 14일 뒤 23:59(표시 시간대는 고르개가 맡는다 — 여기서는 순간만 만든다).
     * @param {number} now - 지금(ms).
     * @param {string} firstTierName - 첫 등수 이름(번역된 「1등」).
     */
    static emptyForm(now: number, firstTierName: string): RewardEventFormModel {
        const start = new Date(Math.ceil(now / 3_600_000) * 3_600_000);
        const end = new Date(start.getTime() + 14 * 86_400_000);
        return {
            name: '',
            memo: '',
            startAt: start.toISOString(),
            endAt: end.toISOString(),
            noticeUrl: '',
            mode: 'SELECTION',
            excludeOperators: true,
            firstCome: { trigger: 'IDENTITY_VERIFIED', limit: 1000 },
            selectionSources: ['MANUAL'],
            commentDraw: { postRef: '', postId: null, postTitle: null, from: start.toISOString(), to: end.toISOString(), excludeAuthor: true },
            commentSameAsPeriod: true,
            bulk: { scope: 'ALL', minGradeLevel: null, joinedFrom: null, joinedTo: null, identityVerifiedOnly: false, activeWithinDays: null },
            tiers: [UtilRewardEvent.emptyTier(firstTierName, 10)],
        };
    }

    static fromDetail(d: RewardEventDetail): RewardEventFormModel {
        const cd = d.selection?.commentDraw ?? null;
        return {
            name: d.name,
            memo: d.memo ?? '',
            startAt: d.startAt,
            endAt: d.endAt,
            noticeUrl: d.noticeUrl ?? '',
            mode: d.mode,
            excludeOperators: d.excludeOperators,
            firstCome: d.firstCome ? { ...d.firstCome } : { trigger: 'IDENTITY_VERIFIED', limit: 1000 },
            selectionSources: d.selection ? [...d.selection.sources] : ['MANUAL'],
            commentDraw: cd ? { ...cd } : { postRef: '', postId: null, postTitle: null, from: d.startAt, to: d.endAt, excludeAuthor: true },
            // 서버 시각 글자는 소수 자리가 다를 수 있다 — 순간으로 견준다
            commentSameAsPeriod: cd ? Date.parse(cd.from) === Date.parse(d.startAt) && Date.parse(cd.to) === Date.parse(d.endAt) : true,
            bulk: d.bulk ? { ...d.bulk } : { scope: 'ALL', minGradeLevel: null, joinedFrom: null, joinedTo: null, identityVerifiedOnly: false, activeWithinDays: null },
            tiers: d.tiers.map((t) => ({ ...t, rewards: t.rewards.map((l) => ({ ...l })) })),
        };
    }

    /** 보낼 본문 — 고른 방식의 설정만, 당첨자 선정이 아니면 첫 등수만(평범한 객체로 새로 만든다) */
    static toRequest(f: RewardEventFormModel): RewardEventSaveRequest {
        const tiers = (f.mode === 'SELECTION' ? f.tiers : f.tiers.slice(0, 1)).map((t) => ({
            id: t.id,
            name: t.name.trim(),
            quota: f.mode === 'FIRST_COME' ? f.firstCome.limit : f.mode === 'BULK' ? 0 : t.quota,
            rewards: t.rewards.map((l) => ({ ...l, manualName: l.manualName?.trim() ?? null, manualMethod: l.manualMethod?.trim() ?? null })),
        }));
        const comment = f.mode === 'SELECTION' && f.selectionSources.includes('COMMENT_DRAW');
        return {
            name: f.name.trim(),
            memo: f.memo.trim(),
            startAt: f.startAt,
            endAt: f.endAt,
            noticeUrl: f.noticeUrl.trim(),
            mode: f.mode,
            firstCome: f.mode === 'FIRST_COME' ? { ...f.firstCome } : null,
            selection:
                f.mode === 'SELECTION'
                    ? {
                          sources: [...f.selectionSources],
                          commentDraw: comment ? { ...f.commentDraw, postRef: f.commentDraw.postRef.trim(), from: f.commentSameAsPeriod ? f.startAt : f.commentDraw.from, to: f.commentSameAsPeriod ? f.endAt : f.commentDraw.to } : null,
                      }
                    : null,
            bulk: f.mode === 'BULK' ? { ...f.bulk } : null,
            excludeOperators: f.excludeOperators,
            tiers,
        };
    }

    /** 등수 하나의 1인당 보상 — 포인트 합 · 아이템 수(상점 아이템 수량 + 팩) · 직접 전달 수 */
    static perPerson(tier: Pick<RewardTier, 'rewards'>): { points: number; items: number; manual: number } {
        return tier.rewards.reduce(
            (acc, l) => ({
                points: acc.points + (l.kind === 'POINT' ? (l.points ?? 0) : 0),
                items: acc.items + (l.kind === 'SHOP_ITEM' ? (l.quantity ?? 1) : l.kind === 'EMOTICON_PACK' ? 1 : 0),
                manual: acc.manual + (l.kind === 'MANUAL' ? 1 : 0),
            }),
            { points: 0, items: 0, manual: 0 },
        );
    }

    /**
     * 보상 합계 미리 보기 — 인원 × 1인당. 일괄은 대상 수를 모르면(`bulkCount` null) 총합도 null.
     * @returns 1인당 가장 큰 포인트, 총 포인트·아이템·직접 전달(모르면 null).
     */
    static totals(req: RewardEventSaveRequest, bulkCount: number | null): { maxPerPerson: number; people: number | null; points: number | null; items: number | null; manual: number | null } {
        let maxPerPerson = 0;
        let people = 0;
        let points = 0;
        let items = 0;
        let manual = 0;
        for (const t of req.tiers) {
            const per = UtilRewardEvent.perPerson(t);
            maxPerPerson = Math.max(maxPerPerson, per.points);
            const n = req.mode === 'BULK' ? (bulkCount ?? 0) : t.quota;
            people += n;
            points += per.points * n;
            items += per.items * n;
            manual += per.manual * n;
        }
        if (req.mode === 'BULK' && bulkCount === null) return { maxPerPerson, people: null, points: null, items: null, manual: null };
        return { maxPerPerson, people, points, items, manual };
    }

    /**
     * 화면 검사 — 칸 경로(서버 `fields` 와 같은 꼴) → 문제. 막는 것은 서버다.
     * @param {RewardEventSaveRequest} req - 보낼 본문.
     * @param {RewardEventCatalog | null} catalog - 고르기 목록(팩 선택 가능 여부).
     */
    static validate(req: RewardEventSaveRequest, catalog: RewardEventCatalog | null): Record<string, RewardEventProblem> {
        const out: Record<string, RewardEventProblem> = {};
        if (!req.name) out.name = 'REQUIRED';
        else if (req.name.length > UtilRewardEvent.NAME_MAX) out.name = 'TOO_LONG';
        if (req.memo.length > UtilRewardEvent.MEMO_MAX) out.memo = 'TOO_LONG';
        if (!req.startAt) out.startAt = 'REQUIRED';
        if (!req.endAt) out.endAt = 'REQUIRED';
        else if (req.startAt && Date.parse(req.endAt) <= Date.parse(req.startAt)) out.endAt = 'END_BEFORE_START';
        if (req.noticeUrl && !/^(https?:\/\/|\/)/.test(req.noticeUrl)) out.noticeUrl = 'URL_FORMAT';
        if (req.mode === 'FIRST_COME') {
            const n = req.firstCome?.limit ?? 0;
            if (!Number.isInteger(n) || n < 1 || n > UtilRewardEvent.LIMIT_MAX) out['firstCome.limit'] = 'OUT_OF_RANGE';
        }
        if (req.mode === 'SELECTION') {
            if (!req.selection?.sources.length) out['selection.sources'] = 'SOURCE_REQUIRED';
            if (req.selection?.commentDraw) {
                const c = req.selection.commentDraw;
                if (!c.postRef) out['selection.commentDraw.postRef'] = 'REQUIRED';
                if (c.from && c.to && Date.parse(c.to) <= Date.parse(c.from)) out['selection.commentDraw.to'] = 'END_BEFORE_START';
            }
        }
        if (req.mode === 'BULK' && req.bulk?.scope === 'CONDITION') {
            const d = req.bulk.activeWithinDays;
            if (d !== null && (!Number.isInteger(d) || d < 1 || d > UtilRewardEvent.ACTIVE_DAYS_MAX)) out['bulk.activeWithinDays'] = 'OUT_OF_RANGE';
            if (req.bulk.joinedFrom && req.bulk.joinedTo && req.bulk.joinedTo < req.bulk.joinedFrom) out['bulk.joinedTo'] = 'END_BEFORE_START';
        }
        req.tiers.forEach((t, i) => {
            if (!t.name) out[`tiers[${i}].name`] = 'REQUIRED';
            else if (t.name.length > UtilRewardEvent.TIER_NAME_MAX) out[`tiers[${i}].name`] = 'TOO_LONG';
            if (req.mode === 'SELECTION' && (!Number.isInteger(t.quota) || t.quota < 1 || t.quota > UtilRewardEvent.LIMIT_MAX)) out[`tiers[${i}].quota`] = 'OUT_OF_RANGE';
            if (!t.rewards.length) out[`tiers[${i}].rewards`] = 'REQUIRED';
            t.rewards.forEach((l, j) => {
                const p = `tiers[${i}].rewards[${j}]`;
                if (l.kind === 'POINT' && (!Number.isInteger(l.points) || (l.points ?? 0) < 1 || (l.points ?? 0) > UtilRewardEvent.POINT_MAX)) out[`${p}.points`] = 'OUT_OF_RANGE';
                if (l.kind === 'SHOP_ITEM') {
                    if (!l.itemId) out[`${p}.itemId`] = 'REQUIRED';
                    if (l.itemUsage === 'CONSUMABLE' && (!Number.isInteger(l.quantity) || (l.quantity ?? 0) < 1 || (l.quantity ?? 0) > UtilRewardEvent.QUANTITY_MAX)) out[`${p}.quantity`] = 'OUT_OF_RANGE';
                }
                if (l.kind === 'EMOTICON_PACK') {
                    if (!l.packId) out[`${p}.packId`] = 'REQUIRED';
                    else if (catalog && catalog.packs.find((x) => x.id === l.packId)?.selectable === false) out[`${p}.packId`] = 'NOT_SELECTABLE';
                }
                if (l.kind === 'MANUAL') {
                    if (!l.manualName) out[`${p}.manualName`] = 'REQUIRED';
                    else if (l.manualName.length > UtilRewardEvent.MANUAL_NAME_MAX) out[`${p}.manualName`] = 'TOO_LONG';
                    if ((l.manualMethod?.length ?? 0) > UtilRewardEvent.MANUAL_METHOD_MAX) out[`${p}.manualMethod`] = 'TOO_LONG';
                }
            });
        });
        return out;
    }

    /** 붙여 넣은 글 → 줄(빈 줄·앞뒤 공백 빼고, 쉼표·탭으로 나눈 것도 줄로) */
    static lines(text: string): string[] {
        return [
            ...new Set(
                text
                    .split(/[\n,\t]/)
                    .map((s) => s.trim())
                    .filter(Boolean),
            ),
        ];
    }

    /** 지급 실행 멱등 키 — 대화상자를 열 때 하나 만들어 같은 확인 안에서는 같은 키를 보낸다 */
    static idempotencyKey(): string {
        return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }
}
