/**
 * 보기 설정 API 가 주고받는 모양 — 서버 응답 그대로(`server/api` `com.community.viewsetting`, `textScale` 은 JSON 정수). 경로·규칙은 community `docs/api-profile.md` 「설정(보기)」(`GET·PUT /api/me/settings`)이고,
 * 칸은 로보얌에 적용할 곳이 있는 것만 남겼다(야간 모드·글자 크기·댓글 정렬 + 데스크톱 메뉴 위치). 사이트 테마·색·본문 너비·목록 밀도는 운영자가 고르는 사이트 테마 몫이라 개인 설정에 두지 않는다
 * (`.claude/guides/frontend/themes.md`). 메뉴 위치는 회원 > 운영자 > 테마 기본 순으로 정한다(사용자 결정 2026-10-28, `UtilMenuPosition`). 시간 표시는 따로 있다(`/api/me/display`). 계약 후보: `docs/features/member-social-contract.md`.
 */

import type { MemberMenuSetting } from '~/utils/util-menu-position';

/** 야간 모드 — `AUTO` 는 기기 설정(`prefers-color-scheme`)을 따른다 */
export type ThemeSetting = 'AUTO' | 'LIGHT' | 'DARK';

/** 글자 크기(백분율) — 글자만 바뀌고 여백은 그대로 */
export type TextScaleSetting = 90 | 100 | 110 | 125;

/** 댓글 기본 정렬 — 댓글 목록 API 의 `sort` 로 보낸다 */
export type CommentOrderSetting = 'OLDEST' | 'LATEST';

/** `GET·PUT /api/me/settings` — `PUT` 은 칸을 모두 보낸다(통째로 바꾼다). 응답은 저장된 설정 */
export interface ViewSettings {
    theme: ThemeSetting;
    textScale: TextScaleSetting;
    commentOrder: CommentOrderSetting;
    /**
     * 데스크톱 메뉴 위치(`AUTO` = 사이트 설정 따름 · `TOP` · `LEFT` · `RIGHT` · `TOP_LEFT` · `TOP_RIGHT`). 모바일에는 적용하지 않는다. 서버 `member_view_settings.menu_position`(V105).
     */
    menuPosition: MemberMenuSetting;
}
