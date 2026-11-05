/** 알약 칩 하나(`EmoticonChipGroup`) — 값 · 화면 문구(부르는 쪽이 `t()` 로 만든다) · 앞 아이콘(선택) */
export interface EmoticonChipOption<V extends string = string> {
    value: V;
    label: string;
    icon?: string;
}
