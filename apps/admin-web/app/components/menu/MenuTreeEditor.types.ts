/** `MenuTreeEditor` 가 그리는 한 줄의 최소 모양 — 부르는 쪽이 노드 등을 더 실어 `row` 슬롯에서 쓴다 */
export interface MenuTreeEditorRow {
    /** 노드의 화면 열쇠 */
    key: string;
    /** 최상위가 1 */
    depth: number;
    /** 안에 넣을 수 있는 줄(폴더·묶음·부모 항목) — 가운데에 놓으면 안으로 들어간다 */
    container: boolean;
    /** 알림·손잡이 이름에 쓰는 보일 이름 */
    name: string;
}
