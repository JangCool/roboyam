package com.community.aimember.llm;

import com.community.aimember.service.AiMemberProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 세 제공사 API 직접 호출(새 의존성 없이 Spring {@link RestClient}) — 결정 2026-11-10: 세 제공사 모두 같은 방식.
 * <ul>
 *   <li><b>OpenAI</b> Chat Completions {@code POST /v1/chat/completions} — {@code Authorization: Bearer}, {@code store: false}(대화 저장 끔).
 *       응답 {@code choices[0].message.content} · {@code usage.prompt_tokens}·{@code completion_tokens}. {@code message.refusal} 이 있으면 거절.</li>
 *   <li><b>Google</b> Gemini {@code POST /v1beta/models/{model}:generateContent} — {@code x-goog-api-key}, {@code systemInstruction} + {@code contents}.
 *       응답 {@code candidates[0].content.parts[].text}(생각 조각 {@code thought: true} 은 뺀다) · {@code usageMetadata}. {@code promptFeedback.blockReason} ·
 *       {@code finishReason SAFETY} 면 거절. 무료 등급 키는 쓰지 않는다(법무 메모 — 운영 키는 유료 등급만).</li>
 *   <li><b>Anthropic</b> Messages {@code POST /v1/messages} — {@code x-api-key}, {@code anthropic-version: 2026-10-21}(이 버전은 temperature·top_p·top_k 를 받지 않아
 *       보내지 않는다), {@code output_config.effort: low}(짧은 잡담 글 — 생각이 늘 켜진 모델의 깊이를 낮춘다). 응답 {@code content[]} 의 {@code text} 블록만 ·
 *       {@code usage.input_tokens}·{@code output_tokens}. {@code stop_reason refusal} 이면 거절.</li>
 * </ul>
 * 재시도는 하지 않는다(정책 11 — 실패는 다음 차례로 미룬다). 키·프롬프트·응답 본문은 로그에 싣지 않는다(제공사·모델·HTTP 상태 번호·까닭 코드만).
 * 실패 응답은 {@link AiLlmErrors} 가 가른다 — 결제·크레딧 부족이면 {@code BILLING_REQUIRED}(429 {@code insufficient_quota} 포함), 제공사 메시지는 가려서 예외에 싣는다(2026-11-11).
 */
@Component
public class AiLlmClient {

    private static final Logger log = LoggerFactory.getLogger(AiLlmClient.class);

    static final String ANTHROPIC_VERSION = "2026-10-21";

    private final RestClient openai;
    private final RestClient google;
    private final RestClient anthropic;

    public AiLlmClient(AiMemberProperties properties) {
        Duration timeout = properties.timeout() == null ? Duration.ofSeconds(60) : properties.timeout();
        this.openai = client(properties.openaiUrl(), timeout);
        this.google = client(properties.googleUrl(), timeout);
        this.anthropic = client(properties.anthropicUrl(), timeout);
    }

    private static RestClient client(String baseUrl, Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * 한 번 부른다.
     *
     * @throws AiLlmException 실패(까닭 코드와 그때까지의 토큰)
     */
    public AiLlmResult generate(AiLlmRequest request, String apiKey) {
        try {
            return switch (request.provider()) {
                case OPENAI -> openai(request, apiKey);
                case GOOGLE -> google(request, apiKey);
                case ANTHROPIC -> anthropic(request, apiKey);
            };
        } catch (AiLlmException e) {
            throw e;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            // 본문의 error 칸으로 결제·크레딧 부족을 가른다(AiLlmErrors) — 로그에는 까닭 코드만, 메시지는 예외에(가린 것 · 300자)
            AiLlmErrors.Failure failure = AiLlmErrors.classify(request.provider(), status.value(), safeBody(e));
            log.warn("AI 제공사 호출이 실패했다 provider={} model={} status={} reason={}", request.provider(), request.model(), status.value(), failure.reason());
            throw new AiLlmException(failure.reason(), 0, 0, failure.detail());
        } catch (ResourceAccessException e) {
            log.warn("AI 제공사가 응답하지 않았다 provider={} model={}", request.provider(), request.model());
            throw new AiLlmException(AiLlmException.TIMEOUT, 0, 0);
        } catch (RuntimeException e) {
            log.warn("AI 제공사 응답을 읽지 못했다 provider={} model={} 원인={}", request.provider(), request.model(), e.getClass().getSimpleName());
            throw new AiLlmException(AiLlmException.PROVIDER_ERROR, 0, 0);
        }
    }

    private AiLlmResult openai(AiLlmRequest r, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", r.model());
        body.put("messages", List.of(Map.of("role", "system", "content", r.system()), Map.of("role", "user", "content", r.user())));
        body.put("max_completion_tokens", r.maxTokens());
        body.put("store", false);
        Map<?, ?> res = openai.post().uri("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + key).body(body).retrieve().body(Map.class);
        Map<?, ?> usage = map(res, "usage");
        int in = integer(usage, "prompt_tokens");
        int out = integer(usage, "completion_tokens");
        Map<?, ?> choice = first(res, "choices");
        Map<?, ?> message = map(choice, "message");
        if (message != null && message.get("refusal") instanceof String refusal && !refusal.isBlank()) {
            throw new AiLlmException(AiLlmException.REFUSED, in, out);
        }
        return result(message == null ? null : message.get("content"), in, out);
    }

    private AiLlmResult google(AiLlmRequest r, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", r.system()))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", r.user())))));
        body.put("generationConfig", Map.of("maxOutputTokens", r.maxTokens()));
        Map<?, ?> res = google.post().uri("/v1beta/models/{model}:generateContent", r.model()).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).header("x-goog-api-key", key).body(body).retrieve().body(Map.class);
        Map<?, ?> usage = map(res, "usageMetadata");
        int in = integer(usage, "promptTokenCount");
        int out = integer(usage, "candidatesTokenCount") + integer(usage, "thoughtsTokenCount");
        Map<?, ?> feedback = map(res, "promptFeedback");
        if (feedback != null && feedback.get("blockReason") != null) {
            throw new AiLlmException(AiLlmException.REFUSED, in, out);
        }
        Map<?, ?> candidate = first(res, "candidates");
        if (candidate != null && "SAFETY".equals(candidate.get("finishReason"))) {
            throw new AiLlmException(AiLlmException.REFUSED, in, out);
        }
        StringBuilder text = new StringBuilder();
        Map<?, ?> content = map(candidate, "content");
        if (content != null && content.get("parts") instanceof List<?> parts) {
            for (Object part : parts) {
                if (part instanceof Map<?, ?> p && !Boolean.TRUE.equals(p.get("thought")) && p.get("text") instanceof String t) {
                    text.append(t);
                }
            }
        }
        return result(text.toString(), in, out);
    }

    private AiLlmResult anthropic(AiLlmRequest r, String key) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", r.model());
        body.put("max_tokens", r.maxTokens());
        body.put("system", r.system());
        body.put("messages", List.of(Map.of("role", "user", "content", r.user())));
        body.put("output_config", Map.of("effort", "low"));
        Map<?, ?> res = anthropic.post().uri("/v1/messages").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .header("x-api-key", key).header("anthropic-version", ANTHROPIC_VERSION).body(body).retrieve().body(Map.class);
        Map<?, ?> usage = map(res, "usage");
        int in = integer(usage, "input_tokens");
        int out = integer(usage, "output_tokens");
        if (res != null && "refusal".equals(res.get("stop_reason"))) {
            throw new AiLlmException(AiLlmException.REFUSED, in, out);
        }
        StringBuilder text = new StringBuilder();
        if (res != null && res.get("content") instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> b && "text".equals(b.get("type")) && b.get("text") instanceof String t) {
                    text.append(t);
                }
            }
        }
        return result(text.toString(), in, out);
    }

    /** 실패 응답 본문(못 읽으면 null) — 아주 긴 본문은 앞만 본다 */
    private static String safeBody(RestClientResponseException e) {
        try {
            String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            return body.length() > 20_000 ? body.substring(0, 20_000) : body;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static AiLlmResult result(Object text, int in, int out) {
        if (!(text instanceof String s) || s.isBlank()) {
            throw new AiLlmException(AiLlmException.EMPTY, in, out);
        }
        return new AiLlmResult(s.strip(), in, out);
    }

    private static Map<?, ?> map(Map<?, ?> parent, String key) {
        return parent != null && parent.get(key) instanceof Map<?, ?> m ? m : null;
    }

    private static Map<?, ?> first(Map<?, ?> parent, String key) {
        return parent != null && parent.get(key) instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m ? m : null;
    }

    private static int integer(Map<?, ?> parent, String key) {
        return parent != null && parent.get(key) instanceof Number n ? n.intValue() : 0;
    }
}
