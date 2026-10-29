/**
 * 공개 가젯 페이지 API(`GET /api/pages/{key}`)가 주는 모양 — 서버 `GadgetPageController`(docs/features/gadget-page-contract.md 「API」, 2026-10-29).
 * 가젯 페이지 = 홈처럼 가젯 격자로 꾸미는 공개 페이지(사용자 결정 2026-10-29 — 첫 페이지 「커뮤니티」 `/p/community`, 다음 「주제」).
 * 배치·가젯은 홈 가젯 응답(`HomeGadgetsResponse`)과 같은 모양이다 — 지금 사이트 테마의 그 페이지 배치(저장한 적 없으면 페이지의 처음 배치).
 * 가젯 데이터의 대상 게시판 「이 페이지의 폴더 게시판 전체」(`boardScope: 'FOLDER'`)는 서버가 페이지의 기준 폴더로 풀어 고른다(화면은 받기만 한다).
 */
import type { HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';

/** 페이지 머리 — 이름·설명·글쓰기 단추 */
export interface GadgetPageHead {
    key: string;
    name: string;
    nameEn: string | null;
    /** 설명(비면 머리에 둘째 줄이 없다) */
    description: string;
    /** 글쓰기 단추를 두는가 */
    writeButton: boolean;
    /** 글쓰기에서 골라 둘 게시판 slug — 기준 폴더의 첫 게시판(없으면 null — 글쓰기 화면이 고르게 한다) */
    writeBoard: string | null;
}

/** `GET /api/pages/{key}` 응답 — 없는 페이지는 404 `GADGET_PAGE_NOT_FOUND` */
export interface GadgetPageResponse extends HomeGadgetsResponse {
    page: GadgetPageHead;
}
