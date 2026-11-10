package com.community.aimember.service;

import com.community.aimember.entity.AiProvider;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.ai-member.* — AI 회원(ai-members-contract).
 *
 * @param runnerEnabled 이 서버에서 생성 실행기를 돌리는가(api 서버만 실행기가 있다 — 여러 대여도 잠금으로 한 곳만 돈다). 환경 변수 {@code ROBOYAM_AI_MEMBER_RUNNER}
 * @param pollMillis    실행기 한 바퀴 간격(밀리초)
 * @param callsPerTick  한 바퀴의 생성 호출 수 상한(계정당 1건)
 * @param models        제공사별 고를 수 있는 모델(첫 값이 기본) — 관리자 화면·저장 검사·실행기가 같이 본다
 * @param openaiUrl     OpenAI Chat Completions 기본 주소
 * @param googleUrl     Google Gemini API 기본 주소
 * @param anthropicUrl  Anthropic Messages API 기본 주소
 * @param timeout       제공사 호출 응답 제한 시간
 * @param maxOutputTokens 한 번에 받을 최대 출력 토큰(글·댓글은 짧다 — 생각 토큰이 드는 모델은 이 안에서 쓴다)
 */
@ConfigurationProperties(prefix = "roboyam.ai-member")
public record AiMemberProperties(@DefaultValue("true") boolean runnerEnabled, @DefaultValue("60000") long pollMillis, @DefaultValue("3") int callsPerTick,
                                 @DefaultValue Models models, @DefaultValue("https://api.openai.com") String openaiUrl,
                                 @DefaultValue("https://generativelanguage.googleapis.com") String googleUrl,
                                 @DefaultValue("https://api.anthropic.com") String anthropicUrl, @DefaultValue("60s") Duration timeout,
                                 @DefaultValue("4000") int maxOutputTokens) {

    /** 제공사별 모델 목록 */
    public record Models(@DefaultValue({"gpt-6-luna", "gpt-6.1-sol"}) List<String> openai,
                         @DefaultValue({"gemini-3.6-flash", "gemini-3.5-flash-lite"}) List<String> google,
                         @DefaultValue({"claude-haiku-5-5", "claude-sonnet-5-5"}) List<String> anthropic) {

        public Map<AiProvider, List<String>> byProvider() {
            Map<AiProvider, List<String>> out = new EnumMap<>(AiProvider.class);
            out.put(AiProvider.OPENAI, clean(openai));
            out.put(AiProvider.GOOGLE, clean(google));
            out.put(AiProvider.ANTHROPIC, clean(anthropic));
            return out;
        }

        private static List<String> clean(List<String> list) {
            return list == null ? List.of() : list.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        }
    }

    /** 그 제공사에서 고를 수 있는 모델인가 */
    public boolean allows(AiProvider provider, String model) {
        return provider != null && model != null && models.byProvider().getOrDefault(provider, List.of()).contains(model);
    }
}
