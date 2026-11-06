import type { SiteNoticeBarResponse } from '~/composables/site/api/useSiteNoticeApi.types';

/**
 * 공지 띠 API — 서버 주소 문자열(`/api/site/notice-bar`)이 있는 **유일한 곳**이다. 상태는 없다(상태는 `useSiteNotice`). 계약: `docs/features/site-notice-bar-contract.md`.
 * 비로그인도 받는다. 무엇을 보일지(공지 게시판 최근 N개 · 직접 입력 문구 · 꺼짐)는 운영자 설정 > 일반 「공지 띠」가 정한다. 꺼져 있으면 `{ version: 'off', items: [] }`.
 */
const PATH = {
    noticeBar: '/api/site/notice-bar',
};

export function useSiteNoticeApi() {
    const client = useApiClient();

    return {
        /** 지금 띠에 보일 공지 — 꺼져 있으면 `items` 가 비어 있다 */
        noticeBar: (signal?: AbortSignal): Promise<SiteNoticeBarResponse> => client.get<SiteNoticeBarResponse>(PATH.noticeBar, { signal }),
    };
}
