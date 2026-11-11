package com.community.aimember.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * roboyam.ai-member.* — AI 회원(ai-members-contract). 고를 수 있는 모델은 여기에 두지 않는다 — 표 {@code ai_models}(shared V189, {@link AiModelCatalog})가 정본이고
 * 관리자 설정 화면 「모델 목록」에서 바꾼다(2026-11-11 — 옛 {@code models.*} 설정은 지웠다).
 *
 * @param runnerEnabled 이 서버에서 생성 실행기를 돌리는가 — 실행기는 관리자(admin) 서버에만 있다(2026-11-10 — 배치는 admin 또는 pg_cron 에서만). {@code roboyam.batch.enabled} 도 켜져야 돌고,
 *                      여러 대여도 잠금으로 한 곳만 돈다. api 서버는 이 값을 쓰지 않는다. 환경 변수 {@code ROBOYAM_AI_MEMBER_RUNNER}
 * @param pollMillis    실행기 한 바퀴 간격(밀리초)
 * @param callsPerTick  한 바퀴의 생성 호출 수 상한(계정당 1건)
 * @param openaiUrl     OpenAI Chat Completions 기본 주소
 * @param googleUrl     Google Gemini API 기본 주소
 * @param anthropicUrl  Anthropic Messages API 기본 주소
 * @param timeout       제공사 호출 응답 제한 시간
 * @param maxOutputTokens 한 번에 받을 최대 출력 토큰(글·댓글은 짧다 — 생각 토큰이 드는 모델은 이 안에서 쓴다)
 */
@ConfigurationProperties(prefix = "roboyam.ai-member")
public record AiMemberProperties(@DefaultValue("true") boolean runnerEnabled, @DefaultValue("60000") long pollMillis, @DefaultValue("3") int callsPerTick,
                                 @DefaultValue("https://api.openai.com") String openaiUrl,
                                 @DefaultValue("https://generativelanguage.googleapis.com") String googleUrl,
                                 @DefaultValue("https://api.anthropic.com") String anthropicUrl, @DefaultValue("60s") Duration timeout,
                                 @DefaultValue("4000") int maxOutputTokens) {
}
