/**
 * 등급 안내·내 진행도 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 모양은 community `docs/api-grade.md`「공개」에서 로보얌 등급 규칙(운영자가 정하는 2~10단계, 조건은 가입 일수·글+댓글 수)으로 옮겼다.
 * 인기글 조건(`minPopular`·`POPULAR`)은 로보얌에 없어 뺐다. 등급 키는 고정 enum 이 아니라 등급 목록의 `gradeKey` 다. 계약 후보: `docs/features/member-activity-contract.md`.
 */

/** 자동 승급 상태 — 켜짐 · 정지 중이라 보류 · 운영자가 등급을 직접 바꿔 고정(정지가 우선) */
export type AutoPromotion = 'ACTIVE' | 'PAUSED_SANCTION' | 'PAUSED_ADMIN';

/** 승급 조건 종류 — 가입 일수 · 글+댓글 수 */
export type RequirementKind = 'DAYS' | 'ACTIVITY';

export interface GradeRef {
    /** 등급 키(`gradeKey`) */
    code: string;
    label: string;
}

/** 다음 등급의 조건 하나 — `current` 는 상한 없는 실제 값(화면이 막대를 필요 값에서 자른다). 기준이 0 인 조건은 싣지 않는다 */
export interface GradeRequirement {
    kind: RequirementKind | (string & {});
    current: number;
    required: number;
    met: boolean;
}

export interface NextGrade extends GradeRef {
    requirements: GradeRequirement[];
}

/** `GET /api/me/grade` — 로그인 필요(정지 중·이메일 인증 전에도 읽는다, 비로그인 `401`). `next` 는 가장 높은 등급이면 null */
export interface MyGrade {
    grade: GradeRef;
    autoPromotion: AutoPromotion;
    next: NextGrade | null;
}

/** 등급 하나의 승급 조건 — 첫 등급은 0·0(가입 즉시) */
export interface GradeRule extends GradeRef {
    minDays: number;
    minActivity: number;
}

/** `GET /api/grade-rules` — **비회원도** 본다. 낮은 등급부터(등급 서열 순) */
export interface GradeRules {
    rules: GradeRule[];
}
