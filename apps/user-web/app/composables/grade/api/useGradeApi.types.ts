/**
 * 등급 안내·내 진행도 API 가 주고받는 모양 — 서버 `grade/dto/response/*`(`GradeRulesResponse`·`MyGradeResponse`)와 같다(2026-10-24 연결). 모양은 community `docs/api-grade.md`「공개」에서 로보얌 등급 규칙(운영자가 정하는 2~10단계, 조건은 가입 일수·글+댓글 수)으로 옮겼다.
 * 인기글 조건(`minPopular`·`POPULAR`)은 로보얌에 없어 뺐다. 등급 키는 고정 enum 이 아니라 등급 목록의 `gradeKey` 다. 계약: `docs/features/member-activity-contract.md`.
 */

/** 자동 승급 상태 — 켜짐 · 정지 중이라 보류 · 운영자가 등급을 직접 바꿔 고정(정지가 우선) */
export type AutoPromotion = 'ACTIVE' | 'PAUSED_SANCTION' | 'PAUSED_ADMIN';

/** 승급 조건 종류 — 가입 일수 · 글+댓글 수 */
export type RequirementKind = 'DAYS' | 'ACTIVITY' | 'LEVEL';

/**
 * 등급 도트 캐릭터(grade-level-contract 3절) — 등급마다 운영자가 고른다(기본 = 서열 순). 그림은 화면 코드의 픽셀 지도(`UtilGradeSprite`).
 * 새싹 · 볼트 · 안테나 · 배터리 · 톱니 · 하트 · 번개 · 별 · 왕관 · 전설 · 신화
 */
export type GradeSpriteKey = 'SPROUT' | 'BOLT' | 'ANTENNA' | 'BATTERY' | 'GEAR' | 'HEART' | 'LIGHTNING' | 'STAR' | 'CROWN' | 'LEGEND' | 'MYTH';

/** 사이트 정보(`GET /api/site/info`)의 등급 한 줄 — 화면이 작성자 `grade` 키로 도트·이름을 찾는다 */
export interface SiteGrade {
    key: string;
    label: string;
    /** 서열(1부터) */
    level: number;
    sprite: GradeSpriteKey;
}

export interface GradeRef {
    /** 등급 키(`gradeKey`) */
    code: string;
    /** 운영자가 입력한 등급 이름 그대로 — 영어 이름 칸이 없어 언어와 상관없이 같다 */
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
    /** 레벨(Lv.1~, 끝 없음 — grade-level-contract 1절). 서버가 아직 안 주면(옛 응답) 없음 — 화면은 레벨 표시를 숨긴다 */
    level?: number | null;
    /** 누적 경험치(적립된 활동 포인트 합 — 쓰거나 소멸해도 줄지 않는다) */
    xp?: number | null;
    /** 이 레벨이 시작하는 누적 XP */
    levelXp?: number | null;
    /** 다음 레벨이 되는 누적 XP */
    nextLevelXp?: number | null;
}

/** 등급 하나의 승급 조건 — 첫 등급은 0·0(가입 즉시) */
export interface GradeRule extends GradeRef {
    minDays: number;
    minActivity: number;
    /** 최소 레벨(grade-level-contract 2절) — 서버가 아직 안 주면(옛 응답) 없음, 1 이하면 조건 글자에 넣지 않는다 */
    minLevel?: number | null;
}

/** `GET /api/grade-rules` — **비회원도** 본다. 낮은 등급부터(등급 서열 순) */
export interface GradeRules {
    rules: GradeRule[];
}
