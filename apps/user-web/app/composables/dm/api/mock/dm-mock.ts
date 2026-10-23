// MOCK: 쪽지 API 의 메모리 목업 — `useDmApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
// 차단 관계는 차단 목업(`block-mock.ts`)의 상태를, 상대 회원은 가짜 회원 명부(`common/mock/member-mock.ts`)를 읽는다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   dm-empty            쪽지함이 비어 있다(처음 상태)            dm-inbox-fail     쪽지함 읽기 실패(500)
//   dm-room-fail        대화방 읽기 실패(500)                     dm-too-new        가입 7일 전(보내기 막힘 · 진행 막대)
//   dm-daily-limit      오늘 20통을 이미 씀                        dm-partner-limit  오늘 새 상대 5명을 이미 채움
//   dm-send-fail        보내기가 서버 오류(500 — 붉은 말풍선·다시 보내기)   dm-send-offline 보내기가 연결 끊김(0)
//   dm-recipients-fail  받는 사람 찾기 실패(500)                   dm-settings-fail  수신 설정 읽기 실패(500)
//   dm-settings-save-fail 수신 설정 저장 실패(500)                 dm-reply          보내면 6초 뒤 상대가 답장한다(대화방 주기 조회 확인용)
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 처음 쪽지함 6개: 안 읽음 2통(로봇덕후) · 답장 전 첫 쪽지(코딩하는곰 — 링크·연락처 막힘) · 탈퇴한 회원 · 쪽지를 받지 않는 회원(조용한관찰자) ·
//     차단한 회원(로봇팔장인 — 쪽지함에는 남고 보낼 수 없음) · 열흘 전 안 읽음 1통(휴머노이드).
//   · 한도: 하루 20통 · 새 상대 5명 · 연속 전송 3초 · 본문 500자 · 받는 사람 검색 2~16자·최대 10명. 오늘 센 수는 새로고침하면 0 으로 돌아간다.
//   · 보낼 자격(나 때문): 이메일 인증 전 → 정지 중(`restriction`) → 사업자 계정 → 가입 7일 전(`?mock=dm-too-new` — 로그인 정보에 가입일이 없다) → 오늘 한도.
//   · 상대가 나를 차단한 경우는 만들지 않았다(내가 차단한 회원만 `UNAVAILABLE`).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardFeedSource } from '~/composables/common/mock/board-feed-mock';
import { mockCursorStart } from '~/composables/common/mock/board-feed-mock';
import { mockDirectoryMember, mockMemberOf, mockSearchMembers, mockValidMemberId } from '~/composables/common/mock/member-mock';
import type { MockMember } from '~/composables/common/mock/member-mock';
import { mockIsBlocked, mockMemoOf } from '~/composables/block/api/mock/block-mock';
import type { AuthUser } from '~/composables/auth/api/useAuthApi.types';
import type {
    DmCanSend,
    DmMessage,
    DmPageQuery,
    DmPartner,
    DmQuota,
    DmReason,
    DmRecipientList,
    DmRoom,
    DmSendResult,
    DmSenderState,
    DmSettings,
    DmThreadItem,
    DmThreadList,
    DmUnreadCount,
    MyDm,
} from '~/composables/dm/api/useDmApi.types';
import UtilDm from '~/utils/util-dm';
import UtilDmContact from '~/utils/util-dm-contact';
import UtilDmRules from '~/utils/util-dm-rules';

/** 목업이 보는 나 — 로그인 정보와 정지 여부 */
export interface DmViewer {
    user: AuthUser | null;
    restricted: boolean;
}

const MIN = 60 * 1000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const RULES = { minAccountAgeDays: 7, bodyMax: 500, retentionDays: 365 };
const LIMITS = { daily: 20, newPartner: 5, intervalMs: 3000, recipients: 10, unreadCap: 100 };

interface ThreadRow {
    id: string;
    /** 상대 — 탈퇴한 회원은 null */
    partner: MockMember | null;
    /** 오래된 것이 앞 */
    messages: DmMessage[];
    /** 내가 읽은 가장 최근 쪽지 ID */
    readUpTo: string | null;
    /** 내 쪽지함에서 지운 지점(그 쪽지까지 안 보인다) */
    clearedUpTo: string | null;
}

let threads: ThreadRow[] | null = null;
let acceptDm = true;
let sentToday = 0;
let newPartnersToday = 0;
let lastSentAt = 0;
let lastMessageMs = 0;
let threadSeq = 10;

/** 쪽지 ID — 서버처럼 시각 순서로 커지게 보낸 시각(밀리초)으로 만든다(같은 밀리초면 1씩 밀어 겹치지 않게) */
const messageId = (atMs: number = Date.now()) => {
    lastMessageMs = Math.max(atMs, lastMessageMs + 1);
    return `91${String(lastMessageMs).padStart(16, '0')}`;
};
const threadId = () => `92${String(++threadSeq).padStart(16, '0')}`;
const at = (msAgo: number) => new Date(Date.now() - msAgo).toISOString();

function seed(): ThreadRow[] {
    if (threads) return threads;
    const flags = mockFlags();
    if (flags.has('dm-daily-limit')) sentToday = LIMITS.daily;
    if (flags.has('dm-partner-limit')) newPartnersToday = LIMITS.newPartner;
    if (flags.has('dm-empty')) return (threads = []);
    // 처음 쪽지의 ID 는 보낸 시각으로 — 늦게 보낸 쪽지가 큰 ID(쪽지함 줄 세우기가 서버와 같게)
    const m = (mine: boolean, body: string, msAgo: number): DmMessage => ({ id: `91${String(Date.now() - msAgo).padStart(16, '0')}`, mine, body, createdAt: at(msAgo) });
    const t1 = [
        m(false, '안녕하세요! 지난번 로봇 팔 제어 글 잘 봤어요.', 2 * DAY + 3 * HOUR),
        m(true, '감사합니다 :) 어떤 부분이 궁금하셨어요?', 2 * DAY + 2 * HOUR),
        m(false, '서보 모터 고르는 기준이 궁금했어요.\n토크랑 속도 중에 뭘 먼저 봐야 할까요?', 2 * DAY + HOUR),
        m(true, '들어 올릴 무게부터 정하고 토크를 먼저 보세요. 자료는 https://example.com/servo-guide 에 정리해 뒀어요.', 2 * DAY),
        m(false, '오 정리 감사합니다!', 40 * MIN),
        m(false, '혹시 다음 주 모임에도 오시나요?', 12 * MIN),
    ];
    const t2 = [m(true, '안녕하세요, 코딩 에이전트 후기 글 보고 연락드려요. 혹시 쓰신 설정 공유해 주실 수 있을까요?', 5 * HOUR)];
    const t3 = [m(false, '중고 부품 거래 아직 가능하세요?', 20 * DAY), m(false, '답이 없으셔서 다른 분께 알아볼게요.', 19 * DAY)];
    const t4 = [m(false, '글 잘 읽었어요.', 6 * DAY), m(true, '감사합니다!', 6 * DAY - HOUR)];
    const t5 = [m(false, '이 링크 한번 들어가 보세요 ㅎㅎ', 4 * DAY)];
    const t6 = [m(false, '센서 비교표 만드신 거 혹시 원본 파일 있으세요?', 10 * DAY)];
    const member = (id: string) => mockDirectoryMember(id);
    const last = (list: DmMessage[], n: number) => list[list.length - 1 - n]?.id ?? null;
    threads = [
        { id: threadId(), partner: member('900000000000000101'), messages: t1, readUpTo: last(t1, 2), clearedUpTo: null },
        { id: threadId(), partner: member('900000000000000102'), messages: t2, readUpTo: last(t2, 0), clearedUpTo: null },
        { id: threadId(), partner: null, messages: t3, readUpTo: last(t3, 0), clearedUpTo: null },
        { id: threadId(), partner: member('900000000000000104'), messages: t4, readUpTo: last(t4, 0), clearedUpTo: null },
        { id: threadId(), partner: member('900000000000000105'), messages: t5, readUpTo: last(t5, 0), clearedUpTo: null },
        { id: threadId(), partner: member('900000000000000103'), messages: t6, readUpTo: null, clearedUpTo: null },
    ];
    return threads;
}

/* ── 읽기 도우미 ── */

function partnerView(member: MockMember | null): DmPartner {
    if (!member) return { id: null, nickname: '탈퇴한 회원', grade: 'SEEDLING', gradeLabel: '새싹', role: 'USER', withdrawn: true, memo: null };
    return { id: member.id, nickname: member.nickname, grade: member.grade, gradeLabel: member.gradeLabel, role: member.role, withdrawn: false, memo: mockMemoOf(member.id) };
}

/** 내 쪽지함에 보이는 쪽지(지운 지점 뒤) */
function visible(row: ThreadRow): DmMessage[] {
    const cut = row.clearedUpTo;
    return cut ? row.messages.filter((m) => UtilDm.compareId(m.id, cut) > 0) : row.messages;
}

function unreadOf(row: ThreadRow): number {
    const read = row.readUpTo;
    return visible(row).filter((m) => !m.mine && (!read || UtilDm.compareId(m.id, read) > 0)).length;
}

function quota(): DmQuota {
    // 다음 한국 시간 0시까지 — KST = UTC+9
    const kstNow = Date.now() + 9 * HOUR;
    const kstMidnight = Math.floor(kstNow / DAY) * DAY + DAY;
    return {
        dailyLimit: LIMITS.daily,
        sentToday,
        remainingToday: Math.max(0, LIMITS.daily - sentToday),
        newPartnerLimit: LIMITS.newPartner,
        newPartnersToday,
        remainingNewPartners: Math.max(0, LIMITS.newPartner - newPartnersToday),
        resetsInSeconds: Math.max(0, Math.round((kstMidnight - kstNow) / 1000)),
    };
}

function sender(viewer: DmViewer): DmSenderState {
    const user = viewer.user;
    let reason: DmSenderState['reason'] = null;
    let availableFrom: string | null = null;
    if (user && !user.emailVerified) reason = 'EMAIL_NOT_VERIFIED';
    else if (viewer.restricted) reason = 'ACCOUNT_RESTRICTED';
    else if (user?.role === 'BUSINESS') reason = 'BUSINESS_RESTRICTED';
    else if (mockFlags().has('dm-too-new')) {
        reason = 'TOO_NEW';
        availableFrom = new Date(Date.now() + 3 * DAY).toISOString();
    } else if (sentToday >= LIMITS.daily) reason = 'DAILY_LIMIT';
    return { canSend: reason === null, reason, availableFrom };
}

function threadWith(partnerId: string): ThreadRow | null {
    return seed().find((t) => t.partner?.id === partnerId) ?? null;
}

/** 이 상대에게 지금 보낼 수 있는가 — 서버와 같은 차례로 이유 하나 */
function reasonFor(viewer: DmViewer, partner: MockMember | null, existing: ThreadRow | null): { reason: DmReason | null; availableFrom: string | null } {
    if (partner && partner.id === viewer.user?.id) return { reason: 'SELF', availableFrom: null };
    const mine = sender(viewer);
    if (mine.reason && mine.reason !== 'DAILY_LIMIT') return { reason: mine.reason, availableFrom: mine.availableFrom };
    if (!partner) return { reason: 'RECIPIENT_WITHDRAWN', availableFrom: null };
    if (mockIsBlocked(partner.id)) return { reason: 'UNAVAILABLE', availableFrom: null };
    if (!partner.acceptDm) return { reason: 'RECIPIENT_REFUSES', availableFrom: null };
    if (sentToday >= LIMITS.daily) return { reason: 'DAILY_LIMIT', availableFrom: null };
    if (!existing && newPartnersToday >= LIMITS.newPartner) return { reason: 'NEW_PARTNER_LIMIT', availableFrom: null };
    return { reason: null, availableFrom: null };
}

const threadMissing = () => new ApiError({ status: 404, code: 'DM_THREAD_NOT_FOUND', message: '없는 대화이거나 내 대화가 아니에요.' });
const profileMissing = () => new ApiError({ status: 404, code: 'PROFILE_NOT_FOUND', message: '탈퇴했거나 없는 회원이에요.' });

/* ── 내 상태·배지·수신 설정 ── */

export async function mockMyDm(viewer: DmViewer): Promise<MyDm> {
    await mockDelay();
    const list = seed();
    const unreadThreads = Math.min(LIMITS.unreadCap, list.filter((t) => unreadOf(t) > 0).length);
    return mockClone({ acceptDm, sender: sender(viewer), quota: quota(), unreadThreads, rules: RULES });
}

export async function mockDmUnreadCount(): Promise<DmUnreadCount> {
    await mockDelay();
    return { count: Math.min(LIMITS.unreadCap, seed().filter((t) => unreadOf(t) > 0).length) };
}

export async function mockDmSettings(): Promise<DmSettings> {
    await mockDelay();
    if (mockFlags().has('dm-settings-fail')) throw MOCK_ERRORS.internal();
    return { acceptDm };
}

export async function mockUpdateDmSettings(value: boolean): Promise<DmSettings> {
    await mockDelay();
    if (mockFlags().has('dm-settings-save-fail')) throw MOCK_ERRORS.internal();
    if (typeof value !== 'boolean') throw MOCK_ERRORS.validation({ acceptDm: '받기·받지 않기 중 하나를 골라 주세요.' });
    acceptDm = value;
    return { acceptDm };
}

/* ── 쪽지함·대화방 ── */

export async function mockDmThreads(query: DmPageQuery): Promise<DmThreadList> {
    await mockDelay();
    if (mockFlags().has('dm-inbox-fail')) throw MOCK_ERRORS.internal();
    const rows: DmThreadItem[] = seed()
        .filter((t) => visible(t).length > 0)
        .map((t) => {
            const last = visible(t)[visible(t).length - 1]!;
            return { id: t.id, partner: partnerView(t.partner), last: { id: last.id, mine: last.mine, preview: UtilDm.preview(last.body), createdAt: last.createdAt }, unreadCount: unreadOf(t) };
        })
        .sort((a, b) => UtilDm.compareId(b.last.id, a.last.id));
    const start = mockCursorStart(query.cursor);
    const end = start + Math.min(50, Math.max(1, query.size ?? UtilDm.INBOX_PAGE_SIZE));
    const hasNext = end < rows.length;
    return mockClone({ items: rows.slice(start, end), hasNext, nextCursor: hasNext ? String(end) : null });
}

export async function mockDmRoom(viewer: DmViewer, id: string, query: DmPageQuery): Promise<DmRoom> {
    await mockDelay();
    if (mockFlags().has('dm-room-fail')) throw MOCK_ERRORS.internal();
    const row = seed().find((t) => t.id === id);
    if (!row) throw threadMissing();
    const { reason, availableFrom } = reasonFor(viewer, row.partner, row);
    const newestFirst = [...visible(row)].reverse();
    const start = mockCursorStart(query.cursor);
    const end = start + Math.min(50, Math.max(1, query.size ?? UtilDm.ROOM_PAGE_SIZE));
    const hasNext = end < newestFirst.length;
    return mockClone({
        thread: { id: row.id, partner: partnerView(row.partner), canSend: reason === null, reason, availableFrom, contactAllowed: row.messages.some((m) => !m.mine) },
        items: newestFirst.slice(start, end),
        hasNext,
        nextCursor: hasNext ? String(end) : null,
        quota: quota(),
    });
}

export async function mockDmMarkRead(id: string): Promise<void> {
    await mockDelay();
    const row = seed().find((t) => t.id === id);
    if (!row) throw threadMissing();
    row.readUpTo = row.messages[row.messages.length - 1]?.id ?? row.readUpTo;
}

export async function mockDmRemove(id: string): Promise<void> {
    await mockDelay();
    const row = seed().find((t) => t.id === id);
    if (!row) throw threadMissing();
    const last = row.messages[row.messages.length - 1]?.id ?? null;
    row.clearedUpTo = last;
    row.readUpTo = last;
}

/* ── 받는 사람·보내기 ── */

export async function mockDmRecipients(source: BoardFeedSource, viewer: DmViewer, raw: string): Promise<DmRecipientList> {
    await mockDelay();
    if (mockFlags().has('dm-recipients-fail')) throw MOCK_ERRORS.internal();
    const q = UtilDmRules.normalizeQuery(raw);
    const n = UtilDmRules.length(q);
    if (n < UtilDmRules.SEARCH_MIN || n > UtilDmRules.SEARCH_MAX) throw MOCK_ERRORS.validation({ q: `닉네임은 ${UtilDmRules.SEARCH_MIN}~${UtilDmRules.SEARCH_MAX}자로 검색해 주세요.` });
    const found = (await mockSearchMembers(source, q)).filter((m) => m.id !== viewer.user?.id && !mockIsBlocked(m.id)).slice(0, LIMITS.recipients);
    return mockClone({ items: found.map((m) => ({ user: partnerView(m), threadId: threadWith(m.id)?.id ?? null })) });
}

export async function mockDmCanSend(source: BoardFeedSource, viewer: DmViewer, userId: string): Promise<DmCanSend> {
    await mockDelay();
    const member = await mockMemberOf(source, userId);
    if (!member) throw profileMissing();
    const existing = threadWith(userId);
    const { reason, availableFrom } = reasonFor(viewer, member, existing);
    return mockClone({
        canSend: reason === null,
        reason,
        availableFrom,
        threadId: existing?.id ?? null,
        newPartner: existing === null,
        contactAllowed: Boolean(existing?.messages.some((m) => !m.mine)),
        quota: quota(),
    });
}

const REPLIES = ['답장 고마워요! 확인해 볼게요.', '좋아요, 그렇게 해요 :)', '자세히 알려 주셔서 감사합니다.'];

export async function mockDmSend(source: BoardFeedSource, viewer: DmViewer, userId: string, raw: string): Promise<DmSendResult> {
    await mockDelay();
    const flags = mockFlags();
    const body = UtilDmRules.normalize(raw);
    const problem = UtilDmRules.bodyProblem(body);
    if (problem === 'EMPTY') throw MOCK_ERRORS.validation({ body: '쪽지 내용을 입력해 주세요.' });
    if (problem === 'TOO_LONG') throw MOCK_ERRORS.validation({ body: `쪽지는 ${RULES.bodyMax}자까지 쓸 수 있어요.` });
    if (problem === 'INVALID_CHAR') throw MOCK_ERRORS.validation({ body: '쪽지에 쓸 수 없는 문자가 있어요.' });
    if (!mockValidMemberId(userId)) throw profileMissing();
    const member = await mockMemberOf(source, userId);
    if (!member) throw profileMissing();
    if (member.id === viewer.user?.id) throw new ApiError({ status: 400, code: 'DM_SELF', message: '나에게는 쪽지를 보낼 수 없어요.' });
    const mine = sender(viewer);
    if (mine.reason === 'EMAIL_NOT_VERIFIED') throw new ApiError({ status: 403, code: 'EMAIL_NOT_VERIFIED', message: '이메일 인증을 마친 뒤에 쪽지를 보낼 수 있어요.' });
    if (mine.reason === 'ACCOUNT_RESTRICTED') throw new ApiError({ status: 403, code: 'ACCOUNT_RESTRICTED', message: '이용이 정지된 동안에는 쪽지를 보낼 수 없어요.' });
    if (mine.reason === 'BUSINESS_RESTRICTED') throw new ApiError({ status: 403, code: 'BUSINESS_ACCOUNT_RESTRICTED', message: '사업자 계정은 쪽지를 보낼 수 없어요.' });
    if (mine.reason === 'TOO_NEW') throw new ApiError({ status: 403, code: 'DM_SENDER_TOO_NEW', message: `가입 후 ${RULES.minAccountAgeDays}일이 지나야 쪽지를 보낼 수 있어요.` });
    if (mockIsBlocked(member.id)) throw new ApiError({ status: 403, code: 'DM_UNAVAILABLE', message: '쪽지를 보낼 수 없는 사용자예요.' });
    if (!member.acceptDm) throw new ApiError({ status: 403, code: 'DM_RECIPIENT_REFUSES', message: '이 회원은 쪽지를 받지 않아요.' });
    const existing = threadWith(member.id);
    const contactAllowed = Boolean(existing?.messages.some((m) => !m.mine));
    if (!contactAllowed && UtilDmContact.has(body)) {
        throw new ApiError({ status: 400, code: 'DM_CONTACT_NOT_ALLOWED', message: '처음 보내는 쪽지에는 링크와 외부 연락처를 넣을 수 없어요. 상대가 답장한 뒤에는 보낼 수 있어요.' });
    }
    const wait = LIMITS.intervalMs - (Date.now() - lastSentAt);
    if (wait > 0) throw new ApiError({ status: 429, code: 'RATE_LIMITED', message: '너무 빠르게 보내고 있어요.', retryAfterSeconds: Math.ceil(wait / 1000) });
    const q = quota();
    if (q.remainingToday <= 0) throw new ApiError({ status: 429, code: 'DM_DAILY_LIMIT', message: `오늘 보낼 수 있는 쪽지 ${LIMITS.daily}통을 모두 썼어요.`, retryAfterSeconds: q.resetsInSeconds });
    if (!existing && q.remainingNewPartners <= 0) {
        throw new ApiError({ status: 429, code: 'DM_NEW_PARTNER_LIMIT', message: `오늘 새로 쪽지를 보낼 수 있는 상대 ${LIMITS.newPartner}명을 모두 채웠어요.`, retryAfterSeconds: q.resetsInSeconds });
    }
    // 서버가 판정하지 못한 실패 — 한도에 들어가지 않는다
    if (flags.has('dm-send-offline')) throw new ApiError({ status: 0, code: 'NETWORK', message: '서버에 연결하지 못했어요.' });
    if (flags.has('dm-send-fail')) throw MOCK_ERRORS.internal();

    const message: DmMessage = { id: messageId(), mine: true, body, createdAt: new Date().toISOString() };
    let row = existing;
    if (!row) {
        row = { id: threadId(), partner: member, messages: [], readUpTo: null, clearedUpTo: null };
        threads = [row, ...seed()];
        newPartnersToday++;
    }
    row.messages.push(message);
    row.readUpTo = message.id;
    sentToday++;
    lastSentAt = Date.now();
    if (flags.has('dm-reply')) {
        const target = row;
        setTimeout(() => {
            target.messages.push({ id: messageId(), mine: false, body: REPLIES[target.messages.length % REPLIES.length]!, createdAt: new Date().toISOString() });
        }, 6000);
    }
    return mockClone({ threadId: row.id, created: !existing, message, quota: quota() });
}
