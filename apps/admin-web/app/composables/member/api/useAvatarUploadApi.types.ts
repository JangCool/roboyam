/**
 * 아바타 올리기 심사 API 가 주고받는 모양 — **서버 연결됨**(admin 서버 `/api/admin/avatar-uploads` — shared `AdminAvatarUploadPage`·`AdminAvatarUploadView`, 계약 `docs/features/commerce-motion-avatar-contract.md` 「API 계약」).
 * 닉네임 이미지 심사(`useNicknameImageApi.types.ts`)와 같은 모양에 아바타 칸(정지 그림·변경권·사용 중·움직이는 꾸미기 충돌)을 더했다.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO. 그림 주소(`url`)는 공개 주소(정사각형, 움직이는 GIF 는 원본). 파일을 지운 신청은 null.
 */
import type { NicknameImageMember, NicknameImageReviewer, NicknameImageStatus } from '~/composables/member/api/useNicknameImageApi.types';

/** 신청 상태 — 닉네임 이미지와 같다(심사 대기 · 승인 · 반려 · 해제) */
export type AvatarUploadStatus = NicknameImageStatus;

/** 신청에 든 아바타 변경권 — 예약(심사 대기) · 사용(승인) · 반환(반려·본인 취소·대체) */
export type AvatarUploadTicket = 'RESERVED' | 'USED' | 'RETURNED';

/** 신청 한 건 */
export interface AvatarUploadView {
    id: string;
    member: NicknameImageMember;
    status: AvatarUploadStatus;
    /** 파일이 지워졌으면 null */
    url: string | null;
    /** 움직이는 GIF 의 첫 장면 정지 그림(글 목록·움직임 줄이기) — 정지 그림은 null */
    stillUrl: string | null;
    /** `image/png` · `image/webp` · `image/gif` … */
    contentType: string;
    bytes: number;
    width: number;
    height: number;
    /** 프레임 수 — 정지 그림은 1 */
    frames: number;
    ticket: AvatarUploadTicket;
    /** 승인된 것 — 지금 회원의 프로필 아바타로 쓰는 중(다른 아바타로 바꿨으면 false) */
    inUse: boolean;
    /** 승인된 것 — 회원의 「내가 올린 아바타」가 지금 이 그림이다(나중에 승인된 그림으로 바뀌었으면 false — 해제할 것이 없다) */
    current: boolean;
    /** 대기 중 움직이는 GIF — 회원이 지금 움직이는 닉네임 이미지를 쓰고 있어 승인하면 닉네임 이미지가 쉰다(하나만) */
    pausesNicknameImage: boolean;
    createdAt: string;
    reviewedAt: string | null;
    reviewer: NicknameImageReviewer | null;
    rejectReason: string | null;
    revokedAt: string | null;
    revokeReason: string | null;
}

/** `GET /api/admin/avatar-uploads` — 쪽은 1부터 */
export interface AvatarUploadPage {
    items: AvatarUploadView[];
    total: number;
    page: number;
    size: number;
    /** 거르기와 상관없는 심사 대기 수 — 탭에 보인다 */
    pendingCount: number;
}

/** 목록 조건 — 빈 검색어는 보내지 않는다 */
export interface AvatarUploadQuery {
    status: AvatarUploadStatus;
    keyword?: string;
    page?: number;
    size?: number;
}

/** 반려·해제 사유(1~200자, 회원에게 보인다) */
export interface AvatarUploadReasonRequest {
    reason: string;
}
