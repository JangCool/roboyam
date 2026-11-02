// MOCK: 사업자 서비스별 정지·해제(2026-11-02 사용자 결정 — 신원은 그대로, HOTDEAL·AD 중 하나만)의 메모리 목업 — `useBusinessApi.ts` 가 부른다.
// 서버 후보: `POST /api/admin/businesses/{id}/services/{service}/suspend`(본문 = 신원 정지 `BusinessSuspendRequest`, 권한 `business.manage`) ·
//           `POST …/services/{service}/resume`(본문 = 신원 해제 `BusinessUnsuspendRequest`, 권한 `business.unsuspend`) → `BusinessDetail`.
//           상세(`GET /api/admin/businesses/{id}`)의 `serviceCards[]` 에 `suspendCode`·`suspendText`·`suspendedAt`·`suspendedByName`·`impact`(이 서비스 건만) 칸.
// 서버가 붙으면 `business/api/mock/` 와 함께 지운다(`useBusinessApi.ts` 의 `// MOCK:` 줄 → `// REAL:` 줄). 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   svc-suspend        켜기 — 상세의 서비스 카드에 영향 건수(`impact`)를 붙여 [서비스 정지]·[서비스 해제] 단추가 보이게 한다
//   svc-suspended      svc-suspend + 처음부터 핫딜 서비스만 정지된 상태(신원이 승인이고 핫딜이 이용 중일 때)
//   svc-suspend-fail   svc-suspend + 서비스 정지·해제 제출이 500
//   svc-suspend-changed  svc-suspend + 서비스 정지·해제 제출이 409 `BUSINESS_STATE_CHANGED`
//   스위치가 하나도 없으면 상세는 서버 응답 그대로다(서비스 단추도 안 보인다 — 실제 동작 그대로).
//
// ── 목업 규칙(가정) ─
//   · 서버 상세(실제 API)를 먼저 읽고, 그 위에 이 목업이 기억한 서비스 정지 상태를 덧씌운다. 신원이 승인이 아니면 덧씌우지 않는다(신원 정지가 우선).
//   · 영향 건수: 서비스 카드의 결제 대기(`pendingCount`) → 결제·입금 대기, 진행·예약(`liveCount`) → 진행 중 절반(올림) + 나머지 예약. 둘 다 0 이면 보기용 고정 값.
//   · 정지하면 그 서비스 카드만 `SUSPENDED` + 사유 칸, 처리 기록에 `SERVICE_SUSPENDED`(메모에 건 처리 선택). 해제하면 `APPROVED` + `SERVICE_RESUMED`. 서버의 다른 값(신원·다른 서비스)은 그대로다.

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    BusinessDetail,
    BusinessHistory,
    BusinessService,
    BusinessServiceCard,
    BusinessSuspendImpact,
    BusinessSuspendRequest,
    BusinessUnsuspendRequest,
} from '~/composables/business/api/useBusinessApi.types';

interface ServiceSuspension {
    suspended: boolean;
    code: BusinessServiceCard['suspendCode'];
    text: string | null;
    at: string | null;
    by: string | null;
}

const MOCK_ACTOR = '운영자(목업)';
/** 사업자 id → 서비스 → 목업이 기억하는 서비스 정지 상태 */
const store = new Map<string, Map<BusinessService, ServiceSuspension>>();
/** 사업자 id → 목업이 더한 처리 기록 */
const history = new Map<string, BusinessHistory[]>();
/** 처음 상태(`svc-suspended`)를 이미 넣은 사업자 */
const seeded = new Set<string>();

function enabled(flags: Set<string>): boolean {
    return ['svc-suspend', 'svc-suspended', 'svc-suspend-fail', 'svc-suspend-changed'].some((f) => flags.has(f));
}

function serviceStore(id: string): Map<BusinessService, ServiceSuspension> {
    let found = store.get(id);
    if (!found) store.set(id, (found = new Map()));
    return found;
}

function impactOf(card: BusinessServiceCard): BusinessSuspendImpact {
    if (card.liveCount + card.pendingCount === 0) return card.service === 'HOTDEAL' ? { awaitingPayment: 1, scheduled: 2, running: 1 } : { awaitingPayment: 0, scheduled: 1, running: 2 };
    const running = Math.ceil(card.liveCount / 2);
    return { awaitingPayment: card.pendingCount, scheduled: card.liveCount - running, running };
}

function seed(detail: BusinessDetail): void {
    if (seeded.has(detail.id)) return;
    seeded.add(detail.id);
    const hotdeal = detail.serviceCards.find((c) => c.service === 'HOTDEAL');
    if (!hotdeal || hotdeal.status !== 'APPROVED') return;
    const at = new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString();
    serviceStore(detail.id).set('HOTDEAL', { suspended: true, code: 'POLICY_VIOLATION', text: '할인 전 가격을 부풀려 올린 핫딜이 반복돼 핫딜 서비스만 멈췄어요.', at, by: MOCK_ACTOR });
    history.set(detail.id, [{ at, action: 'SERVICE_SUSPENDED', actor: MOCK_ACTOR, note: '할인 전 가격을 부풀려 올린 핫딜이 반복돼 핫딜 서비스만 멈췄어요.', service: 'HOTDEAL' }]);
}

/** 상세에 서비스 정지 칸·영향 건수를 덧씌운다 — 스위치가 없으면 서버 응답 그대로 */
export function mockServiceOverlay(detail: BusinessDetail): BusinessDetail {
    const flags = mockFlags();
    if (!enabled(flags) || detail.status !== 'APPROVED') return detail;
    if (flags.has('svc-suspended')) seed(detail);
    const mine = store.get(detail.id);
    return {
        ...detail,
        serviceCards: detail.serviceCards.map((card) => {
            const state = mine?.get(card.service);
            const out: BusinessServiceCard = { ...card, impact: impactOf(card) };
            if (!state) return out;
            return state.suspended
                ? { ...out, status: 'SUSPENDED', suspendCode: state.code, suspendText: state.text, suspendedAt: state.at, suspendedByName: state.by }
                : { ...out, status: 'APPROVED', suspendCode: null, suspendText: null, suspendedAt: null, suspendedByName: null };
        }),
        history: [...detail.history, ...(history.get(detail.id) ?? [])],
    };
}

function failIfAsked(flags: Set<string>): void {
    if (flags.has('svc-suspend-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('svc-suspend-changed')) throw MOCK_ERRORS.conflict('BUSINESS_STATE_CHANGED', '이미 상태가 바뀌었어요. 다시 불러와 주세요.');
}

/** 서비스만 정지 — 신원이 승인이고 그 서비스가 이용 중일 때만 */
export async function mockSuspendService(load: () => Promise<BusinessDetail>, id: string, service: BusinessService, body: BusinessSuspendRequest): Promise<BusinessDetail> {
    await mockDelay();
    failIfAsked(mockFlags());
    const current = mockServiceOverlay(await load());
    const card = current.serviceCards.find((c) => c.service === service);
    if (current.status !== 'APPROVED' || card?.status !== 'APPROVED') throw MOCK_ERRORS.conflict('BUSINESS_STATE_CHANGED', '이미 상태가 바뀌었어요. 다시 불러와 주세요.');
    const at = new Date().toISOString();
    serviceStore(id).set(service, { suspended: true, code: body.code, text: body.text, at, by: MOCK_ACTOR });
    const note = `${body.text} (결제 대기 ${body.pendingAction} · 예약 ${body.scheduledAction} · 진행 중 ${body.runningAction})`;
    history.set(id, [...(history.get(id) ?? []), { at, action: 'SERVICE_SUSPENDED', actor: MOCK_ACTOR, note, service }]);
    return mockServiceOverlay(await load());
}

/** 서비스만 정지 해제 — 그 서비스가 「서비스만 정지」일 때만 */
export async function mockResumeService(load: () => Promise<BusinessDetail>, id: string, service: BusinessService, body: BusinessUnsuspendRequest): Promise<BusinessDetail> {
    await mockDelay();
    failIfAsked(mockFlags());
    const current = mockServiceOverlay(await load());
    const card = current.serviceCards.find((c) => c.service === service);
    if (current.status !== 'APPROVED' || card?.status !== 'SUSPENDED') throw MOCK_ERRORS.conflict('BUSINESS_STATE_CHANGED', '이미 상태가 바뀌었어요. 다시 불러와 주세요.');
    const at = new Date().toISOString();
    serviceStore(id).set(service, { suspended: false, code: null, text: null, at: null, by: null });
    const note = body.note ? `${body.restore} — ${body.note}` : body.restore;
    history.set(id, [...(history.get(id) ?? []), { at, action: 'SERVICE_RESUMED', actor: MOCK_ACTOR, note, service }]);
    return mockServiceOverlay(await load());
}
