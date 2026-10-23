/** 에디터 도구 모음 드롭다운(`RichEditorMenuButton`)의 항목 하나 */
export interface RichMenuItem {
    key: string;
    label: string;
    /** 앞에 붙는 아이콘(primeicons 클래스) — 없으면 `badge` 글자 */
    icon?: string;
    /** 아이콘 대신 앞에 붙는 짧은 글자(예: "H2") */
    badge?: string;
    /** 지금 적용 중인 항목 — 연한 바탕과 체크 표시 */
    active?: boolean;
    disabled?: boolean;
    /** 항목 글자에 입히는 미리보기 style(색 견본 등) */
    sample?: Record<string, string>;
}
