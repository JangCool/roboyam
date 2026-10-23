/**
 * 이용자 메모 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 모양은 community `docs/api-block-scrap.md` 「이용자 메모」 그대로다.
 * 메모는 나에게만 보인다 — 다른 응답에서는 작성자 표시의 `memo`·공개 프로필의 `memo` 로만 온다. 계약 후보: `docs/features/member-social-contract.md`.
 */
import type { Role } from '~/composables/auth/api/useAuthApi.types';

/** `PUT /api/users/{id}/memo` 의 `200` — 저장된 메모. 빈 메모를 보냈으면(지웠으면) null */
export interface MemoResult {
    memo: string | null;
}

/** 내가 남긴 메모 한 줄 — `id` 는 **메모를 남긴 상대 회원의 ID**(고치기·지우기에 그대로 쓴다) */
export interface MemoView {
    id: string;
    nickname: string;
    grade: string;
    gradeLabel: string;
    role: Role;
    memo: string;
    updatedAt: string;
}

/** `GET /api/me/memos?size&cursor` — 처음 남긴 것이 최근인 순서(고쳐도 자리가 바뀌지 않는다). `total` 은 탈퇴한 회원을 뺀 수 */
export interface MemoList {
    total: number;
    items: MemoView[];
    hasNext: boolean;
    nextCursor: string | null;
}
