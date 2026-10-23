// MOCK: 소통 목업들(쪽지·차단·이용자 메모)과 공개 프로필 목업이 같이 쓰는 「가짜 회원 명부」 — 서버에 회원을 찾는 API 가 없어 화면이 고를 상대를 여기서 만든다.
// 명부에 없는 ID 는 실제 게시판 글(`board-feed-mock.ts`)의 작성자 이름·등급을 빌리고, 그것도 없으면 「회원 {ID 끝 4자리}」·새싹으로 그린다(공개 프로필 목업과 같은 규칙).
// 서버가 붙으면 쓰는 목업이 하나도 없을 때 이 파일도 지운다.
//
// 가정(계약 `member-social-contract.md` 「목업 가정」):
//   · 명부 회원 8명 — 쪽지를 받지 않는 회원(`acceptDm=false`) 1명, 운영진(`STAFF`, 차단할 수 없음) 1명이 섞여 있다.
//   · ID 는 Snowflake 모양의 18자리 문자열이다(숫자로 바꾸지 않는다).

import type { Role } from '~/composables/auth/api/useAuthApi.types';
import type { BoardFeedSource } from '~/composables/common/mock/board-feed-mock';
import { mockBoardFeed } from '~/composables/common/mock/board-feed-mock';

/** 목업 회원 한 명 — 작성자 표시(닉네임·등급·역할)와 쪽지 받기 여부 */
export interface MockMember {
    id: string;
    nickname: string;
    /** 등급 키(`gradeKey`) */
    grade: string;
    gradeLabel: string;
    role: Role;
    /** 쪽지 받기(수신 설정) */
    acceptDm: boolean;
}

export const MOCK_MEMBERS: readonly MockMember[] = [
    { id: '900000000000000101', nickname: '로봇덕후', grade: 'LOYAL', gradeLabel: '단골', role: 'USER', acceptDm: true },
    { id: '900000000000000102', nickname: '코딩하는곰', grade: 'REGULAR', gradeLabel: '일반', role: 'USER', acceptDm: true },
    { id: '900000000000000103', nickname: '휴머노이드', grade: 'VETERAN', gradeLabel: '베테랑', role: 'USER', acceptDm: true },
    { id: '900000000000000104', nickname: '조용한관찰자', grade: 'SEEDLING', gradeLabel: '새싹', role: 'USER', acceptDm: false },
    { id: '900000000000000105', nickname: '로봇팔장인', grade: 'LOYAL', gradeLabel: '단골', role: 'USER', acceptDm: true },
    { id: '900000000000000106', nickname: '센서마니아', grade: 'REGULAR', gradeLabel: '일반', role: 'USER', acceptDm: true },
    { id: '900000000000000107', nickname: '운영지기', grade: 'VETERAN', gradeLabel: '베테랑', role: 'STAFF', acceptDm: true },
    { id: '900000000000000108', nickname: '로봇청소기', grade: 'SEEDLING', gradeLabel: '새싹', role: 'USER', acceptDm: true },
];

/** 회원 ID 모양인가 — Snowflake 십진 문자열 */
export function mockValidMemberId(id: string): boolean {
    return /^[1-9]\d{0,19}$/.test(id);
}

/** 명부에서 찾는다 */
export function mockDirectoryMember(id: string | null | undefined): MockMember | null {
    return MOCK_MEMBERS.find((m) => m.id === id) ?? null;
}

/** 실제 게시판 글의 회원 작성자들(ID → 이름·등급) — 글을 못 모으면 빈 목록 */
async function feedAuthors(source: BoardFeedSource | null): Promise<MockMember[]> {
    if (!source) return [];
    try {
        const rows = (await mockBoardFeed(source)).rows;
        const seen = new Map<string, MockMember>();
        for (const row of rows) {
            const a = row.author;
            if (a.kind !== 'MEMBER' || !a.memberId || seen.has(a.memberId) || mockDirectoryMember(a.memberId)) continue;
            seen.set(a.memberId, { id: a.memberId, nickname: a.name, grade: a.grade ?? 'SEEDLING', gradeLabel: a.gradeLabel ?? '새싹', role: 'USER', acceptDm: true });
        }
        return [...seen.values()];
    } catch {
        return [];
    }
}

/**
 * 회원 한 명을 찾는다 — 명부 → 실제 글의 작성자 → 「회원 {끝 4자리}」. 형식이 틀린 ID 는 null(서버의 `404 PROFILE_NOT_FOUND`).
 * @param source 실제 공개 게시판 API(없으면 명부만 본다)
 * @param id 회원 ID
 */
export async function mockMemberOf(source: BoardFeedSource | null, id: string): Promise<MockMember | null> {
    if (!mockValidMemberId(id)) return null;
    const known = mockDirectoryMember(id);
    if (known) return known;
    const fromFeed = (await feedAuthors(source)).find((m) => m.id === id);
    return fromFeed ?? { id, nickname: `회원 ${id.slice(-4)}`, grade: 'SEEDLING', gradeLabel: '새싹', role: 'USER', acceptDm: true };
}

/**
 * 닉네임에 `q` 가 들어 있는 회원 — 앞부분이 같은 것이 먼저, 그다음 닉네임 순(서버 `GET /api/dm/recipients` 와 같은 줄 세우기).
 * @param source 실제 공개 게시판 API
 * @param q 다듬은 검색어
 */
export async function mockSearchMembers(source: BoardFeedSource | null, q: string): Promise<MockMember[]> {
    const needle = q.toLowerCase();
    const all = [...MOCK_MEMBERS, ...(await feedAuthors(source))];
    return all
        .filter((m) => m.nickname.toLowerCase().includes(needle))
        .sort((a, b) => {
            const pa = a.nickname.toLowerCase().startsWith(needle) ? 0 : 1;
            const pb = b.nickname.toLowerCase().startsWith(needle) ? 0 : 1;
            return pa - pb || a.nickname.localeCompare(b.nickname, 'ko');
        });
}
