import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { MyJjalItem } from '~/composables/jjal/api/useJjalApi.types';

/** 올리기 창 줄이기(`util-jjal-image.ts`)의 문제 — 형식 아님 · 움직이는 WebP 를 못 풂 · 깨진 그림 · 10초 넘음 · 100장 넘음 · 줄여도 2MB 넘음 · 원본이 너무 큼 */
export type JjalPrepareProblem = 'UNSUPPORTED' | 'WEBP_ANIMATED_UNSUPPORTED' | 'BROKEN' | 'TOO_LONG' | 'TOO_MANY_FRAMES' | 'RESULT_TOO_BIG' | 'SOURCE_TOO_BIG';

/** 줄인 결과 — 올릴 파일과 그 크기 */
export interface JjalPrepared {
    blob: Blob;
    /** 올릴 파일 이름(확장자만 뜻이 있다) */
    filename: string;
    width: number;
    height: number;
    animated: boolean;
    /** 움짤 장 수(정지 그림은 1) */
    frames: number;
    /** 움짤 재생 시간(ms, 정지 그림은 0) */
    durationMs: number;
}

/**
 * 내 짤 규칙 — 한도 값·화면 주소·게시판별 사용·고르기 창 칸·순서 옮기기. 상태도 요청도 없다. 계약 `docs/features/my-jjal-contract.md`.
 * 순서는 회원이 정한다(계약 「순서」) — 서버가 그 차례로 주고, 화면은 다시 줄 세우지 않는다(최근 쓴 차례는 쓰지 않는다).
 * 토큰(`:jjal-ID:`) 자르기·그리기는 레이어 `UtilRichJjal`(`#layers/rich-editor/utils/util-rich-jjal`) 한 곳이다.
 * 값은 계약 「규칙(가정 — 구현 때 값은 설정으로)」 그대로 — 판정은 서버이고 화면은 미리 알린다.
 */
export default class UtilJjal {
    /** 한 회원 보관함 상한(올린 것 + 담은 것) — 서버가 `limit` 으로 준 값이 우선 */
    static readonly LIMIT = 50;
    /** 댓글 하나에 넣을 수 있는 짤 수(도배 방지 가정) */
    static readonly MAX_PER_COMMENT = 1;
    /** 글 하나에 넣을 수 있는 짤 수 */
    static readonly MAX_PER_POST = 5;
    /** 올리는 파일 크기 상한(서버 `413`) */
    static readonly MAX_BYTES = 2 * 1024 * 1024;
    /** 저장본 긴 변(px) — 브라우저가 이 크기로 줄여 올린다 */
    static readonly MAX_SIDE = 300;
    /** 움짤 프레임 수 상한 */
    static readonly MAX_FRAMES = 100;
    /** 움짤 재생 시간 상한(ms) */
    static readonly MAX_DURATION_MS = 10_000;
    /** 브라우저가 받아 줄이는 원본 크기 상한 — 너무 큰 파일로 브라우저가 멈추지 않게(가정) */
    static readonly SOURCE_MAX_BYTES = 20 * 1024 * 1024;
    /** 올리기 창이 받는 형식(움직이는 WebP 는 브라우저가 풀 수 있을 때만) */
    static readonly ACCEPT = 'image/gif,image/png,image/jpeg,image/webp';

    /** 내 로보티콘 화면의 「내 짤」 칸 — 꽉 찼을 때 「정리하기」가 보낸다 */
    static readonly MY_SECTION_ID = 'my-jjals';
    static readonly MY_SECTION_PATH = `/me/robotikon#${UtilJjal.MY_SECTION_ID}`;

    /**
     * 글 본문 고르기 창에 「짤」 탭을 보이나 — 공개 게시판 칸 `postJjalAllowed`(본문 짤 허용 — 서버가 게시판 값 ?? 기본값으로 푼 유효값, 이미지 올리기 허용과 따로).
     * 게시판이 아직 없으면(글쓰기에서 고르기 전 등) 켠 것으로 본다 — 저장 때 서버가 판정한다(`422 JJAL_NOT_ALLOWED`).
     */
    static postAllowed(board: Pick<BoardDetail, 'postJjalAllowed'> | null | undefined): boolean {
        return board?.postJjalAllowed ?? true;
    }

    /** 댓글 고르기 창에 「짤」 탭을 보이나 — 공개 게시판 칸 `commentJjalAllowed`(서버가 게시판 값 ?? 기본값으로 푼 유효값). 게시판이 아직 없으면 켠 것으로 본다 */
    static commentAllowed(board: Pick<BoardDetail, 'commentJjalAllowed'> | null | undefined): boolean {
        return board?.commentJjalAllowed ?? true;
    }

    /** 19금 게시판(성인 전용)의 글·댓글 짤은 담기 없음 — 서버 `collectable` 이 정본이고 화면은 한 번 더 끈다 */
    static collectableIn(board: Pick<BoardDetail, 'adultOnly'> | null | undefined): boolean {
        return board?.adultOnly !== true;
    }

    /** 고르기 창 칸 — 내가 정한 순서 그대로, 내려간 짤(서버가 `url: null` 로 준다)만 뺀다(자리는 내 짤 칸에 남는다) */
    static forPicker(items: readonly MyJjalItem[]): MyJjalItem[] {
        return items.filter((i) => !i.blocked && Boolean(i.url));
    }

    /**
     * 순서 옮기기 — `from` 번째를 빼서 `to` 번째(뺀 뒤의 차례)에 넣는다. 범위를 벗어나면 끝에 붙인다.
     * @returns 새 배열(넘긴 배열은 그대로).
     */
    static move<T>(items: readonly T[], from: number, to: number): T[] {
        if (from < 0 || from >= items.length) return [...items];
        const next = [...items];
        const [picked] = next.splice(from, 1);
        next.splice(Math.max(0, Math.min(to, next.length)), 0, picked as T);
        return next;
    }

    /** 두 ID 차례가 같은가(순서를 바꿨는지) */
    static sameOrder(a: readonly string[], b: readonly string[]): boolean {
        return a.length === b.length && a.every((id, i) => id === b[i]);
    }

    /** 내려간 짤 수 */
    static blockedCount(items: readonly MyJjalItem[]): number {
        return items.filter((i) => i.blocked).length;
    }

    /** 바이트 → 「1.2MB」·「340KB」 */
    static size(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
        return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    }
}
