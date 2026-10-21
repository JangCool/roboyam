/**
 * 셸 슬롯(`shell`)의 props 계약 — 모든 테마의 셸 컴포넌트가 같은 계약을 지킨다(그래야 `<component :is>` 로 갈아끼워도 레이아웃이 그대로다). → `.claude/guides/frontend/themes.md`
 *
 * - emits: `open-menu` — 모바일 ☰ 를 눌렀다(드로어는 레이아웃이 연다).
 * - slots: 기본 = 본문(페이지) · `after-header` = 헤더(와 상단 메뉴) 아래에 놓을 안내 배너·모바일 상단 탭(테마와 무관해 레이아웃이 채운다).
 * - 모바일의 하단 탭·글쓰기 버튼·드로어는 테마와 무관해 레이아웃이 그린다. 셸은 데스크톱 구성(헤더·메뉴 위치·본문 폭·푸터)을 정한다.
 */
export interface ThemeShellProps {
    /** 안쪽 화면(글 상세·글쓰기)인가 — 모바일에서 헤더를 숨긴다 */
    inner?: boolean;
    /** 데스크톱 메뉴를 그리는가 — 글쓰기처럼 넓은 화면이 필요한 곳은 페이지가 끈다 */
    sideMenu?: boolean;
}
