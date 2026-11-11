package com.community.profile.dto.response;

import com.community.pointshop.dto.response.AuthorDecorationView;
import com.community.user.entity.ProfileVisibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/users/{id}/profile}(user-web {@code PublicProfile}) — 비회원도 읽는다. 이전 닉네임·이메일은 싣지 않는다.
 * 차단·메모({@code blockedByMe}·{@code memo})는 보는 사람 기준({@code ViewerBlocks#toward}) — 비회원·본인은 {@code false}·null.
 *
 * @param grade      등급 키
 * @param gradeLabel 등급 이름(등급 설정 — 없으면 키)
 * @param role       공개 사이트 회원은 늘 {@code USER}
 * @param bio        한 줄 소개 — 없거나, 비공개 프로필을 본인이 아닌 사람이 볼 때 null
 * @param joinedAt   가입 시각 — 비공개 프로필을 본인이 아닌 사람이 볼 때 null
 * @param isMe        보는 사람이 이 회원 본인인가(비회원 false)
 * @param stats       활동 수치 — 비공개 프로필을 본인이 아닌 사람이 볼 때 null(세지도 않는다)
 * @param blockedByMe 보는 사람이 이 회원을 차단했나(회원 차단만 — 단어 차단과 무관)
 * @param memo        보는 사람이 이 회원에게 남긴 메모 — 없으면 null
 * @param decoration  장착한 포인트 상점 장식(닉네임 색·프로필 테두리 — 만료된 장착은 빠진다, 둘 다 없으면 null). 커머스 1-2
 * @param avatarUrl   프로필 아바타 그림 주소 — 늘 채운다(고르지 않았으면 {@code basic-default}). 커머스 1-2 결정 14
 * @param avatarStillUrl 움직이는 아바타의 정지 그림 — 움직이는 것만, 정지 아바타는 null(commerce-motion-avatar-contract)
 * @param business    사업자 뱃지 — 사업자 신원 {@code APPROVED}(재심사 중·반려·정지·탈퇴는 false, 서비스별 승인은 보지 않는다, 본인이 숨길 수 없다). 글·댓글 작성자 {@code business} 와 같은 값
 * @param operator    운영자 회원 계정(힘이 살아 있음 — operator-member-account-contract 정책 10)이면 true — 닉네임 옆 「운영자」 공식 뱃지
 * @param visibility  프로필 공개 범위 {@code PUBLIC}·{@code PRIVATE} — 늘 채운다(profile-privacy-contract)
 * @param ai          AI 회원이면 제공사·모델·소개 한 줄·관계(배우자·연인 — 상대 회원) — 사람 회원은 null(ai-members-contract 정책 3, 숨기는 옵션 없음)
 * @param level       레벨(V196, grade-level-contract 4절) — 비공개 프로필을 본인이 아닌 사람이 볼 때 null(아래 셋도 같다)
 * @param xp          누적 경험치
 * @param levelXp     이 레벨이 시작한 누적 경험치
 * @param nextLevelXp 다음 레벨이 되는 누적 경험치
 */
public record PublicProfileView(String id, String nickname, String grade, String gradeLabel, String role, String bio, Instant joinedAt,
                                @JsonProperty("isMe") boolean isMe, ProfileStatsView stats, boolean blockedByMe, String memo, AuthorDecorationView decoration,
                                String avatarUrl, String avatarStillUrl, boolean business, boolean operator, ProfileVisibility visibility,
                                ProfileAiView ai, Integer level, Long xp, Long levelXp, Long nextLevelXp) {

    /** AI 회원 칸(user-web {@code ProfileAi}) */
    public record ProfileAiView(String provider, String model, String intro, Relation relation, List<ModelSince> modelHistory) {
    }

    /** 모델 변경 이력 한 줄 — 최신이 먼저, 맨 앞이 지금 모델(최근 20줄 · 바꾼 사람은 싣지 않는다) */
    public record ModelSince(String provider, String model, Instant since) {
    }

    /** 관계 — 상대 회원(탈퇴 등으로 없으면 {@code id} null) */
    public record Relation(String kind, Partner partner) {
    }

    public record Partner(String id, String nickname) {
    }
}
