/**
 * 등급 API 가 주고받는 모양 — 후보 계약이고 확정 전이다. 칸 이름·오류 코드는 community `docs/api-grade.md` 의 "승급 기준"(`GET·PUT` 등급 기준 API)을 바탕으로 목록 기반으로 넓혔다.
 *
 * **등급은 4단계 고정이 아니라 순서 있는 목록이다**(정책 확정 2026-10-21 — 지금은 4단계지만 늘 수 있다). 등급 설정이 원천이고, 등급 역할(역할 관리의 회원 역할 > 등급)은 이 목록에서 자동으로 만들어진다.
 *  · `gradeKey`: 서버가 주는 **불변 식별자**(시드 SEEDLING·REGULAR·LOYAL·VETERAN, 새로 만든 등급은 서버가 `GRADE_5` … 형태로 발급한다 — 가정). 이름을 바꿔도, 순서를 옮겨도 그대로다. 역할 아이디는 `ROLE_GRADE_<gradeKey>` 로 파생한다.
 *  · `level`: 서열(1..N). 목록의 순서가 곧 서열이다. **첫 등급(level 1)은 기본 등급**이라 승급 조건이 없고(0·0), 지우거나 옮길 수 없다.
 *
 * ⚠ community 의 `minPopular`(인기글·출하 완료글 수)는 roboyam 에 같은 개념이 아직 없어 넣지 않았다 — 승급 조건 항목은 확정 전이다.
 */

/** 등급 식별자 — 서버가 발급하는 불변 문자열 */
export type GradeKey = string;

/**
 * 등급 뱃지 색 이름 — 고정 목록(자유 입력 금지). 색 값은 화면이 PrimeVue 색 토큰(`--p-<이름>-<단계>`)으로 푼다.
 * 서버는 이 목록 밖의 값을 `400 VALIDATION`(`fields.grades[i].badgeColor`)으로 거절한다. 목록의 범위는 확정 전이다.
 */
export type GradeBadgeColor = 'gray' | 'blue' | 'indigo' | 'violet' | 'pink' | 'red' | 'orange' | 'amber' | 'green' | 'teal' | 'cyan';

/** 등급 하나 — 이름·서열·승급 조건·뱃지 표시·소속 회원 수 */
export interface Grade {
    gradeKey: GradeKey;
    /** 서열(1..N) — 목록 순서와 같다. 1 은 기본 등급 */
    level: number;
    /** 표시 이름(고유, 최대 8자 — community `label varchar(8)`) */
    label: string;
    /** 가입 후 일수. 기본 등급은 0 */
    minDays: number;
    /** 글·댓글 합계. 기본 등급은 0 */
    minActivity: number;
    /** 뱃지 색 이름 */
    badgeColor: GradeBadgeColor;
    /** 뱃지 아이콘(PrimeIcons 클래스, 예: `pi pi-star`). 아이콘 없음이면 `null`. 허용 목록 밖이면 서버가 `fields.grades[i].badgeIcon` 으로 거절한다 */
    badgeIcon: string | null;
    /** 이 등급인 회원 수 — 등급을 지우면 이 회원들이 바로 아래 등급으로 이동한다 */
    memberCount: number;
}

/**
 * 운영자가 등급을 **수동으로 올린** 회원의 이후 동작 — 값은 전역 하나지만 **수동으로 올린 회원에게만** 적용된다(정책 확정 2026-10-21, 정확한 의미는 가정).
 * 수동으로 올린 적 없는 회원은 이 값과 상관없이 늘 일반 자동 승급 규칙(조건 충족 즉시/배치)을 따른다.
 *  · `CONTINUE_AUTO` 계속 자동 승급: 수동으로 올린 회원도 이후 더 높은 등급의 조건을 채우면 자동으로 올린다(기본값 — 가정).
 *  · `STOP_AUTO` 자동 승급 중지: 수동으로 올린 회원은 이후 어떤 자동 승급 조치도 하지 않는다.
 */
export type GradeManualPolicy = 'CONTINUE_AUTO' | 'STOP_AUTO';

/** `GET /api/admin/grades` — 등급은 서열(level) 순서로 온다 */
export interface GradesResponse {
    grades: Grade[];
    manualPolicy: GradeManualPolicy;
}

/**
 * 저장 요청의 한 줄 — 목록 **순서가 곧 서열**이다. 이미 있는 등급은 `gradeKey` 를 보내고, 새 등급은 `gradeKey` 를 빼서 보낸다(서버가 발급한다).
 * 첫 줄은 기본 등급이라 `gradeKey` 가 그대로여야 하고 승급 조건은 보내지 않는다(보내도 무시한다). 요청에 없는 기존 등급은 지우는 것이고, 그 등급의 회원은 바로 아래 등급으로 이동한다.
 */
export interface GradeRequest {
    gradeKey?: GradeKey;
    label: string;
    minDays?: number;
    minActivity?: number;
    badgeColor: GradeBadgeColor;
    badgeIcon: string | null;
}

/**
 * `PUT /api/admin/grades` — 전체 저장: 순서·추가·삭제·수정을 한 번에 보내고 서버가 diff 한다(하나라도 틀리면 아무것도 저장하지 않는다).
 * 개별 `POST/PATCH/DELETE` 대신 전체 저장을 제안한다 — 저장 줄(한 번에 저장·이탈 가드)과 맞고, 순서·승급 조건의 "위 등급일수록 같거나 커야 한다"는 규칙이 여러 줄에 걸쳐 있어 줄 하나씩 저장하면 중간 상태가 규칙을 어길 수 있다.
 */
export interface GradesSaveRequest {
    grades: GradeRequest[];
    manualPolicy: GradeManualPolicy;
}

/** `POST /api/admin/grades/preview` 요청 — 저장 요청과 같은 모양(저장하지 않고 소급 영향만 센다) */
export type GradePreviewRequest = GradesSaveRequest;

/** 미리보기의 등급 한 줄 — 이 기준으로 저장하면 지금 이 등급이 되는 회원 수. 새 등급은 `gradeKey` 가 `null` 이다 */
export interface GradePreviewItem {
    gradeKey: GradeKey | null;
    label: string;
    count: number;
}

/**
 * 등급을 지우면 그 등급의 회원이 **바로 아래 등급**으로 이동한다(정책 확정). 미리보기의 이동 한 줄 — 이름은 요청을 보낼 때 기준이다.
 * 서버 동작 가정: 삭제와 회원 이동은 한 트랜잭션이고, 이동한 회원은 자동 승급 판정 대상에 계속 포함되며(이동된 등급 기준으로 다시 올라갈 수 있다), 이동 이력은 등급 변경 이력에 사유 `등급 삭제`로 남는다(이력 화면은 후속).
 */
export interface GradeMoveItem {
    fromGradeKey: GradeKey;
    fromLabel: string;
    /** 이동할 등급. 바로 아래 등급이 이번에 새로 만드는 등급이면 `null` */
    toGradeKey: GradeKey | null;
    toLabel: string;
    count: number;
}

/**
 * 이 기준으로 저장하면 지금 일어나는 일 — 소급해서 승급될(새 등급이 생기면 그 등급이 될) 회원 수(`items`·`total`)와, 지우는 등급에서 아래 등급으로 이동할 회원 수(`willMove`).
 * 승급 인원(`total`)에 이동하는 회원은 포함하지 않는다.
 */
export interface GradePreviewResponse {
    items: GradePreviewItem[];
    total: number;
    willMove: GradeMoveItem[];
}
