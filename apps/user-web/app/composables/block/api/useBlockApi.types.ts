/**
 * 차단(닉네임·단어) API 가 주고받는 모양 — 서버(api)도 이 모양으로 준다. 모양은 community `docs/api-block-scrap.md` 「닉네임 차단」「차단 단어」 그대로다
 * (닉네임 이미지 칸 `nicknameImage` 는 로보얌 작성자 표시에 없어 뺐다). 계약 후보: `docs/features/member-social-contract.md`.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { Role } from '~/composables/auth/api/useAuthApi.types';

/** 글·댓글이 접히는 까닭 — `USER` 차단한 회원 · `WORD` 차단한 단어(둘 다면 `USER`) */
export type BlockedBy = 'USER' | 'WORD';

/** `PUT /api/users/{id}/block` 의 `200` — 멱등(이미 차단했으면 처음 차단한 시각 그대로) */
export interface BlockResult {
    blocked: boolean;
    blockedAt: string;
}

/** 내가 차단한 회원 한 줄 — 탈퇴한 회원은 빠진다. 닉네임은 읽을 때의 현재 값 */
export interface BlockedUserView {
    id: string;
    nickname: string;
    /** 등급 키(`gradeKey`) */
    grade: string;
    gradeLabel: string;
    /** 차단한 뒤 운영진이 되었을 수 있다 — 그러면 공식 뱃지 */
    role: Role;
    blockedAt: string;
}

/** `GET /api/me/blocks/users?size&cursor` — 차단한 시각이 최근인 것이 위. `total` 은 탈퇴한 회원을 뺀 수 */
export interface BlockedUserList {
    total: number;
    items: BlockedUserView[];
    hasNext: boolean;
    nextCursor: string | null;
}

/** 차단 단어 한 줄 */
export interface BlockedWordView {
    id: string;
    word: string;
    createdAt: string;
}

/** `GET /api/me/blocks/words` — 최근에 더한 것이 위, 최대 `limit` 개라 쪽을 나누지 않는다. 길이·개수는 화면이 미리 검사할 값(판정은 서버) */
export interface BlockedWordList {
    limit: number;
    minLength: number;
    maxLength: number;
    items: BlockedWordView[];
}

/** 목록 쿼리 — `size` 1~50(기본 20), `cursor` 는 앞 응답의 `nextCursor` */
export interface BlockPageQuery {
    size?: number;
    cursor?: string;
}
