package com.community.group.dto.request;

/** {@code PUT /api/groups/{id}/recruiting} — 모집 켜기·끄기 */
public record GroupRecruitingRequest(Boolean recruiting) {
}
