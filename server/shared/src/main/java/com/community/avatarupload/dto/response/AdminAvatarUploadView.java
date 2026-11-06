package com.community.avatarupload.dto.response;

import java.time.Instant;

/**
 * 아바타 올리기 신청 한 줄(관리자 — admin-web {@code AvatarUploadView}). {@code status} 는 {@code PENDING}·{@code APPROVED}·{@code REJECTED}·{@code REVOKED} —
 * 승인된 뒤 새 그림으로 바뀌었거나 회원이 지운 그림도 {@code APPROVED} 로 보이고 {@code current} 가 false(파일이 지워져 {@code url} 은 null).
 *
 * @param ticket              신청에 든 아바타 변경권 — {@code RESERVED}(대기) · {@code USED}(승인) · {@code RETURNED}(반려)
 * @param inUse               승인된 지금 그림을 회원이 프로필 아바타로 쓰는 중
 * @param current             회원의 「내가 올린 아바타」가 지금 이 그림이다(해제할 수 있다)
 * @param pausesNicknameImage 대기 중인 움직이는 GIF — 회원이 지금 움직이는 닉네임 이미지를 쓰고 있어 승인하면 닉네임 이미지가 쉰다(하나만)
 */
public record AdminAvatarUploadView(String id, AvatarUploadMemberView member, String status, String url, String stillUrl, String contentType, int bytes, int width,
                                    int height, int frames, String ticket, boolean inUse, boolean current, boolean pausesNicknameImage, Instant createdAt,
                                    Instant reviewedAt, AvatarUploadReviewerView reviewer, String rejectReason, Instant revokedAt, String revokeReason) {
}
