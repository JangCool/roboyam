// MOCK: 게시판 목록 API(`GET /api/boards`)의 메모리 목업 — `useBoardApi.ts` 가 이 파일의 함수를 부른다.
// 값은 ROBOYAM 예시(AI·로봇) 기본 게시판 구성(`install-presets/roboyam.json`)과 맞춘 것이다(게시판 이름·말머리). 서버가 붙으면 `board/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   boards-fail       게시판 목록 요청 실패 (`menu-fail` 과 함께 쓰면 메뉴 오류 상태)

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardView } from '~/composables/board/api/useBoardApi.types';

interface MockBoardSeed {
    slug: string;
    name: string;
    /** 영문명 — 일부러 일부 게시판에는 없다(영어 화면에서 한국어 이름으로 되돌아가는 모양을 보려고) */
    nameEn?: string;
    color: string | null;
    investmentNotice?: boolean;
    staffOnlyWrite?: boolean;
    prefixes: string[];
}

const BOARD_SEEDS: MockBoardSeed[] = [
    { slug: 'ai', name: 'AI·테크', nameEn: 'AI & Tech', color: 'BLUE', prefixes: ['뉴스', '해설', '툴', '팁', '질문', '후기', '잡담', '공지'] },
    { slug: 'robot', name: '로봇', nameEn: 'Robots', color: 'TEAL', prefixes: ['뉴스', '영상', '해설', '제품', '질문', '후기', '잡담', '공지'] },
    { slug: 'stock', name: '산업·주식', nameEn: 'Industry & Stocks', color: 'AMBER', investmentNotice: true, prefixes: ['산업해설', '실적', '공급망', '이슈', '질문', '공지'] },
    { slug: 'game', name: '게임', color: 'PURPLE', prefixes: ['소식', '공략', '로봇렌즈', '파티모집', '질문', '공지'] },
    { slug: 'sports', name: '스포츠', color: 'GREEN', prefixes: ['소식', '경기', '로봇렌즈', '직관', '질문', '공지'] },
    { slug: 'free', name: '자유게시판', nameEn: 'Free Board', color: null, prefixes: ['잡담', '유머', '정보', '일상', '질문', '후기', '공지'] },
    { slug: 'notice', name: '공지', color: null, staffOnlyWrite: true, prefixes: ['공지', '업데이트', '점검'] },
];

/** 메뉴 목업(`menu-mock.ts`)도 같은 게시판을 쓴다 */
export const MOCK_BOARDS: BoardView[] = BOARD_SEEDS.map((b, index) => ({
    id: `mock-board-${index + 1}`,
    slug: b.slug,
    name: b.name,
    nameEn: b.nameEn ?? null,
    description: null,
    investmentNotice: b.investmentNotice ?? false,
    staffOnlyWrite: b.staffOnlyWrite ?? false,
    layout: 'LIST',
    prefixes: b.prefixes.map((name, i) => ({ id: `mock-prefix-${index + 1}-${i + 1}`, name, staffOnly: name === '공지', common: ['질문', '후기', '잡담', '공지'].includes(name) })),
    color: b.color,
}));

export async function mockBoards(signal?: AbortSignal): Promise<BoardView[]> {
    await mockDelay(signal);
    if (mockFlags().has('boards-fail')) throw MOCK_ERRORS.internal();
    return MOCK_BOARDS;
}
