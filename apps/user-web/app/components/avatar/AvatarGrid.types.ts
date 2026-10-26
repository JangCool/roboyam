/** 아바타 격자의 칸 하나 — 부르는 쪽이 카탈로그 줄(`AvatarItem`)에서 만든다 */
export interface AvatarGridCell {
    id: string;
    /** 카탈로그 한글 이름 */
    name: string;
    url: string;
    /** 눌린 칸(지금 쓰는 것·지금 보는 것) — 고리와 체크 표시 */
    active: boolean;
    /** 이름 아래 작은 글(가격·「보유」·「사용 중」·「판매 중단」) — 없으면 빈 글자 */
    caption: string;
    /** 흐리게(판매 중단) */
    dim: boolean;
    /** 스크린리더 이름(이름 + 상태) */
    ariaLabel: string;
}
