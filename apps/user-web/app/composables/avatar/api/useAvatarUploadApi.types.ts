/**
 * 아바타 올리기 API 가 주고받는 모양 — 서버 `server/api`(`/api/me/avatar-upload`, shared `MyAvatarUploadView`). **서버 연결됨**.
 * 계약: `docs/features/commerce-motion-avatar-contract.md` 「API 계약」 아바타 올리기(api). 시각은 UTC ISO, ID 는 문자열(Snowflake).
 */

/** 대기·반려 신청 하나 — `url` 은 저장한 그림(움직이는 GIF 는 원본), `stillUrl` 은 움직이는 GIF 의 첫 장면 PNG(그 밖 null). 파일이 지워졌으면 둘 다 null */
export interface AvatarUploadRequest {
    id: string;
    status: 'PENDING' | 'REJECTED';
    url: string | null;
    stillUrl: string | null;
    animated: boolean;
    createdAt: string;
    /** 반려 사유(반려만) */
    rejectReason: string | null;
}

/** 승인된 「내가 올린 아바타」 한 장(새로 승인되면 바뀐다) — 카탈로그에서는 `upload-mine` 줄 */
export interface AvatarUploadCurrent {
    url: string;
    /** 움직이는 GIF 의 정지 그림(서버가 만든 첫 장면) — 정지 그림은 null */
    stillUrl: string | null;
    animated: boolean;
    approvedAt: string;
}

/** 올리기 한도 — 화면이 미리 거를 값(판정은 늘 서버, 서버 `AvatarUploadRules`) */
export interface AvatarUploadLimits {
    maxBytes: number;
    /** 가장 짧은 변의 최소(px) */
    minSize: number;
    /** 서버가 맞추는 저장 크기의 한도(px — 움직이는 GIF 원본·WebP 는 줄이지 않는다) */
    maxSize: number;
    /** 정사각형 허용 오차(%) */
    ratioTolerancePercent: number;
    maxGifFrames: number;
    /** `PNG` · `JPG` · `GIF` · `WebP` */
    formats: string[];
}

/** `GET /api/me/avatar-upload` — 화면 상태 한 벌(`POST` 의 201 도 같은 모양) */
export interface MyAvatarUpload {
    /** 쓸 수 있는 아바타 변경권 수(대기 신청에 예약된 장은 빼고) */
    ticketCount: number;
    /** 한 장 값(P) — 품목이 없으면 null */
    ticketPrice: number | null;
    /** 사러 갈 변경권 품목 ID — 없으면 null */
    ticketItemId: string | null;
    /** 대기 신청에 예약된 장이 있다(취소·반려되면 돌아온다) */
    reserved: boolean;
    /** 가장 최근의 대기·반려 신청 — 없으면 null */
    request: AvatarUploadRequest | null;
    /** 승인된 내가 올린 아바타 — 없으면 null */
    current: AvatarUploadCurrent | null;
    /** 그 그림을 지금 프로필 아바타로 쓰는 중 */
    inUse: boolean;
    limits: AvatarUploadLimits;
}

/** 올리기 선택 — 취소·진행률 */
export interface AvatarUploadOptions {
    signal?: AbortSignal;
    /** 0~1 */
    onProgress?: (fraction: number) => void;
}
