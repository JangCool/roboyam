package com.community.profile.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * {@code GET /api/users/{id}/profile}(user-web {@code PublicProfile}) — 비회원도 읽는다. 이전 닉네임·이메일은 싣지 않는다.
 * 차단·메모({@code blockedByMe}·{@code memo})는 보는 사람 기준({@code ViewerBlocks#toward}) — 비회원·본인은 {@code false}·null.
 *
 * @param grade      등급 키
 * @param gradeLabel 등급 이름(등급 설정 — 없으면 키)
 * @param role       공개 사이트 회원은 늘 {@code USER}
 * @param bio        한 줄 소개 — 없으면 null
 * @param isMe        보는 사람이 이 회원 본인인가(비회원 false)
 * @param blockedByMe 보는 사람이 이 회원을 차단했나(회원 차단만 — 단어 차단과 무관)
 * @param memo        보는 사람이 이 회원에게 남긴 메모 — 없으면 null
 */
public record PublicProfileView(String id, String nickname, String grade, String gradeLabel, String role, String bio, Instant joinedAt,
                                @JsonProperty("isMe") boolean isMe, ProfileStatsView stats, boolean blockedByMe, String memo) {
}
