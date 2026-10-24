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
