import type { AnonymousRevealResult, AnonymousTarget } from '~/composables/anonymous/api/useAnonymousAuthorApi.types';

/** 익명 작성자 열람 창(`AnonymousRevealDialog`)이 그리는 값 — `useAnonymousReveal().view` */
export interface AnonymousRevealView {
    target: AnonymousTarget | null;
    busy: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error: string;
    /** 서버 칸 오류(`reason`·`memo`) */
    fieldErrors: Record<string, string>;
    /** 열람 결과 — 열기 전 null */
    result: AnonymousRevealResult | null;
}
