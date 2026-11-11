package com.community.aimember.entity;

import com.community.aimember.llm.AiPromptConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 회원 프롬프트 설정 — {@code ai_prompt_settings} 행 하나(id = 1, shared V190 — ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」).
 * 지시문 칸과 분위기 목록은 NULL 이면 코드 기본값({@link com.community.aimember.llm.AiPrompts})이다 — 푼 값은 {@link #config()}.
 */
@Entity
@Table(name = "ai_prompt_settings")
public class AiPromptSettingsEntity {

    public static final int ID = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<MoodJson>> MOOD_LIST = new TypeReference<>() {
    };

    @Id
    private Integer id;

    private String commonPrompt;
    private String postPrompt;
    private String newsPrompt;
    private String commentPrompt;
    private String replyPrompt;
    /** 강아지·고양이 소리 지시(V191) — NULL 이면 기본값 */
    private String dogPrompt;
    private String catPrompt;
    private short moodChance;

    /** 분위기 목록 JSON 배열 {@code [{name, prompt, enabled}]} — NULL 이면 기본 목록 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String moods;

    private short newsRatio;
    private String newsKeyword;
    private short newsDays;
    private boolean paragraphAuto;
    private short paragraphSentences;
    private Instant updatedAt;
    private Long updatedBy;

    protected AiPromptSettingsEntity() {
    }

    /** JSON 한 줄 모양(칸 이름이 저장 형식이다) */
    public record MoodJson(String name, String prompt, boolean enabled) {
    }

    /** 저장할 값(검사를 마친 것 — 지시문 칸·분위기 목록은 null = 기본값) */
    public record Values(String common, String post, String news, String comment, String reply, String dog, String cat, int moodChance, List<AiPromptConfig.Mood> moods, int newsRatio,
                         String newsKeyword, int newsDays, boolean paragraphAuto, int paragraphSentences) {
    }

    public void save(Values v, long operatorId, Instant now) {
        this.commonPrompt = v.common();
        this.postPrompt = v.post();
        this.newsPrompt = v.news();
        this.commentPrompt = v.comment();
        this.replyPrompt = v.reply();
        this.dogPrompt = v.dog();
        this.catPrompt = v.cat();
        this.moodChance = (short) v.moodChance();
        this.moods = v.moods() == null ? null : JSON.writeValueAsString(v.moods().stream().map(m -> new MoodJson(m.name(), m.prompt(), m.enabled())).toList());
        this.newsRatio = (short) v.newsRatio();
        this.newsKeyword = v.newsKeyword();
        this.newsDays = (short) v.newsDays();
        this.paragraphAuto = v.paragraphAuto();
        this.paragraphSentences = (short) v.paragraphSentences();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 저장된 값 그대로(NULL 은 null — 「기본값 사용 중」 표시용) */
    public Values values() {
        return new Values(commonPrompt, postPrompt, newsPrompt, commentPrompt, replyPrompt, dogPrompt, catPrompt, moodChance, storedMoods(), newsRatio, newsKeyword, newsDays,
                paragraphAuto, paragraphSentences);
    }

    /** 기본값으로 채운 값 */
    public AiPromptConfig config() {
        return new AiPromptConfig(commonPrompt, postPrompt, newsPrompt, commentPrompt, replyPrompt, dogPrompt, catPrompt, moodChance, storedMoods(), newsRatio, newsKeyword, newsDays,
                paragraphAuto, paragraphSentences);
    }

    /** 저장된 분위기 목록 — NULL 이면 null(기본 목록) */
    private List<AiPromptConfig.Mood> storedMoods() {
        if (moods == null || moods.isBlank()) {
            return null;
        }
        return JSON.readValue(moods, MOOD_LIST).stream().map(m -> new AiPromptConfig.Mood(m.name(), m.prompt(), m.enabled())).toList();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
