import type { BoardSanctionKind, BoardSanctionReasonCode } from '~/composables/board/api/useBoardStaffApi.types';

/** 작성자 제재 대화창(`BoardSanctionDialog`)이 올리는 값 — 덧붙임은 앞뒤 공백을 뗀 값(비면 null) */
export interface BoardSanctionDialogValue {
    kind: BoardSanctionKind;
    reasonCode: BoardSanctionReasonCode;
    reasonText: string | null;
}

/** 대화창이 그릴 현황 읽기 상태 — `protected` 는 받을 수 없는 대상(409 BOARD_STAFF_TARGET_PROTECTED) */
export type BoardSanctionDialogState = 'loading' | 'ready' | 'error' | 'protected';
