// MOCK: 신고 사건의 **핫딜 글 처리**(hotdeal-contract 「신고 사건 조치」 — `action: DEAL_END` + 사유 `DEAL_FALSE_PRICE`·`DEAL_HIDDEN_AD`) 목업.
// 신고 관리 화면 자체는 실제 서버(`/api/admin/reports…`)에 붙어 있다. 서버에 아직 없는 두 가지만 여기서 덧씌운다 — 서버가 붙으면 이 파일과 `useReportApi` 의 `// MOCK:` 줄을 지운다.
//   1. 사건 줄의 `deal`(대상이 핫딜 글인지 + 딜 상태) — 지금 서버는 주지 않는다.
//   2. `POST /api/admin/reports/{id}/resolve { action: 'DEAL_END', reasonCode, note }` — 지금 서버는 모르는 조치라 400 이 난다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   report-deal   모든 글(POST) 사건을 핫딜 글(진행 중)로 본다 → 조치 사유에 핫딜 사유 2개, [핫딜 종료] 단추, D4 안내 확인
//   (스위치가 없어도 핫딜 사유 신고가 있는 글 사건은 핫딜 글로 본다 — `UtilReport.isDealCase`)
//
// ── 목업 가정 ─
//   · [핫딜 종료]를 누르면 실제 서버의 사건은 그대로 열려 있다. 대신 이 메모리에 「목업으로 닫음」을 적어 두고 목록·상세 응답에 덧씌운다(새로고침하면 사라진다).
//   · 같은 글이 핫딜 목업(`hotdeal/api/mock`)에 있으면 그 딜도 운영진 종료로 바꾼다.

import { ApiError } from '~/composables/common/useApiClient';
import { mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockHotdealEndFromReport } from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { ReportCaseDetail, ReportCaseItem, ReportCaseListView, ReportResolveRequest } from '~/composables/report/api/useReportApi.types';
import UtilReport from '~/utils/util-report';

interface MockClosed {
    closedAt: string;
    reasonCode: ReportResolveRequest['reasonCode'];
    note: string;
}

/** 목업 [핫딜 종료]로 닫은 사건 — 사건 ID → 처리 */
const closed = new Map<string, MockClosed>();
const ME = { id: 'mock-me', name: '관리자(나)' };

function decorateCase(item: ReportCaseItem): ReportCaseItem {
    const done = closed.get(item.id);
    let next = item;
    if (item.deal === undefined && item.targetType === 'POST' && mockFlags().has('report-deal')) next = { ...next, deal: { view: 'ACTIVE' } };
    if (done) {
        next = {
            ...next,
            status: 'RESOLVED',
            action: 'DEAL_END',
            reasonCode: done.reasonCode,
            note: done.note,
            closedAt: done.closedAt,
            closedBy: ME,
            reviewer: next.reviewer ?? ME,
            deal: { view: 'ENDED' },
        };
    }
    return next;
}

/** 목록 응답에 덧씌운다 */
export function mockReportDecorateList(view: ReportCaseListView): ReportCaseListView {
    return { ...view, items: view.items.map(decorateCase) };
}

/** 상세 응답에 덧씌운다 — 목업으로 닫은 사건이면 처리 기록 한 줄도 더한다 */
export function mockReportDecorateDetail(detail: ReportCaseDetail): ReportCaseDetail {
    const done = closed.get(detail.case.id);
    const events = done
        ? [
              ...(detail.events ?? []),
              { id: `mock-deal-end-${detail.case.id}`, type: 'RESOLVED' as const, actor: { kind: 'OPERATOR' as const, id: ME.id, name: ME.name }, detail: { action: 'DEAL_END', reasonCode: done.reasonCode, note: done.note }, createdAt: done.closedAt },
          ]
        : detail.events;
    return { ...detail, case: decorateCase(detail.case), events };
}

/**
 * `POST /{id}/resolve { action: 'DEAL_END' }` 목업 — 서버와 같은 검사(열린 사건 · 핫딜 글 · 사유 코드 · 글 1~200자)를 하고, 지금 상세를 받아 「처리됨」으로 덧씌워 돌려준다.
 * @param {() => Promise<ReportCaseDetail>} fetchDetail - 실제 서버의 지금 상세.
 */
export async function mockReportDealEnd(id: string, body: ReportResolveRequest, fetchDetail: () => Promise<ReportCaseDetail>): Promise<ReportCaseDetail> {
    await mockDelay();
    const current = mockReportDecorateDetail(await fetchDetail());
    const c = current.case;
    if (!UtilReport.isOpen(c.status)) throw new ApiError({ status: 409, code: 'REPORT_CASE_CLOSED', message: '이미 처리된 사건이에요.' });
    if (!UtilReport.isDealCase(c)) throw new ApiError({ status: 409, code: 'REPORT_ACTION_INVALID', message: '핫딜 글 사건에서만 핫딜을 종료할 수 있어요.' });
    const fields: Record<string, string> = {};
    if (!UtilReport.reasonsFor('POST', true).includes(body.reasonCode)) fields.reasonCode = '이 사건에 쓸 수 없는 사유예요.';
    const n = UtilReport.noteLength(body.note);
    const max = UtilReport.noteMax('DEAL_END');
    if (n === 0) fields.note = '처리 글을 적어 주세요.';
    else if (n > max) fields.note = `처리 글은 ${max}자까지 쓸 수 있어요.`;
    if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields });
    closed.set(id, { closedAt: new Date().toISOString(), reasonCode: body.reasonCode, note: body.note.trim() });
    mockHotdealEndFromReport(c.target.postId, body.note.trim());
    return mockReportDecorateDetail(current);
}
