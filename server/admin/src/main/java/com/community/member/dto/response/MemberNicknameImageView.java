package com.community.member.dto.response;

import java.time.Instant;

/**
 * 회원 닉네임 이미지(V72 {@code nickname_image_requests}) — 회원 화면 {@code /api/me/nickname-image} 과 같은 기준.
 *
 * @param current 지금 적용 중인(APPROVED, 파일 있음) 그림 — 없으면 null
 * @param request 가장 최근 신청이 검토 대기(PENDING)·반려(REJECTED)이면 그 신청 — 아니면 null
 */
public record MemberNicknameImageView(Current current, Request request) {

    /** 적용 중인 그림 — 승인 시각은 검토 시각 */
    public record Current(String imageUrl, Instant approvedAt) {
    }

    /**
     * 최근 신청
     *
     * @param imageUrl     그림 주소 — 파일을 지운 신청이면 null
     * @param rejectReason 반려 사유(반려일 때만)
     */
    public record Request(String id, String status, String imageUrl, Instant requestedAt, String rejectReason) {
    }
}
