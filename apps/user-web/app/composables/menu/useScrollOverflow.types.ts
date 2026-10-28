/** `AppScrollRow` 가 밖으로 내놓는 것 — 스크롤 상자(옆으로 미는 줄). 부모가 `useCenterActive` 를 잇는 데 쓴다(`useScrollRowRef`) */
export interface ScrollRowHandle {
    scroller: HTMLElement | null;
}
