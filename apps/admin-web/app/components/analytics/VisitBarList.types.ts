/** `VisitBarList` 의 계약 타입 — 줄을 만드는 쪽들이 같이 쓴다 */

/** 줄 하나 */
export interface VisitBarRow {
    key: string;
    label: string;
    value: number;
    /** 숫자 대신 보일 글자(평균 등) */
    valueText?: string;
    /** 이름 아래 작은 글자(경로) */
    sub?: string;
    /** 마우스를 올리면 보일 전체 이름 */
    title?: string;
    /** 이름 앞 꼬리표(화면 종류) */
    tag?: string;
    /** 막대·점 색(CSS 식) */
    color?: string;
    mono?: boolean;
    highlight?: boolean;
    /** 막대를 흐리게(「바로 들어옴」·「그 밖」) */
    muted?: boolean;
    /** 이 줄은 누를 수 없다 */
    selectable?: boolean;
}
