package com.community.aimember.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AI 회원 관리 API 의 응답 모양 — admin-web {@code useAiMemberAdminApi.types.ts} 와 같은 칸(이름·모양). ID 는 문자열, 시각은 UTC ISO.
 */
public final class AiMemberViews {

    private AiMemberViews() {
    }

    /** 사람 — 회원 ID·닉네임 */
    public record PersonRef(String memberId, String nickname) {
    }

    /** 관계 상대 AI 계정 */
    public record AccountRef(String memberId, String nickname, String aiMemberId) {
    }

    public record Relation(String kind, AccountRef partner) {
    }

    /** 하루 활동 창 — 서비스 시간대(한국)의 {@code HH:mm} */
    public record Window(String start, String end) {
    }

    public record Windows(Window morning, Window afternoon) {
    }

    public record Persona(String gender, String ageBand, String personality, String tone, String interests) {
    }

    public record Today(long posts, long comments) {
    }

    /** 목록 한 줄 */
    public record Row(String id, PersonRef member, String avatarId, String provider, String model, Relation relation, boolean enabled, Today today,
                      long pendingDrafts, int dailyPosts, String restingReason) {
    }

    /** 상세 = 목록 줄 + 편집 칸 */
    /** 모델 변경 이력의 바꾼 운영자 */
    public record OperatorRef(String operatorId, String operatorName) {
    }

    /** 모델 변경 이력 한 줄 — {@code since} 부터 이 모델(최신이 먼저, 맨 앞이 지금 모델) */
    public record ModelHistory(String provider, String model, Instant since, OperatorRef changedBy) {
    }

    public record Detail(@JsonUnwrapped Row row, Persona persona, String bio, Windows windows, List<String> boards, List<String> groups, Instant updatedAt,
                         String updatedBy, List<ModelHistory> modelHistory) {
    }

    public record Calls(long today, int limit) {
    }

    public record ListView(List<Row> items, boolean stopped, Calls calls) {
    }

    public record BoardOption(String slug, String name, String blockedReason) {
    }

    public record GroupOption(String id, String name) {
    }

    /** 고를 수 있는 아바타 — 카탈로그의 정지 아바타 전부(무료·유료, 움직이는 것·올린 그림 제외). AI 계정은 포인트로 사지 않는다 */
    public record AvatarOption(String id, String name, String category) {
    }

    public record FormOptions(Map<String, List<String>> models, List<BoardOption> boards, List<GroupOption> groups, List<AccountRef> accounts, List<AvatarOption> avatars) {
    }

    // ── 대기함 ──

    public record DraftAccount(String aiMemberId, String nickname, String provider, String model) {
    }

    public record BoardRef(String slug, String name) {
    }

    public record DraftAuthor(String nickname, boolean ai) {
    }

    public record DraftPost(String postId, String title, DraftAuthor author) {
    }

    public record DraftParent(String commentId, DraftAuthor author, String body) {
    }

    public record Draft(String id, String kind, DraftAccount account, BoardRef board, String groupName, String title, String body, DraftPost post,
                        DraftParent parent, Integer round, Instant createdAt, long tokens) {
    }

    public record DraftList(List<Draft> items, long total) {
    }

    public record BatchResult(int done, int skipped) {
    }

    // ── 설정 ──

    public record Range(int min, int max) {
    }

    public record KeyState(String provider, boolean configured, Instant updatedAt, String updatedBy) {
    }

    public record Settings(boolean stopped, Instant stoppedAt, String stoppedBy, boolean instantPublish, String instantScope, List<String> instantAccountIds,
                           int dailyCallLimit, long todayCalls, boolean commentOnMemberPosts, Range firstComments, Range replyRounds, Range memberReplies,
                           List<KeyState> keys, boolean cryptoKeyConfigured) {
    }

    // ── 기록 ──

    public record LogAccount(String aiMemberId, String nickname) {
    }

    public record LogTokens(int input, int output) {
    }

    public record Log(String id, Instant at, LogAccount account, String kind, String result, String provider, String model, LogTokens tokens, String target,
                      String reviewer, String failReason) {
    }

    public record LogPage(List<Log> items, int page, long total, long tokenSum) {
    }

    /** AI 전용 소모임 지정 결과 */
    public record GroupAiOnly(String id, String name, boolean aiOnly) {
    }
}
