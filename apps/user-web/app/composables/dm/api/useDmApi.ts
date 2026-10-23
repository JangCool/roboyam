import {
    mockDmCanSend,
    mockDmMarkRead,
    mockDmRecipients,
    mockDmRemove,
    mockDmRoom,
    mockDmSend,
    mockDmSettings,
    mockDmThreads,
    mockDmUnreadCount,
    mockMyDm,
    mockUpdateDmSettings,
} from '~/composables/dm/api/mock/dm-mock';
import type { DmViewer } from '~/composables/dm/api/mock/dm-mock';
import type {
    DmCanSend,
    DmPageQuery,
    DmRecipientList,
    DmRoom,
    DmSendResult,
    DmSettings,
    DmThreadList,
    DmUnreadCount,
    MyDm,
} from '~/composables/dm/api/useDmApi.types';

/**
 * 쪽지 API — 서버 주소 문자열(`/api/dm/…`·`/api/me/dm…`·`/api/users/{id}/dm`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useDmBadge`·`useDmInbox`).
 * 계약 후보: `docs/features/member-social-contract.md`(모양은 community `docs/api-dm.md`). 모두 **로그인 필요**(401). 읽기·삭제·수신 설정은 이메일 인증 전·정지 중에도 되고 **보내기만** 막힌다.
 *
 * 보내기(`send`) 오류(위에서 처음 걸린 것 하나): `400 VALIDATION fields.body`(빈 글·500자 초과·제어 문자) · `404 PROFILE_NOT_FOUND` · `400 DM_SELF` ·
 * `403 EMAIL_NOT_VERIFIED` · `403 ACCOUNT_RESTRICTED` · `403 BUSINESS_ACCOUNT_RESTRICTED` · `403 DM_SENDER_TOO_NEW`(가입 7일 전) · `403 DM_UNAVAILABLE`(차단 관계 — 어느 쪽이든) ·
 * `403 DM_RECIPIENT_REFUSES` · `400 DM_CONTACT_NOT_ALLOWED`(상대가 답장하기 전 링크·연락처) · `429 RATE_LIMITED`(3초) · `429 DM_DAILY_LIMIT`(20통) · `429 DM_NEW_PARTNER_LIMIT`(새 상대 5명).
 * 대화방·읽음·삭제의 없는·남의 대화는 모두 `404 DM_THREAD_NOT_FOUND`. 받는 사람 찾기 `400 VALIDATION fields.q`(2~16자). 수신 설정 `400 VALIDATION fields.acceptDm`.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/dm-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    me: '/api/me/dm',
    unreadCount: '/api/dm/unread-count',
    settings: '/api/me/dm/settings',
    threads: '/api/dm/threads',
    thread: (id: string) => `/api/dm/threads/${encodeURIComponent(id)}`,
    read: (id: string) => `/api/dm/threads/${encodeURIComponent(id)}/read`,
    recipients: '/api/dm/recipients',
    user: (id: string) => `/api/users/${encodeURIComponent(id)}/dm`,
};

export function useDmApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 받는 사람의 이름은 명부·실제 게시판 글에서 찾고, 보낼 자격(인증·정지·사업자)은 로그인 정보로 정한다
    const boardApi = useBoardApi();
    const source = { boards: () => boardApi.boards(), posts: boardApi.posts };
    const { user, restriction } = useAuth();
    const viewer = (): DmViewer => ({ user: user.value, restricted: restriction.value?.status === 'SUSPENDED' || restriction.value?.status === 'BANNED' });
    void PATH;

    return {
        /** 내 쪽지 상태 — 수신 설정·보낼 자격·한도·안 읽은 대화 수·규칙 */
        me: (signal?: AbortSignal): Promise<MyDm> =>
            // MOCK:
            (void signal, mockMyDm(viewer())),
        // REAL: client.get<MyDm>(PATH.me, { signal }),

        /** 안 읽은 대화 수(상한 100) — 배지가 보이는 동안 약 60초마다 부른다 */
        unreadCount: (signal?: AbortSignal): Promise<DmUnreadCount> =>
            // MOCK:
            (void signal, mockDmUnreadCount()),
        // REAL: client.get<DmUnreadCount>(PATH.unreadCount, { signal }),

        /** 쪽지 수신 설정 */
        settings: (signal?: AbortSignal): Promise<DmSettings> =>
            // MOCK:
            (void signal, mockDmSettings()),
        // REAL: client.get<DmSettings>(PATH.settings, { signal }),

        /** 수신 설정 바꾸기(멱등) — 받지 않아도 기존 대화는 그대로, 내가 먼저 보내는 것은 막지 않는다 */
        updateSettings: (acceptDm: boolean): Promise<DmSettings> =>
            // MOCK:
            mockUpdateDmSettings(acceptDm),
        // REAL: client.put<DmSettings>(PATH.settings, { acceptDm }),

        /** 쪽지함 — 마지막 쪽지가 최근인 대화가 위 */
        threads: (query: DmPageQuery = {}, signal?: AbortSignal): Promise<DmThreadList> =>
            // MOCK:
            (void signal, mockDmThreads(query)),
        // REAL: client.get<DmThreadList>(PATH.threads, { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 대화방 — 최근 쪽지가 앞. **읽음 처리는 하지 않는다**(화면이 보여 준 뒤 `markRead`) */
        thread: (id: string, query: DmPageQuery = {}, signal?: AbortSignal): Promise<DmRoom> =>
            // MOCK:
            (void signal, mockDmRoom(viewer(), id, query)),
        // REAL: client.get<DmRoom>(PATH.thread(id), { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 이 대화를 지금까지 온 것까지 읽음으로(멱등 204) — 이 대화의 쪽지 알림도 읽음이 된다 */
        markRead: (id: string): Promise<void> =>
            // MOCK:
            mockDmMarkRead(id),
        // REAL: client.post<void>(PATH.read(id)),

        /** 대화 삭제 — **내 쪽지함에서만**(멱등 204). 되돌릴 수 없다 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDmRemove(id),
        // REAL: client.del<void>(PATH.thread(id)),

        /** 받는 사람 찾기 — `q` 는 2~16자 */
        recipients: (q: string, signal?: AbortSignal): Promise<DmRecipientList> =>
            // MOCK:
            (void signal, mockDmRecipients(source, viewer(), q)),
        // REAL: client.get<DmRecipientList>(PATH.recipients, { query: { q }, signal }),

        /** 이 회원에게 보낼 수 있나(읽기만 한다 — 한도를 쓰지 않는다). 없는·탈퇴한 회원 `404 PROFILE_NOT_FOUND` */
        canSend: (userId: string, signal?: AbortSignal): Promise<DmCanSend> =>
            // MOCK:
            (void signal, mockDmCanSend(source, viewer(), userId)),
        // REAL: client.get<DmCanSend>(PATH.user(userId), { signal }),

        /** 쪽지 보내기 — `201`. 오류는 파일 머리말 */
        send: (userId: string, body: string): Promise<DmSendResult> =>
            // MOCK:
            mockDmSend(source, viewer(), userId, body),
        // REAL: client.post<DmSendResult>(PATH.user(userId), { body }),
    };
}
