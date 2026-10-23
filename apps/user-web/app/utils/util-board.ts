import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BoardView, CommentEditorLevel, PostEditorLevel, PrefixView } from '~/composables/board/api/useBoardApi.types';
import type { ContentFormat } from '~/composables/board/api/usePostApi.types';
import UtilColor from '~/utils/util-color';

/**
 * 게시판·말머리를 화면에 그리는 규칙.
 *
 * ⚠ **게시판·말머리·메뉴 그룹의 목록은 여기 없다** — 서버 설정 데이터(boards, prefixes)에서 받는다(CLAUDE.md 2).
 * 여기 있는 것은 그것을 어떻게 그릴지(그룹 이름·아이콘 글자·색)뿐이다.
 */

/**
 * 게시판 고정 유형(주제·광장·안내)은 없어졌다 — 메뉴의 그룹 이름·순서는 운영자가 메뉴 구성에서 정하고 서버가 `GET /api/menu` 로 준다(`UtilMenu`).
 * 여기 있는 이름(번역 키 `board.bundleLabel`)은 (1) 메뉴를 못 받았을 때의 기본 메뉴와 (2) 글쓰기 화면의 게시판 고르기 목록에서 **메뉴에 없는 게시판**(또는 메뉴를 못 받았을 때)의 묶음 제목만 쓴다.
 * 메뉴에 있는 게시판의 묶음 제목은 메뉴의 그룹 이름이다(`UtilMenu.boardPickGroups`).
 */

/** 게시판을 한 묶음으로 모은 것 — 메뉴 구성을 모를 때 쓰는 기본 묶음 */
export interface BoardBundle {
    label: string;
    boards: BoardView[];
}

/** 색 계열 — 말머리 태그·게시판 아이콘·홈 게시판 카드 로고가 같이 쓴다 */
export type Tone = 'blue' | 'purple' | 'green' | 'amber' | 'teal' | 'neutral';

const TONES: readonly Tone[] = ['blue', 'purple', 'green', 'amber', 'teal'];

/** 태그(말머리 표시)의 색 클래스 */
const TAG_CLASS: Record<Tone, string> = {
    blue: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
    purple: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300',
    green: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
    amber: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
    teal: 'bg-teal-100 text-teal-800 dark:bg-teal-950 dark:text-teal-300',
    neutral: 'bg-surface-100 text-surface-600 dark:bg-surface-800 dark:text-surface-300',
};

/** 작은 점(메뉴)의 색 클래스 */
const DOT_CLASS: Record<Tone, string> = {
    blue: 'bg-blue-500',
    purple: 'bg-purple-500',
    green: 'bg-green-500',
    amber: 'bg-amber-500',
    teal: 'bg-teal-500',
    neutral: 'bg-surface-400',
};

/** id 문자열로 고르는 색 — 같은 말머리는 언제나 같은 색이다(색을 서버에서 받지 않는다) */
function toneOfId(id: string): Tone {
    let hash = 0;
    for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
    return TONES[hash % TONES.length]!;
}

/** 표시색을 칠하는 CSS 스타일 한 벌 */
export type ColorStyle = Record<string, string>;

export default class UtilBoard {
    /**
     * 게시판을 "게시판" 한 묶음으로 모은다(서버가 준 순서 유지) — 메뉴를 못 받았을 때의 기본 메뉴와, 글쓰기 화면의 게시판 고르기 중 메뉴에 없는 게시판이 쓴다.
     * 게시판의 성격으로 나누지 않는다(고정 유형 폐지 — 게시판 응답에 성격 칸이 없다). 메뉴의 그룹 이름은 운영자가 메뉴 구성에서 정하며 `GET /api/menu` 로 온다.
     * `GET /api/boards` 는 일반 게시판만 싣는다(소모임 게시판은 게시판 목록·메뉴에 없다 — GRP-006, G-5 contract) — 거르지 않고 그대로 묶는다.
     * @param {Translate} t - 번역 함수(묶음 이름 `board.bundleLabel` 을 지금 언어로 읽는다).
     * @param {readonly BoardView[]} boards - 서버가 준 게시판 목록(순서는 그대로 유지한다).
     * @returns {BoardBundle | null} 한 묶음. 게시판이 없으면 null.
     */
    static bundleBoards(t: Translate, boards: readonly BoardView[]): BoardBundle | null {
        return boards.length > 0 ? { label: t('board.bundleLabel'), boards: [...boards] } : null;
    }

    /**
     * 태그(말머리 표시)의 색 클래스를 돌려줍니다.
     * @param {Tone} tone - 색 계열.
     * @returns {string} 배경·글자색 클래스 문자열(다크 모드 포함).
     */
    static tagClass(tone: Tone): string {
        return TAG_CLASS[tone];
    }

    /**
     * 메뉴 작은 점의 색 클래스를 돌려줍니다.
     * @param {Tone} tone - 색 계열.
     * @returns {string} 배경색 클래스 문자열.
     */
    static dotClass(tone: Tone): string {
        return DOT_CLASS[tone];
    }

    /**
     * 순서(0부터)로 고르는 색 — 홈 게시판 카드 가젯의 로고처럼 표시색이 없을 때 순서로 정하는 곳이 쓴다(메뉴 점은 쓰지 않는다)
     * @param {number} index - 순서(0부터). 범위를 벗어나거나 음수여도 돌려서 고른다.
     * @returns {Tone} 그 순서의 색 계열.
     */
    static toneAt(index: number): Tone {
        return TONES[((index % TONES.length) + TONES.length) % TONES.length]!;
    }

    /**
     * 말머리 색. 운영진 전용(공지)은 중립색이고 나머지는 id 로 갈린다
     * @param {{ id: string; staffOnly?: boolean }} prefix - 말머리(id 와 운영진 전용 여부만 본다).
     * @returns {Tone} 운영진 전용이면 `neutral`, 아니면 id 로 정해진 색 계열.
     */
    static prefixTone(prefix: Pick<PrefixView, 'id' | 'staffOnly'> | { id: string; staffOnly?: boolean }): Tone {
        return prefix.staffOnly ? 'neutral' : toneOfId(prefix.id);
    }

    /**
     * 운영자가 고른 표시색(프리셋 이름)을 읽는다 — 12색 이름이 아닌 값(빈 값·옛 응답·모르는 이름)은 null(색 없음). 이름 → 색 값 표는 `UtilColor.PRESETS` 한 곳에만 있다.
     * @param {unknown} color - 서버가 준 `color`.
     * @returns {string | null} 프리셋 이름 또는 null(색 없음).
     */
    static presetColor(color: unknown): string | null {
        return UtilColor.isPreset(color) ? (color as string) : null;
    }

    /**
     * 작은 점(메뉴·로고)에 칠할 배경 — 고른 표시색이 있을 때만. 없으면 undefined(메뉴는 점을 그리지 않는다).
     * @param {unknown} color - 게시판 표시색.
     * @returns {ColorStyle | undefined} 라이트·다크를 따르는 배경 스타일.
     */
    static dotStyle(color: unknown): ColorStyle | undefined {
        const pair = UtilColor.cssPair(UtilBoard.presetColor(color));
        return pair ? { background: pair } : undefined;
    }

    /**
     * 글자(게시판 이름)에 칠할 색 — 고른 표시색이 있을 때만.
     * @param {unknown} color - 게시판 표시색.
     * @returns {ColorStyle | undefined} 라이트·다크를 따르는 글자색 스타일.
     */
    static textStyle(color: unknown): ColorStyle | undefined {
        const pair = UtilColor.cssPair(UtilBoard.presetColor(color));
        return pair ? { color: pair } : undefined;
    }

    /**
     * 태그(말머리·게시판 이름 알약)에 칠할 색 — 같은 색 14% 바탕 + 그 바탕 위에서 4.5:1 이 되게 명도만 보정한 글자색(`UtilColor.tagText`, 색 값 자체는 그대로). 고른 색이 없거나 운영진 전용이면 undefined.
     * @param {unknown} color - 표시색.
     * @param {boolean} [staffOnly] - 운영진 전용 말머리는 색과 무관하게 회색이라 칠하지 않는다.
     * @returns {ColorStyle | undefined} 태그 스타일. 자동·회색이면 undefined(클래스로 그린다).
     */
    static tagStyle(color: unknown, staffOnly = false): ColorStyle | undefined {
        const preset = staffOnly ? null : UtilBoard.presetColor(color);
        const pair = UtilColor.cssPair(preset);
        const text = UtilColor.cssTagText(preset);
        return pair && text ? { '--tag-c': pair, color: text, background: 'color-mix(in srgb, var(--tag-c) 14%, var(--p-content-background))' } : undefined;
    }

    /**
     * 허브 목록의 네모 아이콘 글자 — 영문으로 시작하면 앞 두 글자(AI), 아니면 첫 글자(로·산·게)
     * @param {string} name - 게시판 이름.
     * @returns {string} 아이콘에 넣을 글자. 영문으로 시작하면 앞 한두 글자(대문자), 아니면 첫 글자, 비어 있으면 `?`.
     */
    static iconText(name: string): string {
        const ascii = /^[A-Za-z]{1,2}/.exec(name);
        return ascii ? ascii[0].toUpperCase() : (Array.from(name)[0] ?? '?');
    }

    /**
     * 썸네일 카드형으로 그리는 게시판인지 — 서버가 준 `layout` 설정값을 그대로 쓴다(slug 로 짐작하지 않는다)
     * @param {Pick<BoardView, 'layout'> | null | undefined} board - 게시판 정보. 없으면 목록형으로 본다.
     * @returns {boolean} `layout` 이 `CARD` 이면 true.
     */
    static usesCardLayout(board: Pick<BoardView, 'layout'> | null | undefined): boolean {
        return board?.layout === 'CARD';
    }

    /**
     * 갤러리형(사진·영상 썸네일 격자)으로 그리는 게시판인지 — 서버가 준 `layout` 설정값을 그대로 쓴다(slug 로 짐작하지 않는다). 홈의 갤러리 가젯은 이 게시판만 고를 수 있다.
     * @param {Pick<BoardView, 'layout'> | null | undefined} board - 게시판 정보. 없으면 목록형으로 본다.
     * @returns {boolean} `layout` 이 `GALLERY` 이면 true.
     */
    static usesGalleryLayout(board: Pick<BoardView, 'layout'> | null | undefined): boolean {
        return board?.layout === 'GALLERY';
    }

    /**
     * 게시판 위 말머리 필터 칩으로 보일 말머리. 공통 "공지"(운영진 전용 공통 말머리)는 뺀다 —
     * 공지는 고정 공지로 목록 위에 따로 나오고, 칩은 고유 말머리 + 질문·후기·잡담 으로 최대 7개다(features.md 2).
     * 공지 게시판 자신의 말머리(공지·업데이트·점검)는 공통이 아니라 그대로 칩이 된다.
     * @param {PrefixView[]} prefixes - 게시판의 말머리 목록.
     * @returns {PrefixView[]} 필터 칩으로 보일 말머리(운영진 전용 공통 말머리는 뺀다).
     */
    static chipPrefixes(prefixes: PrefixView[]): PrefixView[] {
        return prefixes.filter((p) => !(p.common && p.staffOnly));
    }

    /**
     * 이 게시판에서 이 사용자가 고를 수 있는 말머리 — 운영진 전용(공지)은 운영진·운영자에게만
     * @param {Pick<BoardView, 'prefixes'>} board - 게시판 정보(말머리 목록을 가진 것).
     * @param {boolean} isStaff - 운영진·운영자인가.
     * @returns {PrefixView[]} 이 사용자가 고를 수 있는 말머리. 운영진 전용은 운영진에게만 들어 있다.
     */
    static selectablePrefixes(board: Pick<BoardView, 'prefixes'>, isStaff: boolean): PrefixView[] {
        return board.prefixes.filter((p) => isStaff || !p.staffOnly);
    }

    // ── 에디터(공개 게시판 계약 「에디터 화면」) — 서버 유효값. 모르는 값·옛 응답(칸 없음)은 평문/허용 안 함으로 본다 ─

    /**
     * 글 에디터 수준 — `NONE` 이면 Textarea, 아니면 그 수준의 에디터.
     * @param {Pick<BoardView, 'postEditor'>} board - 게시판.
     * @returns {PostEditorLevel} 수준.
     */
    static postEditor(board: Pick<BoardView, 'postEditor'> | null | undefined): PostEditorLevel {
        const value = board?.postEditor;
        return value === 'SIMPLE' || value === 'ADVANCED' ? value : 'NONE';
    }

    /**
     * 댓글 에디터 수준 — `NONE` 이면 Textarea, `SIMPLE` 이면 간단 에디터.
     * @param {Pick<BoardView, 'commentEditor'>} board - 게시판.
     * @returns {CommentEditorLevel} 수준.
     */
    static commentEditor(board: Pick<BoardView, 'commentEditor'> | null | undefined): CommentEditorLevel {
        return board?.commentEditor === 'SIMPLE' ? 'SIMPLE' : 'NONE';
    }

    /** 이 게시판의 새 글·고친 글 본문 형식 — 에디터면 `HTML` */
    static postFormat(board: Pick<BoardView, 'postEditor'> | null | undefined): ContentFormat {
        return UtilBoard.postEditor(board) === 'NONE' ? 'TEXT' : 'HTML';
    }

    /** 이 게시판의 새 댓글·고친 댓글 본문 형식 */
    static commentFormat(board: Pick<BoardView, 'commentEditor'> | null | undefined): ContentFormat {
        return UtilBoard.commentEditor(board) === 'NONE' ? 'TEXT' : 'HTML';
    }

    /**
     * 에디터에서 새 이미지를 올릴 수 있는가 — 고급 에디터 + 게시판 허용 + 회원(비회원 글쓰기·비회원 글 고치기는 감춘다 — `POST /api/images` 는 회원만).
     * 감추는 것은 편의이고 막는 것은 서버다(`422 IMAGE_UPLOAD_NOT_ALLOWED`).
     * @param {Pick<BoardView, 'postEditor' | 'imageUploadAllowed'>} board - 게시판.
     * @param {boolean} guest - 비회원으로 쓰거나 비회원 글을 고친다.
     * @returns {boolean} 올릴 수 있으면 true.
     */
    static imageUploadOn(board: Pick<BoardView, 'postEditor' | 'imageUploadAllowed'> | null | undefined, guest: boolean): boolean {
        return !guest && UtilBoard.postEditor(board) === 'ADVANCED' && board?.imageUploadAllowed === true;
    }
}
