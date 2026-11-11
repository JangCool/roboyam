package com.community.aimember.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 회원 관리 API 의 요청 모양 — admin-web {@code useAiMemberAdminApi.types.ts}. 칸 검사는 서비스가 한다({@code 400 VALIDATION} 칸 경로 {@code persona.tone} 등).
 */
public final class AiMemberRequests {

    private AiMemberRequests() {
    }

    public record Window(String start, String end) {
    }

    public record Windows(Window morning, Window afternoon) {
    }

    public record Persona(String gender, String ageBand, String personality, String tone, String interests) {
    }

    public record Relation(String kind, String partnerAiMemberId) {
    }

    /**
     * 고치기 — {@code avatarId} 는 목업 타입에 없던 칸(카탈로그 아바타 ID — 빠지거나 null 이면 그대로). {@code voice} 글 방식 NORMAL·DOG·CAT(V191 — 빠지거나 null 이면 그대로,
     * 만들기는 NORMAL). 옛 「하루 글 수」({@code dailyPosts})는 V191 에서 뺐다(새 글 양은 설정의 「글 쓰는 때」).
     */
    public record Save(String provider, String model, Persona persona, String bio, Relation relation, Windows windows, String voice, List<String> boards,
                       List<String> groups, String avatarId) {
    }

    /** 만들기 — 고치기 칸 + 닉네임(서버가 로그인할 수 없는 회원 계정을 새로 만든다) */
    public record Create(String nickname, String avatarId, String provider, String model, Persona persona, String bio, Relation relation, Windows windows,
                         String voice, List<String> boards, List<String> groups) {

        public Save save() {
            return new Save(provider, model, persona, bio, relation, windows, voice, boards, groups, avatarId);
        }
    }

    public record Enabled(Boolean enabled) {
    }

    public record Ids(List<String> ids) {
    }

    public record Edit(String title, String body) {
    }

    /** 설정 저장 — 글 쓰는 때(V191) {@code quietDays} 1~30 · {@code busyIntervalDays} 1~90(화면은 7·30) · {@code dailyPostMax} 0~5 */
    public record SettingsSave(Boolean instantPublish, String instantScope, List<String> instantAccountIds, Integer dailyCallLimit, Boolean commentOnMemberPosts,
                               Integer quietDays, Integer busyIntervalDays, Integer dailyPostMax) {
    }

    public record Stopped(Boolean stopped) {
    }

    /** 키 — 값은 로그·감사에 싣지 않는다 */
    public record Key(String key) {

        @Override
        public String toString() {
            return "Key[***]";
        }
    }

    public record GroupAiOnly(Boolean aiOnly) {
    }

    /** 모델 직접 추가 — 모델 ID 만 꼭 있어야 한다(설명·가격·무료 표시는 고르기) */
    public record ModelCreate(String provider, String modelId, String label, BigDecimal priceInput, BigDecimal priceOutput, Boolean freeTier) {
    }

    /** 모델 사용 켜기·끄기 */
    public record ModelEnabled(Boolean enabled) {
    }

    /** 분위기 한 줄 */
    public record PromptMood(String name, String prompt, Boolean enabled) {
    }

    /**
     * 프롬프트 설정 저장(V190 · 강아지·고양이 소리 지시 {@code dog}·{@code cat} V191) — 지시문 칸이 null·빈 글이거나 기본값과 같으면 기본값(NULL)으로 둔다. {@code moods} 가 null 이면 기본 목록.
     * 확률·일수·문장 수가 빠지면 400 {@code VALIDATION}.
     */
    public record PromptSave(String common, String post, String news, String comment, String reply, String dog, String cat, Integer moodChance, List<PromptMood> moods, Integer newsRatio,
                             String newsKeyword, Integer newsDays, Boolean paragraphAuto, Integer paragraphSentences) {
    }

    /**
     * 프롬프트 미리 보기 — 저장 전 화면 값({@code settings})으로 실제 보낼 시스템·사용자 메시지를 만든다(제공사 호출 없음).
     *
     * @param kind POST · NEWS · COMMENT · REPLY
     * @param mood {@code settings.moods} 안의 차례(0부터) — null 이면 분위기 없음
     */
    public record PromptPreview(String aiMemberId, String kind, Integer mood, PromptSave settings) {
    }
}
