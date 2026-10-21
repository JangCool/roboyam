/**
 * 지금 보고 있는 게시판 — 메뉴(상단 탭·햄버거·왼쪽 메뉴)가 어디를 밝힐지, 글쓰기 버튼이 어디로 갈지 아는 데 쓴다.
 * 게시판 목록·글 상세·글쓰기 화면이 알려 주고(`set`), 화면을 떠나면 지운다(`clear`).
 *
 * `write` 는 게시판 목록 화면이 받은 "지금 사용자의 글쓰기 가능 여부"(`useBoardApi().board` 의 `canWrite`)다 —
 * 떠 있는 글쓰기 버튼(FAB)이 운영진 전용 게시판에서 일반 회원에게는 보이지 않게 하려는 것이다.
 */
import type { WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';

export interface CurrentBoardWrite {
    slug: string;
    canWrite: boolean;
    reason: WriteBlockedReason | null;
}

export function useCurrentBoard() {
    const slug = useState<string | null>('current-board', () => null);
    const write = useState<CurrentBoardWrite | null>('current-board-write', () => null);

    function set(next: string): void {
        slug.value = next;
        if (write.value && write.value.slug !== next) write.value = null;
    }

    /** `ifSlug` 를 주면 아직 그 게시판일 때만 지운다 — 새 화면이 먼저 다른 게시판을 알렸다면 건드리지 않는다 */
    function clear(ifSlug?: string): void {
        if (ifSlug && slug.value !== ifSlug) return;
        slug.value = null;
        write.value = null;
    }

    function setWrite(value: CurrentBoardWrite | null): void {
        write.value = value;
    }

    return { slug, write, set, clear, setWrite };
}
