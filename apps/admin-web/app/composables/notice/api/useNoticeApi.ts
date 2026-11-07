import type { RichImageUploadResult } from '#layers/rich-editor/components/RichEditor.types';
import type { NoticeContext, NoticeCreated, NoticeDetail, NoticeListView, NoticeQuery, NoticeSaveRequest } from '~/composables/notice/api/useNoticeApi.types';

/**
 * 공지 관리 API — 서버 주소(`/api/admin/notices…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약: `docs/features/notice-admin-contract.md` 「API」(서버 admin `com.community.notice`).
 * 공지 = 공지 게시판(slug `notice`)의 글. 새 저장소 없이 그 게시판 글을 다룬다. 작성자는 로그인한 운영자에게 이어진 운영자 회원 계정(operator-member-account-contract).
 *   - `GET    /api/admin/notices/context` (`post.read`) → `NoticeContext` — 공지 게시판(없으면 null) · 내 운영자 회원 계정(멈춤이어도 `userId`·`nickname` 이 있다) · 공지 띠 요약
 *   - `GET    /api/admin/notices?q=&state=&prefix=&page=&size=` (`post.read`) → `NoticeListView`(고정 글이 먼저 — `pinnedAt` 내림차순, 그다음 최신) · 404 `NOTICE_BOARD_MISSING`
 *   - `GET    /api/admin/notices/{id}` (`post.read`) → `NoticeDetail`(`content.format` 은 늘 `HTML`) · 공지 게시판 글이 아니면 404 `POST_NOT_FOUND`
 *   - `POST   /api/admin/notices` `{ prefixId|null, title, body, pinned }` (`post.manage`) → 201 `{ id }`
 *   - `PUT    /api/admin/notices/{id}` 같은 몸 (`post.manage`) → 204 — 작성자는 바뀌지 않는다. 지운 글은 409 `POST_STATE_INVALID`
 *       · 400 VALIDATION(`fields.title` 1~100자 · `fields.body`(보이는 글자 10자 이상, 이미지 30·영상 10 상한) · `fields.prefixId` 켜진 말머리)
 *       · 409 `NOTICE_WRITER_NOT_LINKED`(연결 없음·멈춤) · 409 `PIN_LIMIT` · 409 `NOTICE_BOARD_INACTIVE`(꺼진 게시판 — 쓰기·고치기·고정만 막힘) · 422 `IMAGE_UPLOAD_NOT_ALLOWED` · 404 `NOTICE_BOARD_MISSING`
 *   - `POST   /api/admin/notices/{id}/pin` · `/unpin` (`post.manage`, 본문 없음) → 204 · 409 `PIN_LIMIT` · 409 `POST_STATE_INVALID`(공개 글만 고정)
 *   - `DELETE /api/admin/notices/{id}` (`post.manage`, 본문 없음) → 204 — 게시글 관리의 삭제와 같은 상태(복구·조치 기록은 게시글 관리 — 조치 기록 덧붙임 `via: NOTICE_ADMIN`)
 *   - `POST   /api/admin/notices/images` multipart `file` (`post.manage`) → 201 `{ id, url, width, height }` · 400 `fields.file` · 409 `NOTICE_WRITER_NOT_LINKED`
 *       본문에는 `<img data-image-id="{id}">` 표식으로 들어가고 서버가 src·크기를 채운다(RichEditor 레이어 `imageBlock` 이 그렇게 만든다).
 * 쓰기는 CSRF(`useApiClient` 가 붙인다). 오류는 모두 `ApiError`.
 */
const ROOT = '/api/admin/notices';
const PATH = {
    context: `${ROOT}/context`,
    list: ROOT,
    images: `${ROOT}/images`,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    act: (id: string, action: 'pin' | 'unpin') => `${ROOT}/${encodeURIComponent(id)}/${action}`,
};

export function useNoticeApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 공지 게시판 · 내 운영자 회원 계정 · 공지 띠 요약 */
        context: (): Promise<NoticeContext> => client.get<NoticeContext>(PATH.context),

        /** 공지 목록 — 빈 조건은 보내지 않는다 */
        list: (query: NoticeQuery, signal?: AbortSignal): Promise<NoticeListView> =>
            client.get<NoticeListView>(PATH.list, {
                query: {
                    q: query.q || undefined,
                    state: query.state && query.state !== 'ALL' ? query.state : undefined,
                    prefix: query.prefix || undefined,
                    page: query.page,
                    size: query.size,
                },
                signal,
            }),

        /** 공지 하나 + 본문 */
        detail: (id: string): Promise<NoticeDetail> => client.get<NoticeDetail>(PATH.one(id)),

        /** 새 공지 — 내 운영자 회원 계정으로 올린다 */
        create: (body: NoticeSaveRequest): Promise<NoticeCreated> => client.post<NoticeCreated>(PATH.list, body),

        /** 공지 고치기 — 작성자는 그대로 */
        update: (id: string, body: NoticeSaveRequest): Promise<void> => client.put<void>(PATH.one(id), body),

        pin: (id: string): Promise<void> => client.post<void>(PATH.act(id, 'pin')),

        unpin: (id: string): Promise<void> => client.post<void>(PATH.act(id, 'unpin')),

        /** 지우기 — 복구는 게시글 관리에서 */
        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),

        /**
         * 본문 이미지 한 장 올리기(RichEditor `upload`) — 공동구매 이미지와 같은 방식(`$fetch` multipart).
         * admin-web `useApiClient` 에는 XHR 진행률 올리기가 없어 진행률은 다 올라간 뒤 한 번(부르는 쪽이 `onProgress(1)`) 알린다. 취소는 `signal` 로 된다.
         */
        uploadImage: (file: File, signal?: AbortSignal): Promise<RichImageUploadResult> => {
            const form = new FormData();
            form.append('file', file);
            return client.post<RichImageUploadResult>(PATH.images, form, { signal });
        },
    };
}
