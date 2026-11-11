package com.community.aimember.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.math.BigDecimal;
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

    /** 계정에 고를 수 있는 모델 하나(모델 목록에서 「사용」인 것만) — 가격은 100만 토큰당 USD 참고값(없으면 null) */
    public record ModelOption(String modelId, String label, BigDecimal priceInput, BigDecimal priceOutput, boolean freeTier, boolean defaultModel) {
    }

    /** {@code models} — 제공사 → 켜진 모델(목록 차례, 기본은 {@code defaultModel}) */
    public record FormOptions(Map<String, List<ModelOption>> models, List<BoardOption> boards, List<GroupOption> groups, List<AccountRef> accounts,
                              List<AvatarOption> avatars) {
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

    /** 키 줄 — {@code status} OK · BILLING_REQUIRED · KEY_INVALID(OK 가 아니면 그 제공사 계정이 쉰다), {@code statusDetail} 제공사 메시지(가린 것), {@code statusAt} 바뀐 때 */
    public record KeyState(String provider, boolean configured, Instant updatedAt, String updatedBy, String status, String statusDetail, Instant statusAt) {
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
                      String reviewer, String failReason, String failDetail) {
    }

    public record LogPage(List<Log> items, int page, long total, long tokenSum) {
    }

    // ── 모델 목록 ──

    /** 모델 목록 한 줄 — {@code accounts} = 이 모델을 쓰는 AI 계정 수(있으면 지울 수 없다 — 끄면 그 계정들이 쉰다) */
    public record ModelRow(String id, String provider, String modelId, String label, BigDecimal priceInput, BigDecimal priceOutput, boolean freeTier,
                           boolean enabled, boolean defaultModel, long accounts, Instant updatedAt) {
    }

    public record ModelList(List<ModelRow> items) {
    }

    /** AI 전용 소모임 지정 결과 */
    public record GroupAiOnly(String id, String name, boolean aiOnly) {
    }

    // ── 프롬프트(V190) ──

    public record PromptMood(String name, String prompt, boolean enabled) {
    }

    /** 프롬프트 값 한 벌 — {@code values} 는 지금 쓰는 값(빈 칸은 기본값으로 채운 것), {@code defaults} 는 코드 기본값 */
    public record PromptValues(String common, String post, String news, String comment, String reply, int moodChance, List<PromptMood> moods, int newsRatio,
                               String newsKeyword, int newsDays, boolean paragraphAuto, int paragraphSentences) {
    }

    /** 입력 한도(서버 검사와 같다) */
    public record PromptLimits(int commonMax, int taskMax, int moodsMax, int moodNameMax, int moodPromptMax, int newsKeywordMax, int newsDaysMin, int newsDaysMax,
                               int sentencesMin, int sentencesMax) {
    }

    /**
     * 프롬프트 설정 화면 — {@code fixedRules} = 시스템 맨 뒤에 늘 붙는 고정 규칙(읽기 전용), {@code placeholders} = 지시문에 쓸 수 있는 자리표시.
     */
    public record PromptSettings(PromptValues values, PromptValues defaults, List<String> fixedRules, List<String> placeholders, PromptLimits limits,
                                 Instant updatedAt, String updatedBy) {
    }

    /** 미리 보기의 뉴스 소재 글 */
    public record PromptSource(String postId, String title) {
    }

    /**
     * 미리 보기 결과 — {@code sourceMissing} = 뉴스 글인데 지금 소재 후보가 없다({@code user} 는 null, 실행기라면 보통 새 글로 쓴다).
     * {@code sample} = 첫 댓글·답글처럼 인용할 글이 예시 글이다. {@code place} = 쓰는 곳 이름(계정이 고른 첫 게시판 — 없으면 null).
     */
    public record PromptPreview(String kind, String system, String user, PromptSource source, boolean sourceMissing, boolean sample, String place, String mood) {
    }
}
