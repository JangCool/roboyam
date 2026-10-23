// MOCK: 회원 이벤트 API(`/api/admin/member-events`, 계약 `docs/features/message-template-contract.md` 「개정 3」 이벤트 화면)의 메모리 목업 — `useMemberEventApi.ts` 가 부른다.
// 서버가 붙으면 이 파일(과 `notification/api/mock/`)을 지운다. 바꾼 것은 메모리에만 남아 **새로고침하면 처음 상태로 돌아간다**.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   member-events-error       목록이 `500 INTERNAL` 로 실패
//   member-events-save-error  바꾸기가 `500` 으로 실패(그 줄이 되돌아가고 토스트)
//   member-events-denied      `notification.manage` 가 없는 것처럼 바꾸기가 `403`
//
// ── 흉내 ─
//   실제 이벤트 5종(`MemberNotificationType` — 이름·설명은 알림 템플릿 목업과 같은 것) + 「준비 중」 5종(게시판 기본 설정 「알림」에만 있는 종류 중 겹치지 않는 것).
//   이름 대응: 댓글 = `COMMENT_ON_POST`, 답글 = `REPLY_ON_COMMENT`(게시판 기본 설정의 「사이트 알림·이메일·회원이 끌 수 있음」 값을 처음 값으로 옮겼다고 가정).
//   포인트 회수·글 숨김·댓글 숨김은 계정 알림(코드가 끌 수 없게 잠금), 운영 처리·스텝 처리는 처리 알림(잠금). 이메일은 지금 회원 알림에 없어 모두 꺼진 채로 시작한다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MemberEventUpdateRequest, MemberEventView } from '~/composables/notification/api/useMemberEventApi.types';
import { mockMemberEventSeeds } from '~/composables/template/api/mock/notification-template-mock';

type Can = (code: string) => boolean;

interface Extra {
    recipientText: string;
    featureReady: boolean;
    enabled: boolean;
    inApp: boolean;
    memberCanTurnOff: boolean;
    lockReason: MemberEventView['lockReason'];
    boardSettingKind: string | null;
    sentLast30Days: number;
}

const READY_EXTRA: Record<string, Extra> = {
    COMMENT: { recipientText: '글쓴이(댓글 쓴 사람 본인 제외)', featureReady: true, enabled: true, inApp: true, memberCanTurnOff: true, lockReason: null, boardSettingKind: 'COMMENT_ON_POST', sentLast30Days: 1284 },
    REPLY: { recipientText: '댓글 쓴이(답글 쓴 사람 본인 제외)', featureReady: true, enabled: true, inApp: true, memberCanTurnOff: true, lockReason: null, boardSettingKind: 'REPLY_ON_COMMENT', sentLast30Days: 532 },
    POINTS_REVOKED: { recipientText: '포인트가 회수된 회원', featureReady: true, enabled: true, inApp: true, memberCanTurnOff: false, lockReason: 'ACCOUNT', boardSettingKind: null, sentLast30Days: 7 },
    POST_HIDDEN: { recipientText: '글이 숨겨진 글쓴이', featureReady: true, enabled: true, inApp: true, memberCanTurnOff: false, lockReason: 'ACCOUNT', boardSettingKind: null, sentLast30Days: 12 },
    COMMENT_HIDDEN: { recipientText: '댓글이 숨겨진 댓글 쓴이', featureReady: true, enabled: true, inApp: true, memberCanTurnOff: false, lockReason: 'ACCOUNT', boardSettingKind: null, sentLast30Days: 21 },
};

/** 준비 중 — 게시판 기본 설정 「알림」에만 있는 종류(이름은 서버가 요청 언어로 줄 글이라 한국어) */
const PLANNED: Omit<MemberEventView, 'enabled' | 'inApp' | 'email' | 'sentLast30Days'>[] = [
    { code: 'MENTION', area: 'content', name: '멘션', description: '글·댓글에서 닉네임으로 불리면 알려요.', recipientText: '불린 회원', featureReady: false, memberCanTurnOff: true, lockReason: null, boardSettingKind: 'MENTION' },
    { code: 'POST_POPULAR', area: 'content', name: '내 글이 인기글이 됨', description: '내 글이 인기글이 되면 알려요.', recipientText: '글쓴이', featureReady: false, memberCanTurnOff: true, lockReason: null, boardSettingKind: 'POST_POPULAR' },
    { code: 'OPERATOR_ACTION', area: 'safety', name: '운영 처리', description: '운영자가 내 글·댓글을 숨기거나 지우거나 되살리면 사유와 함께 알려요.', recipientText: '처리된 글·댓글의 쓴이', featureReady: false, memberCanTurnOff: false, lockReason: 'ACTION', boardSettingKind: 'OPERATOR_ACTION' },
    { code: 'STAFF_ACTION', area: 'safety', name: '스텝·매니저 처리', description: '게시판 스텝·매니저가 내 글·댓글을 처리하면 알려요.', recipientText: '처리된 글·댓글의 쓴이', featureReady: false, memberCanTurnOff: false, lockReason: 'ACTION', boardSettingKind: 'STAFF_ACTION' },
    { code: 'REPORT_RESULT', area: 'safety', name: '신고 처리 결과', description: '내가 한 신고가 처리되면 결과를 알려요.', recipientText: '신고한 회원', featureReady: false, memberCanTurnOff: true, lockReason: null, boardSettingKind: 'REPORT_RESULT' },
];

let rows: MemberEventView[] | null = null;

function state(): MemberEventView[] {
    if (rows) return rows;
    const ready = mockMemberEventSeeds().map((seed): MemberEventView => {
        const extra = READY_EXTRA[seed.code] as Extra;
        return { ...seed, ...extra, email: false };
    });
    const planned = PLANNED.map((item): MemberEventView => ({ ...item, enabled: false, inApp: true, email: false, sentLast30Days: 0 }));
    rows = [...ready, ...planned];
    return rows;
}

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

export async function mockMemberEvents(can: Can): Promise<MemberEventView[]> {
    await mockDelay();
    if (!can('notification.read')) throw MOCK_ERRORS.denied();
    if (mockFlags().has('member-events-error')) throw MOCK_ERRORS.internal();
    return clone(state());
}

export async function mockUpdateMemberEvent(can: Can, code: string, body: MemberEventUpdateRequest): Promise<MemberEventView> {
    await mockDelay();
    const flags = mockFlags();
    if (!can('notification.manage') || flags.has('member-events-denied')) throw MOCK_ERRORS.denied();
    if (flags.has('member-events-save-error')) throw MOCK_ERRORS.internal();
    const row = state().find((item) => item.code === code);
    if (!row) throw new ApiError({ status: 404, code: 'MEMBER_EVENT_NOT_FOUND', message: '회원 이벤트를 찾을 수 없어요.' });
    if (!row.featureReady) throw new ApiError({ status: 409, code: 'MEMBER_EVENT_NOT_READY', message: '아직 일어나지 않는 이벤트라 바꿀 수 없어요.' });
    if (row.lockReason && body.memberCanTurnOff) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력값을 확인해 주세요.', fields: { memberCanTurnOff: '이 이벤트는 회원이 끌 수 없어요.' } });
    Object.assign(row, { enabled: body.enabled, inApp: body.inApp, email: body.email, memberCanTurnOff: row.lockReason ? false : body.memberCanTurnOff });
    return clone(row);
}
