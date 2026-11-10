import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';

/**
 * 홈 슬롯(`home`)의 props 계약 — 모든 테마의 홈 템플릿(`DefaultHome` 기본형 · `DaumHome` 다음식 · `RoboyamHome` 로보얌형 · `NaverHome` 네이버형)이 같은 계약을 지킨다. → `.claude/guides/frontend/themes.md`
 *
 * - 템플릿 = 사이트 테마(사용자 결정 2026-10-28). 템플릿은 **겉모양만** 정한다 — 가젯 모양(`provideGadgetLook`)과 테마 표식(`data-template`)을 두고 공통 격자 `GadgetGrid` 를 그린다.
 * - **배치(어느 가젯이 어디에 얼마나 크게)는 운영자 몫**이다(사용자 결정 2026-10-28 — 관리자 홈 배치 편집기, 테마마다 칸 수 12·24 + 가젯별 x·y·w·h). 템플릿은 칸 이름·열·모바일 순서를 갖지 않는다.
 * - 슬롯 `lead`(선택): 격자 위에 그릴 것 — 가젯 페이지의 제목 카드(제목 카드에 넣은 가젯이 템플릿의 가젯 모양을 따르게 템플릿 안에서 그린다, 2026-11-10).
 * - 데이터 조회·오류·투자 고지는 페이지(`pages/index.vue`)가 한다. 템플릿은 그리기만 한다.
 */
export interface HomeTemplateProps {
    /** 운영자 배치(읽은 값) */
    layout: HomeLayout;
    /** 가젯 목록 */
    gadgets: Gadget[];
}
