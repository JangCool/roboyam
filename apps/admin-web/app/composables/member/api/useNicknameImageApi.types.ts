/**
 * 닉네임 이미지 심사 API 가 주고받는 모양 — admin 서버 `/api/admin/nickname-images`(계약 `docs/features/member-social-contract.md` 「닉네임 이미지」·관리자 검토).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO. 이미지 주소(`url`)는 공개 주소이고 240×60 보다 클 수 있다(WebP·움직이는 GIF 는 원본 크기) — 화면이 240×60 틀에 맞춰 그린다. 파일을 지운 신청(해제·취소 등)은 null.
 */
import type { GradeKey } from '~/composables/grade/api/useGradeApi.types';

/** 신청 상태 — 검토 대기 · 승인(적용 중) · 반려 · 해제(승인 뒤 운영자가 내림) */
export type NicknameImageStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED';

/** 신청한 회원 — 읽을 때의 현재 닉네임·등급 */
export interface NicknameImageMember {
    id: string;
    nickname: string;
    grade: GradeKey;
    gradeLabel: string;
}

/** 처리한 운영자 */
export interface NicknameImageReviewer {
    id: string;
    /** 운영자 계정이 지워졌으면 null */
    name: string | null;
}

/** 신청 한 건 */
export interface NicknameImageView {
    id: string;
    member: NicknameImageMember;
    status: NicknameImageStatus;
    /** 파일이 지워졌으면 null */
    url: string | null;
    /** `image/png` · `image/webp` · `image/gif` … */
    contentType: string;
    bytes: number;
    width: number;
    height: number;
    /** 움직이는 그림(GIF)의 프레임 수 — 정지 그림은 1 */
    frames: number;
    createdAt: string;
    /** 승인·반려한 시각(대기면 null) */
    reviewedAt: string | null;
    reviewer: NicknameImageReviewer | null;
    rejectReason: string | null;
    revokedAt: string | null;
    revokeReason: string | null;
}

/** `GET /api/admin/nickname-images` — 쪽은 1부터 */
export interface NicknameImagePage {
    items: NicknameImageView[];
    total: number;
    page: number;
    size: number;
    /** 거르기와 상관없는 검토 대기 수 — 탭에 보인다 */
    pendingCount: number;
}

/** 목록 조건 — 빈 검색어는 보내지 않는다 */
export interface NicknameImageQuery {
    status: NicknameImageStatus;
    /** 닉네임 검색 */
    keyword?: string;
    /** 1부터 */
    page?: number;
    size?: number;
}

/** 반려·해제 사유(1~200자) */
export interface NicknameImageReasonRequest {
    reason: string;
}

// ── 신청 조건 설정(`/api/admin/nickname-image-settings`) ─

/** 조건 방식 — 등급 기준(최소 등급 + 이메일 인증, 늘 필요) · 사용자 정의(최소 등급·가입 후 일수·글·댓글 수·보유 포인트·이메일 인증을 모두 채워야 한다) */
export type NicknameImageSettingsMode = 'GRADE' | 'CUSTOM';

/** 고를 수 있는 등급 — 서열 순 */
export interface NicknameImageGradeOption {
    key: GradeKey;
    label: string;
    level: number;
}

/** 마지막으로 저장한 운영자 */
export interface NicknameImageSettingsEditor {
    id: string;
    /** 운영자 계정이 지워졌으면 null */
    name: string | null;
}

/** 저장하는 값 — 숫자 칸의 0 은 그 조건 없음. 등급 기준이면 `gradeKey` 필수, 사용자 정의에서 null = 등급 조건 없음 */
export interface NicknameImageSettingsRequest {
    mode: NicknameImageSettingsMode;
    gradeKey: GradeKey | null;
    /** 가입 후 일수 0~3650 */
    minDays: number;
    /** 글·댓글 수 0~100000 */
    minActivity: number;
    /** 보유 포인트 0~100000000 */
    minPoints: number;
    /** 사용자 정의에서만 끌 수 있다(등급 기준은 서버가 늘 요구한다) */
    requireEmailVerified: boolean;
}

/** `GET`·`PUT /api/admin/nickname-image-settings` — 저장된 값 + 고를 수 있는 등급 */
export interface NicknameImageSettingsView extends NicknameImageSettingsRequest {
    /** 고른 등급이 지워졌다(`gradeKey` 가 `grades` 에 없다) — 다시 골라야 한다 */
    gradeMissing: boolean;
    grades: NicknameImageGradeOption[];
    /** 한 번도 저장하지 않았으면 null(기본값: 등급 기준 · 베테랑 이상) */
    updatedAt: string | null;
    updatedBy: NicknameImageSettingsEditor | null;
}

/** 서버가 `fields.<칸>` 으로 거절할 수 있는 칸 */
export type NicknameImageSettingsField = keyof NicknameImageSettingsRequest;
