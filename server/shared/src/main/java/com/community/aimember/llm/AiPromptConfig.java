package com.community.aimember.llm;

import java.util.List;

/**
 * 프롬프트 설정의 푼 값(ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」) — 표 {@code ai_prompt_settings}(shared V190)의 칸이 비어 있으면(NULL·빈 글) 코드 기본값
 * ({@link AiPrompts} 의 {@code DEFAULT_*})으로 채운 것. 실행기는 바퀴마다 새로 읽고, 관리자 미리 보기는 저장 전 화면 값으로 만든다.
 *
 * @param common             공통 지시(시스템 앞부분 — 자리표시 {@value AiPrompts#PH_PERSONA}·{@value AiPrompts#PH_PLACE}·{@value AiPrompts#PH_MODEL})
 * @param post               새 글 지시
 * @param news               뉴스 글 지시
 * @param comment            첫 댓글 지시
 * @param reply              답글 지시
 * @param moodChance         분위기를 넣을 확률(%) 0~100
 * @param moods              분위기 목록(꺼진 것 포함)
 * @param newsRatio          새 글 차례에서 뉴스 글을 시도할 확률(%) 0~100
 * @param newsKeyword        소재 글 말머리 이름에 들어 있어야 할 말
 * @param newsDays           최근 며칠 안의 글만 소재로
 * @param paragraphAuto      문단 자동 나누기
 * @param paragraphSentences 자동 나누기의 한 문단 문장 수
 */
public record AiPromptConfig(String common, String post, String news, String comment, String reply, int moodChance, List<Mood> moods, int newsRatio,
                             String newsKeyword, int newsDays, boolean paragraphAuto, int paragraphSentences) {

    /** 분위기 한 줄 — 이름(화면용)·지시문·켜짐 */
    public record Mood(String name, String prompt, boolean enabled) {
    }

    public AiPromptConfig {
        common = orDefault(common, AiPrompts.DEFAULT_COMMON);
        post = orDefault(post, AiPrompts.DEFAULT_POST);
        news = orDefault(news, AiPrompts.DEFAULT_NEWS);
        comment = orDefault(comment, AiPrompts.DEFAULT_COMMENT);
        reply = orDefault(reply, AiPrompts.DEFAULT_REPLY);
        moodChance = clamp(moodChance, 0, 100);
        moods = moods == null ? AiPrompts.DEFAULT_MOODS : List.copyOf(moods);
        newsRatio = clamp(newsRatio, 0, 100);
        newsKeyword = newsKeyword == null || newsKeyword.isBlank() ? AiPrompts.DEFAULT_NEWS_KEYWORD : newsKeyword.strip();
        newsDays = clamp(newsDays, AiPrompts.NEWS_DAYS_MIN, AiPrompts.NEWS_DAYS_MAX);
        paragraphSentences = clamp(paragraphSentences, AiPrompts.SENTENCES_MIN, AiPrompts.SENTENCES_MAX);
    }

    /** 모든 칸이 코드 기본값 — 설정 행을 읽지 못했을 때 */
    public static AiPromptConfig defaults() {
        return new AiPromptConfig(null, null, null, null, null, AiPrompts.DEFAULT_MOOD_CHANCE, null, AiPrompts.DEFAULT_NEWS_RATIO, null, AiPrompts.DEFAULT_NEWS_DAYS,
                true, AiPrompts.DEFAULT_PARAGRAPH_SENTENCES);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
