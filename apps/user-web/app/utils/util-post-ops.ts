import type { BoardDetail, BoardSecretMode } from '~/composables/board/api/useBoardApi.types';
import { POST_PROCESS_STATUSES } from '~/composables/board/api/usePostApi.types';
import type { PostDetail, PostProcessStatus, PostProcessStatusActor } from '~/composables/board/api/usePostApi.types';

/**
 * 운영 게시판(버그 신고·기능 요청)의 비밀글·처리 상태를 그리고 읽는 순수 규칙. 판정(읽을 수 있는가 · 바꿀 수 있는가)은 서버가 한다.
 * 서버 규칙 이름: 게시판 `secretMode`·`statusEnabled`, 글 `secret`·`processStatus`(`PostProcessStatus` 7개 고정)·`processStatusNote`.
 * 일반 게시판과 소모임 게시판이 같이 쓴다(핫딜·직접홍보 게시판은 서버가 켜지 못하게 한다). 비회원 글도 비밀글이 될 수 있다(글 비밀번호로 연다).
 */

/** 바꾼 쪽 → 이름 i18n 키(`post.statusActor*`) */
const ACTOR_KEY: Record<PostProcessStatusActor, string> = {
    OPERATOR: 'post.statusActorOperator',
    BOARD_STAFF: 'post.statusActorBoardStaff',
    GROUP_STAFF: 'post.statusActorGroupStaff',
};

/** 처리 상태 → 이름 i18n 키(`post.status*`) */
const LABEL_KEY: Record<PostProcessStatus, string> = {
    RECEIVED: 'post.statusReceived',
    CHECKING: 'post.statusChecking',
    IN_PROGRESS: 'post.statusInProgress',
    DONE: 'post.statusDone',
    ON_HOLD: 'post.statusOnHold',
    REJECTED: 'post.statusRejected',
    DUPLICATE: 'post.statusDuplicate',
};

/**
 * 처리 상태 딱지의 색 — 말머리(바탕을 칠한 알약)와 갈리게 테두리 + 글자색. 완료 초록 · 반려 빨강 · 진행 중 파랑 · 확인 중 하늘 · 보류 호박 · 중복 보라 · 접수 회색.
 * 색만으로 가르지 않는다(글자가 늘 있다)
 */
const TONE_CLASS: Record<PostProcessStatus, string> = {
    RECEIVED: 'border-surface-300 text-surface-600 dark:border-surface-600 dark:text-surface-300',
    CHECKING: 'border-sky-300 text-sky-700 dark:border-sky-700 dark:text-sky-300',
    IN_PROGRESS: 'border-blue-300 text-blue-700 dark:border-blue-700 dark:text-blue-300',
    DONE: 'border-green-400 bg-green-50 text-green-700 dark:border-green-700 dark:bg-green-950/40 dark:text-green-300',
    ON_HOLD: 'border-amber-300 text-amber-700 dark:border-amber-700 dark:text-amber-300',
    REJECTED: 'border-red-300 text-red-700 dark:border-red-800 dark:text-red-300',
    DUPLICATE: 'border-purple-300 text-purple-700 dark:border-purple-800 dark:text-purple-300',
};

export default class UtilPostOps {
    /** 처리 상태 7개(보이는 차례) */
    static readonly STATUSES: readonly PostProcessStatus[] = POST_PROCESS_STATUSES;

    /**
     * 처리 상태 이름의 i18n 키.
     * @param {PostProcessStatus} status - 처리 상태.
     * @returns {string} i18n 키.
     */
    static labelKey(status: PostProcessStatus): string {
        return LABEL_KEY[status];
    }

    /**
     * 처리 상태 딱지의 색 클래스.
     * @param {PostProcessStatus} status - 처리 상태.
     * @returns {string} 클래스 문자열.
     */
    static toneClass(status: PostProcessStatus): string {
        return TONE_CLASS[status];
    }

    /**
     * 주소 쿼리(`?status=`) 값을 처리 상태로 읽는다 — 모르는 값은 빈 글자(전체).
     * @param {unknown} value - 쿼리 값.
     * @returns {PostProcessStatus | ''} 처리 상태 또는 빈 글자.
     */
    static parseStatus(value: unknown): PostProcessStatus | '' {
        const text = Array.isArray(value) ? String(value[0] ?? '') : typeof value === 'string' ? value : '';
        return (POST_PROCESS_STATUSES as readonly string[]).includes(text) ? (text as PostProcessStatus) : '';
    }

    /**
     * 게시판의 비밀글 방식 — 옛 응답(칸 없음)·모르는 값은 `OFF`.
     * @param {Pick<BoardDetail, 'secretMode'> | null | undefined} board - 게시판.
     * @returns {BoardSecretMode} 비밀글 방식.
     */
    static secretMode(board: Pick<BoardDetail, 'secretMode'> | null | undefined): BoardSecretMode {
        const mode = board?.secretMode;
        return mode === 'OPTIONAL' || mode === 'ALWAYS' ? mode : 'OFF';
    }

    /**
     * 처리 상태를 쓰는 게시판인가 — 옛 응답(칸 없음)은 끔.
     * @param {Pick<BoardDetail, 'statusEnabled'> | null | undefined} board - 게시판.
     * @returns {boolean} 켜졌으면 true.
     */
    static statusOn(board: Pick<BoardDetail, 'statusEnabled'> | null | undefined): boolean {
        return board?.statusEnabled === true;
    }

    /**
     * 글쓰기 본문의 `secret` — 글쓴이가 고르면 그 값, 모든 글 비밀이면 true, 없음이면 싣지 않는다(undefined). 회원·비회원이 같다.
     * @param {BoardSecretMode} mode - 게시판 비밀글 방식.
     * @param {boolean} checked - 폼의 「비밀글」 체크.
     * @returns {boolean | undefined} 보낼 값(undefined = 칸을 뺀다).
     */
    static writeSecret(mode: BoardSecretMode, checked: boolean): boolean | undefined {
        if (mode === 'OFF') return undefined;
        return mode === 'ALWAYS' ? true : checked;
    }

    /**
     * 글 고치기 본문의 `secret` — 글쓴이가 고르는 게시판(`OPTIONAL`)일 때만 싣는다. 그 밖(모든 글 비밀·없음 — 설정을 바꿨어도 이미 쓴 글은 그대로)은 칸을 뺀다.
     * 회원 글·비회원 글이 같다.
     * @param {BoardSecretMode} mode - 게시판 비밀글 방식.
     * @param {boolean} checked - 폼의 「비밀글」 체크.
     * @returns {boolean | undefined} 보낼 값(undefined = 칸을 뺀다).
     */
    static updateSecret(mode: BoardSecretMode, checked: boolean): boolean | undefined {
        return mode === 'OPTIONAL' ? checked : undefined;
    }

    /** 운영진 메모 최대 글자 수(서버 `fields.note` 와 같다) */
    static readonly NOTE_MAX = 200;

    /**
     * 보낼 메모 — 앞뒤 공백을 떼고, 비면 undefined(칸을 뺀다).
     * @param {string} text - 입력한 메모.
     * @returns {string | undefined} 보낼 메모.
     */
    static noteOf(text: string): string | undefined {
        const trimmed = text.trim();
        return trimmed ? trimmed : undefined;
    }

    /**
     * 바꾼 쪽 이름의 i18n 키 — 모르는 값은 운영자로 본다.
     * @param {PostProcessStatusActor} actor - 바꾼 쪽.
     * @returns {string} i18n 키.
     */
    static actorKey(actor: PostProcessStatusActor): string {
        return ACTOR_KEY[actor] ?? ACTOR_KEY.OPERATOR;
    }

    /**
     * 처리 상태를 바꿀 수 있는가 — 글 상세의 서버 판정(`viewer.canChangeStatus`)이 먼저, 칸이 없는 옛 응답은 게시판 운영진(`viewer.staff`).
     * @param {Pick<PostDetail, 'viewer'>} post - 글 상세.
     * @param {boolean} boardStaff - 게시판 정보의 `viewer.staff`.
     * @returns {boolean} 바꿀 수 있으면 true.
     */
    static canChangeStatus(post: Pick<PostDetail, 'viewer'>, boardStaff: boolean): boolean {
        const fromPost = post.viewer?.canChangeStatus;
        return typeof fromPost === 'boolean' ? fromPost : boardStaff;
    }

    /**
     * 요청 본문에 펼칠 `secret` 칸 — 값이 없으면 칸을 아예 뺀다.
     * @param {boolean | undefined} secret - `writeSecret`·`updateSecret` 의 값.
     * @returns {{ secret?: boolean }} 펼칠 조각.
     */
    static secretField(secret: boolean | undefined): { secret?: boolean } {
        return secret === undefined ? {} : { secret };
    }
}
