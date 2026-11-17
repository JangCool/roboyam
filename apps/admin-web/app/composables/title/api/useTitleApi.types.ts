/**
 * 회원 칭호 관리(관리자) API 가 주고받는 모양 — 계약 `docs/features/member-title-contract.md` 「구현 결정」 3·7·「서버 구현 결과」와
 * admin 서버 DTO(`server/admin/.../title/dto` — `AdminTitleView`·`AdminTitleRequest`·`AdminTitleGrantView`·`AdminTitleApplicationView`·
 * `AdminMonthlyCandidatesView`·`AdminTitleSettingsView`, shared `TitleBadgeView`) 칸 이름 그대로다(2026-11-17 서버 코드와 대조).
 * 고치기 `PUT /api/admin/titles/{key}`(키는 주소), 만들기의 `key` 는 선택(비우면 서버가 발급, 겹치면 409 `TITLE_KEY_TAKEN`), 신청 목록은 `{ items }`.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { TitleTier } from '#layers/member-title/utils/util-title-icon';

/** 칩 색 — 보라 = 수여·심사, 초록 = 자동 */
export type TitleChipColor = 'PURPLE' | 'GREEN';

/** 받는 방법 — 자동 · 신청 심사 · 운영자 수여 */
export type TitleMethod = 'AUTO' | 'APPLY' | 'AWARD';

/** 심사하는 사람 — 운영자만 · 운영자 + 연결 게시판 매니저 */
export type TitleReviewer = 'OPERATOR' | 'OPERATOR_AND_BOARD_MANAGER';

/** 신청 상태 */
export type TitleApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 신청 목록 상태 거르기 — 서버 기본은 PENDING, `ALL` = 전부 */
export type TitleApplicationStatusFilter = TitleApplicationStatus | 'ALL';

/** 받은 길 — 자동 · 운영자 수여 · 신청 승인(서버 `TitleSource`) */
export type TitleSource = 'AUTO' | 'AWARD' | 'APPLICATION';

/** 준·심사한 사람 종류 — 운영자 · 게시판 매니저(회원). 자동이면 null */
export type TitleActorKind = 'OPERATOR' | 'BOARD_MANAGER';

/** 조건 종류(서버 `TitleConditionType`) — 모두 채우면 받는다. 등급 이상 · 글 수(게시판) · 인기글 진입 일수 · 출석 일수 · 신고로 숨겨진 글 없음 */
export type TitleConditionType = 'GRADE_AT_LEAST' | 'POSTS' | 'POPULAR_DAYS' | 'ATTENDANCE_DAYS' | 'NO_REPORT_HIDDEN' | 'PURCHASE_PROOFS';

/** 단계 칭호가 세는 것(서버 `TitleMetric`) — 인기글 진입 일수 · 출석 일수 · 글 수 · 체험단 약속 지킴 */
export type TitleMetric = 'POPULAR_DAYS' | 'ATTENDANCE_DAYS' | 'POSTS' | 'TRIAL_KEPT';

/** 칭호 표시 한 벌(서버 `TitleBadgeView`) — 받은 줄·신청 줄의 `title`, 화면이 칩을 그리는 모양 */
export interface TitleLook {
    key: string;
    /** 표시 이름(이달의 작가는 「11월의 작가」) */
    label: string;
    /** 영어 표시 이름(없으면 null — 화면은 `label` 로 되돌린다) */
    labelEn: string | null;
    icon: string;
    tier: TitleTier | null;
    chip: TitleChipColor;
}

/**
 * 조건 하나(요청 `AdminTitleRequest.Condition` · 응답 `AdminTitleView.ConditionView`). `value` = 세는 조건의 문턱(1 이상, 그 밖 null) ·
 * `boardId`/`boardSlug` = `POSTS` 의 게시판(둘 다 없으면 모든 게시판 — 기본 칭호의 처음 값은 `boardSlug`, 받은 값을 그대로 돌려 보내면 된다) · `gradeKey` = `GRADE_AT_LEAST` 의 등급 키.
 * 응답에는 서버가 요청 언어로 만든 한 줄 `label` 이 더 붙는다.
 */
export interface TitleCondition {
    type: TitleConditionType;
    value: number | null;
    boardId: string | null;
    boardSlug: string | null;
    gradeKey: string | null;
    /** 응답만 — 조건 한 줄(요청 언어) */
    label?: string;
}

/** 칭호의 고칠 수 있는 칸(서버 `TitleSpec` 이름 그대로) — 줄·요청이 같이 쓴다 */
export interface AdminTitleSpec {
    /** 이름(공백 뺀 6자까지, 등급 이름과 겹치면 400, 다른 칭호와 겹치면 409 `TITLE_NAME_TAKEN`) */
    name: string;
    nameEn: string | null;
    icon: string;
    chip: TitleChipColor;
    method: TitleMethod;
    reviewer: TitleReviewer;
    /** 연결 게시판(심사자가 게시판 매니저를 포함할 때) */
    boardId: string | null;
    conditions: TitleCondition[];
    /** 단계가 세는 것 — null 이면 단계 없음(문턱 셋도 null) */
    tierMetric: TitleMetric | null;
    tierBronze: number | null;
    tierSilver: number | null;
    tierGold: number | null;
    /** 혜택 표시 — 체험단 먼저 신청 · 매거진 첫 화면 후보 · 한정 아바타 테두리(기능 쪽이 칭호를 읽어 판단한다) */
    benefitTrialPriority: boolean;
    benefitMagazine: boolean;
    benefitAvatarFrame: boolean;
    /** 휴면 — 6개월 활동이 없으면 숨김(다시 쓰면 돌아옴) */
    dormantHide: boolean;
    enabled: boolean;
    sortOrder: number;
}

/** `GET /api/admin/titles` 한 줄(`AdminTitleView`, `title.read`) — 꺼진 칭호도 나온다 */
export interface AdminTitle extends AdminTitleSpec {
    key: string;
    /** 연결 게시판 이름 — 없으면 null */
    boardName: string | null;
    /** 기본 칭호(설치 때 들어간 7개 — 받는 방법을 바꿀 수 없다, 409 `TITLE_BUILTIN_LOCKED`) */
    builtin: boolean;
    /** 가진 회원 수(탈퇴 빼고) */
    holders: number;
    /** 받는 방법 한 줄(요청 언어 — 도감과 같은 글) */
    howTo: string;
    updatedAt: string | null;
}

/**
 * `POST /api/admin/titles`(만들기) · `PUT /api/admin/titles/{key}`(고치기)(`AdminTitleRequest`, `title.manage`).
 * 검사 오류는 칸 경로 키(`name`·`conditions[0].value`·`tierBronze` …)로 온다. `retroactive` = 저장 뒤 기존 활동으로 소급 지급(자동 칭호만 — false 면 안 함).
 */
export interface AdminTitleRequest extends AdminTitleSpec {
    /** 만들기만 — 칭호 키(소문자·숫자·하이픈 2~32자, 비우면 서버가 발급). 고치기는 주소의 키를 쓴다 */
    key?: string | null;
    retroactive: boolean;
}

/** `GET /api/admin/titles/settings`(`AdminTitleSettingsView`) — 이달의 작가 한 달 최대 인원(1~20) · 명예 작가 횟수(1~24) · 거절 뒤 다시 신청까지 일수(1~365) */
export interface TitleSettings {
    monthlyMax: number;
    honoraryCount: number;
    reapplyDays: number;
    updatedAt: string | null;
    /** 저장 응답만 의미 — 기준을 내려 이번에 소급해서 준 명예 작가 수(조회는 0) */
    honoraryGranted: number;
}

/** `PUT /api/admin/titles/settings`(`AdminTitleSettingsRequest`) */
export interface TitleSettingsRequest {
    monthlyMax: number;
    honoraryCount: number;
    reapplyDays: number;
}

/** 줄에 붙는 회원(`AdminTitleGrantView.MemberView`) — `memberNo` 는 회원 관리의 회원번호 */
export interface TitleMember {
    id: string;
    nickname: string | null;
    memberNo: string;
}

/** `POST /api/admin/titles/{key}/grants`(`AdminTitleGrantRequest`) — 기간(이달의 작가 `YYYY-MM`, 올해의 작가 `YYYY`) · 단계(없으면 동) · 사유(선택, 500자) */
export interface TitleGrantRequest {
    memberId: string;
    period?: string | null;
    tier?: TitleTier | null;
    reason: string;
}

/** 받은 줄 하나(`AdminTitleGrantView` — 주고 뺀 기록 · 수여 결과 · 회원 칭호 · 이달의 작가 「이미 받은 회원」). 회수해도 줄은 남는다 */
export interface TitleGrantRow {
    /** 받은 줄 ID(회수 `DELETE …/grants/{grantId}`) */
    grantId: string;
    title: TitleLook;
    member: TitleMember;
    /** `YYYY-MM`·`YYYY`, 기간 없는 칭호는 null */
    period: string | null;
    source: TitleSource;
    grantedAt: string;
    /** 준 사람 이름 — 운영자 이름, 게시판 매니저 승인이면 그 회원 닉네임, 자동이면 null */
    grantedBy: string | null;
    grantedByKind: TitleActorKind | null;
    reason: string | null;
    revokedAt: string | null;
    /** 회수한 운영자 이름 */
    revokedBy: string | null;
    revokeReason: string | null;
    /** 회수되지 않았나 */
    active: boolean;
    /** 이 수여(이달의 작가)로 명예 작가가 따라왔으면 그 줄 */
    honorary: TitleGrantRow | null;
}

/** `GET /api/admin/titles/grants?key&memberId&page&size`(`AdminTitleGrantPageView`) — page 는 0부터, size 기본 20·최대 100 */
export interface TitleGrantPage {
    items: TitleGrantRow[];
    page: number;
    size: number;
    total: number;
}

export interface TitleGrantQuery {
    key?: string;
    memberId?: string;
    page: number;
    size: number;
}

/** 신청 줄의 조건 한 줄(`AdminTitleApplicationView.ConditionView`) */
export interface TitleApplicationCondition {
    type: string;
    label: string;
    met: boolean;
}

/** 칭호 신청 한 줄(`AdminTitleApplicationView`) */
export interface AdminTitleApplication {
    id: string;
    title: TitleLook;
    member: TitleMember;
    status: TitleApplicationStatus;
    /** 신청자의 조건별 채움 — 대기 중인 신청만 지금 기준(처리된 신청은 빈 배열) */
    conditions: TitleApplicationCondition[];
    /** 연결 게시판(게시판 매니저도 심사하는 칭호) — 없으면 null */
    boardName: string | null;
    /** 거절 사유 · 승인 메모 */
    reason: string | null;
    /** 거절 뒤 다시 신청할 수 있는 때 */
    reapplyAt: string | null;
    createdAt: string;
    reviewedAt: string | null;
    /** 심사한 사람(운영자 이름 · 게시판 매니저 닉네임) — 대기면 null */
    reviewedBy: string | null;
    reviewedByKind: TitleActorKind | null;
}

/** `GET /api/admin/titles/applications?status&key`(`AdminTitleApplicationListView`) — 최신 순 200개까지 */
export interface AdminTitleApplicationList {
    items: AdminTitleApplication[];
}

/** 심사·회수 사유(`AdminTitleReasonRequest`) */
export interface TitleReasonRequest {
    reason: string;
}

/** 이달의 작가 후보의 글 한 편 — `featured` = 그 달 우수 글 · `manual` = 「후보에 넣기」로 넣음(둘 다일 수 있다) */
export interface MonthlyCandidatePost {
    id: string;
    title: string;
    featured: boolean;
    manual: boolean;
}

/** 후보 한 명(`AdminMonthlyCandidatesView.Candidate`) — `awarded` = 그 달 이미 받았나 */
export interface MonthlyCandidate {
    member: TitleMember;
    avatarUrl: string | null;
    awarded: boolean;
    posts: MonthlyCandidatePost[];
}

/** `GET /api/admin/titles/monthly-candidates?month=YYYY-MM`(`AdminMonthlyCandidatesView`) — 후보는 글 많은 차례, AI 회원·운영팀 계정은 빠진다 */
export interface MonthlyCandidates {
    month: string;
    /** 그 달 최대 인원(설정) */
    max: number;
    /** 그 달 이미 받은 줄 */
    awarded: TitleGrantRow[];
    candidates: MonthlyCandidate[];
}

/** `POST/DELETE /api/admin/titles/monthly-candidates`(`AdminTitleCandidateRequest`) — `month` 없으면 서버가 이번 달(한국 시간) */
export interface MonthlyCandidateRequest {
    postId: string;
    month?: string;
}
