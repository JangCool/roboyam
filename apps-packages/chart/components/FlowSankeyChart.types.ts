/**
 * `FlowSankeyChart` 의 계약 타입 — 앱은 `#layers/chart/components/FlowSankeyChart.types` 로 import 한다.
 * 레이어는 앱의 i18n 키·숫자 규칙을 모르므로, 화면 문구는 앱이 번역한 글자(`FlowSankeyLabels`)로, 숫자 글자는 앱의 함수(`FlowSankeyFormat`)로 넘긴다.
 */

/** 끝 칸의 모양 — 보통 페이지 · 「바로 들어옴」 같은 바깥(흐린 주황) · 상위 밖을 묶은 「그 밖」(흐린 회색) */
export type FlowSankeyTone = 'page' | 'entry' | 'rest';

/** 들어온 곳·나간 곳 한 칸 */
export interface FlowSankeyEnd {
    /** 칸 식별자(페이지 경로 등) — 누르면 `select` 로 돌려준다. 들어온 쪽과 나간 쪽에 같은 값이 있어도 된다 */
    key: string;
    /** 칸 이름(차트 글자 — 길면 말줄임) */
    label: string;
    /** 툴팁에 보일 긴 이름(경로 등) — 없으면 `label` */
    detail?: string;
    /** 이동 횟수(0 보다 커야 그린다) */
    value: number;
    tone?: FlowSankeyTone;
    /** 누르면 기준으로 바꿀 수 있나(`select`) — 「바로 들어옴」·「그 밖」은 false */
    selectable?: boolean;
}

/** 가운데(기준) 칸 */
export interface FlowSankeyCenter {
    key: string;
    label: string;
    detail?: string;
}

/** 앱이 번역해 넘기는 문구 묶음 */
export interface FlowSankeyLabels {
    /** 차트 상자(`role="img"`)의 요약 `aria-label` */
    summary: string;
    /** 숨긴 표 */
    tableCaption: string;
    /** 숨긴 표 머리 — 방향(들어온 곳·나간 곳) · 페이지 · 이동 */
    direction: string;
    page: string;
    moves: string;
    incoming: string;
    outgoing: string;
    /** 툴팁 — 이동 횟수 뒤에 붙는 말 앞의 이름(「이동」) */
    movesTip: string;
    /** 툴팁 — 누를 수 있는 칸 아래 안내(「눌러서 기준으로 보기」) */
    selectHint: string;
}

/** 앱이 넘기는 글자 함수 — 빠진 것은 레이어 기본(천 단위 쉼표) */
export interface FlowSankeyFormat {
    number: (n: number) => string;
}
