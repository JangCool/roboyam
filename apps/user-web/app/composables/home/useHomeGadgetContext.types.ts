import type { Ref } from 'vue';

/**
 * 가젯 모양 — 템플릿(테마 슬롯 `home`)이 정해 아래 가젯에 내려 준다. 가젯 컴포넌트는 테마 id 를 모르고 이 값만 본다(새 테마가 와도 가젯을 고치지 않는다).
 *  - `classic` 기본형: 카드 없는 구역 — 구역 이름 줄(아래 선) + 내용 + 아래 가운데 「더보기 ›」 줄. 평평하지 않은 가젯은 테두리 카드.
 *  - `card` 카드형(다음식·네이버형): 카드(역할 변수 `--gadget-card-*`) + 높이 48 머리(제목 · 오른쪽 「더보기 ›」).
 */
export type HomeGadgetLook = 'classic' | 'card';

/**
 * 템플릿이 고르는 가젯 세부 모양(선택) — 주지 않은 값은 기본값이라, 값을 주지 않는 템플릿(기본형·다음식·네이버형)의 모양은 그대로다. 가젯은 테마 id 를 모르고 이 값만 본다.
 */
export interface HomeGadgetLookOptions {
    /** 하이라이트(첫 글 크게)를 넓은 칸(40rem 이상)에서 「큰 글 왼쪽 + 나머지 오른쪽 목록」 두 열로 — 기본 false(큰 글 위 + 나머지 두 열 목록). 로보얌 템플릿 */
    heroSplit: boolean;
    /** 갤러리가 넓은 칸에서 쓰는 열 수 — 기본 3(30rem 이상 3칸, 좁으면 2). 4 면 36rem 이상에서 한 줄 4칸(좁으면 2). 로보얌 템플릿 */
    galleryColumns: 3 | 4;
    /** 한 열(모바일) 모양 — 홈 격자가 한 열(`stack` — md 미만 또는 홈 폭 36rem 미만)일 때만 쓴다. 데스크톱·태블릿 격자에는 영향이 없다 */
    mobile: HomeGadgetMobileLook;
}

/**
 * 한 열(모바일) 세부 모양 — 템플릿(테마)이 고른다. 기본값은 모두 「지금 모양 그대로」(위아래로 쌓기 · `reading` 순서)라, 값을 주지 않은 템플릿의 모바일은 바뀌지 않는다.
 * 가로로 미는 줄은 공통 클래스 `gadget-scroll-row`(`main.css` — 스크롤바 숨김 · 스냅 · 카드 안쪽 여백까지 닿기)다.
 */
export interface HomeGadgetMobileLook {
    /**
     * 한 열의 가젯 순서 — `reading`(기본): 운영자 배치의 (y, x) · `columns`: 본문 열 먼저, 오른쪽 열은 그 뒤(`UtilHomeGrid.byColumns` — 어떤 가젯도 가로지르지 않는 칸 경계로 열을 나눈다. 나눌 경계가 없으면 `reading`).
     * 모바일에서만 보일 가젯(`mobile` 노출)을 본문 열 중간에 두면 그 자리에 끼어든다.
     */
    order: 'reading' | 'columns';
    /** 하이라이트(`heroSplit` 큰 글)의 나머지 글을 가로로 미는 카드 줄로 */
    highlightScroll: boolean;
    /** 하이라이트 큰 글(`heroSplit`)의 사진을 카드 좌우 끝까지(반경 0) */
    heroBleed: boolean;
    /** 갤러리를 가로로 미는 썸네일 줄로(약 2.3장이 보인다) */
    galleryScroll: boolean;
    /** 공동구매를 넘기기 단추 대신 가로로 미는 카드 줄로(하나면 한 장) */
    groupBuyScroll: boolean;
    /** 인기 소모임을 가로로 미는 작은 카드 줄로(첫 글자 상자 + 이름 + 멤버 수) */
    groupsScroll: boolean;
    /** 게시판 카드 묶음을 가로로 미는 카드 줄로 */
    boardCardsScroll: boolean;
}

/** `provideHomeGadgetLook` 에 주는 세부 모양 — 모두 선택, 한 열 모양도 바꿀 값만 */
export type HomeGadgetLookInput = Partial<Omit<HomeGadgetLookOptions, 'mobile'>> & { mobile?: Partial<HomeGadgetMobileLook> };

/** 홈 한 방문에서 가젯들이 나눠 쓰는 것 */
export interface HomeGadgetShared {
    /** 데스크톱 폭(`lg`, 1024px 이상)인가 — 가젯의 PC/모바일 노출과 홈 격자(`HomeGadgetGrid`)의 데스크톱 배치가 이 값을 쓴다 */
    wide: Ref<boolean>;
    /** 지금 시각(밀리초, 1분마다) — NEW·마감까지 남은 날 */
    now: Ref<number>;
}
