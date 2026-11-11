package com.community.aimember.llm;

import com.community.aimember.entity.AiProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제공사 실패 응답 → 까닭 코드 + 제공사 메시지(2026-11-11 — 결제·크레딧 부족을 따로 가른다). 응답 본문은 JSON 의 {@code error} 칸만 읽는다.
 *
 * <p><b>결제·크레딧 부족({@link AiLlmException#BILLING_REQUIRED})</b> — HTTP 상태보다 먼저 본다(429 라도 돈이 없으면 이것):
 * <ul>
 *   <li><b>OpenAI</b>: {@code error.code} 또는 {@code error.type} 이 {@code insufficient_quota} · {@code billing_hard_limit_reached} · {@code billing_not_active}.
 *       같은 429 라도 {@code rate_limit_exceeded} 는 요청 한도다.</li>
 *   <li><b>Anthropic</b>: 402 · {@code error.type billing_error} · {@code error.message} 에 「credit balance」(400 「Your credit balance is too low…」).</li>
 *   <li><b>Google</b>: 메시지에 「enable billing」 · {@code error.status} 가 {@code FAILED_PRECONDITION}·{@code PERMISSION_DENIED} 이고 메시지에 「billing」 ·
 *       {@code RESOURCE_EXHAUSTED}(429) 이고 「limit: 0」(그 등급에서 못 쓰는 모델) 또는 「billing」·「quota」 문구가 있는데 <b>다시 시도 안내가 없을 때</b>
 *       ({@code RetryInfo} 상세나 「retry in」 이 있으면 분당 한도 — 요청 한도로 둔다).</li>
 * </ul>
 * 그 밖에 401·403 · Google 400 {@code API_KEY_INVALID}(「API key not valid」) → {@link AiLlmException#KEY_INVALID}, 429 → {@link AiLlmException#RATE_LIMITED},
 * 나머지 → {@link AiLlmException#PROVIDER_ERROR}.
 *
 * <p><b>제공사 메시지</b>: {@code error.message} 문자열만(본문 전체·다른 칸은 싣지 않는다) — 줄바꿈·제어 문자는 빈칸으로, 키 모양({@code sk-…}·{@code AIza…}·{@code Bearer …})과
 * 이메일·전화번호 등({@link AiPiiMasker})은 가리고, 앞 {@value #DETAIL_MAX}자까지.
 */
public final class AiLlmErrors {

    /** 제공사 메시지 최대 글자(코드 포인트) — {@code ai_member_drafts.fail_detail}·{@code ai_provider_keys.status_detail} varchar(300) */
    public static final int DETAIL_MAX = 300;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> OPENAI_BILLING = Set.of("insufficient_quota", "billing_hard_limit_reached", "billing_not_active");
    private static final Pattern KEY_LIKE = Pattern.compile("(?i)(?:sk-(?:ant-|proj-)?[A-Za-z0-9_*\\-]{4,}|AIza[0-9A-Za-z_\\-]{8,}|bearer\\s+[A-Za-z0-9._\\-]{8,})");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]+");

    private AiLlmErrors() {
    }

    /** 가른 결과 — 까닭 코드와 가린 제공사 메시지(없으면 null) */
    public record Failure(String reason, String detail) {
    }

    /**
     * @param provider 부른 제공사
     * @param status   HTTP 상태 번호
     * @param body     응답 본문(없거나 JSON 이 아니면 상태 번호만으로 가른다)
     */
    public static Failure classify(AiProvider provider, int status, String body) {
        Map<?, ?> error = errorOf(body);
        String message = text(error, "message");
        String code = text(error, "code");
        String type = text(error, "type");
        String googleStatus = text(error, "status");
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String reason;
        if (billing(provider, status, code, type, googleStatus, lower, error)) {
            reason = AiLlmException.BILLING_REQUIRED;
        } else if (status == 401 || status == 403 || (provider == AiProvider.GOOGLE && (lower.contains("api key not valid") || hasDetailReason(error, "API_KEY_INVALID")))) {
            reason = AiLlmException.KEY_INVALID;
        } else if (status == 429) {
            reason = AiLlmException.RATE_LIMITED;
        } else {
            reason = AiLlmException.PROVIDER_ERROR;
        }
        return new Failure(reason, sanitize(message));
    }

    private static boolean billing(AiProvider provider, int status, String code, String type, String googleStatus, String lower, Map<?, ?> error) {
        return switch (provider) {
            case OPENAI -> (code != null && OPENAI_BILLING.contains(code)) || (type != null && OPENAI_BILLING.contains(type));
            case ANTHROPIC -> status == 402 || "billing_error".equals(type) || lower.contains("credit balance");
            case GOOGLE -> {
                if (lower.contains("enable billing")) {
                    yield true;
                }
                if (("FAILED_PRECONDITION".equals(googleStatus) || "PERMISSION_DENIED".equals(googleStatus)) && lower.contains("billing")) {
                    yield true;
                }
                boolean exhausted = "RESOURCE_EXHAUSTED".equals(googleStatus) || status == 429;
                if (!exhausted) {
                    yield false;
                }
                if (lower.contains("limit: 0")) {
                    yield true;
                }
                boolean retryHint = lower.contains("retry in") || hasDetailType(error, "RetryInfo");
                yield !retryHint && (lower.contains("billing") || lower.contains("quota"));
            }
        };
    }

    /** 제공사 메시지를 저장할 모양으로 — 빈칸 정리 · 키 모양·개인정보 가림 · 앞 300자 */
    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String s = CONTROL.matcher(message).replaceAll(" ").strip();
        if (s.isEmpty()) {
            return null;
        }
        s = KEY_LIKE.matcher(s).replaceAll(AiPiiMasker.MASK);
        s = AiPiiMasker.mask(s);
        if (s.codePointCount(0, s.length()) > DETAIL_MAX) {
            s = s.substring(0, s.offsetByCodePoints(0, DETAIL_MAX));
        }
        return s;
    }

    private static Map<?, ?> errorOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Object root = JSON.readValue(body, Object.class);
            // Google 은 가끔 [ { "error": … } ] 로 준다
            if (root instanceof List<?> list && !list.isEmpty()) {
                root = list.get(0);
            }
            return root instanceof Map<?, ?> m && m.get("error") instanceof Map<?, ?> e ? e : null;
        } catch (RuntimeException e) {
            return null; // JSON 이 아니다(게이트웨이 HTML 등) — 본문은 싣지 않는다
        }
    }

    private static String text(Map<?, ?> m, String key) {
        if (m == null) {
            return null;
        }
        Object v = m.get(key);
        return v instanceof String s ? s : v instanceof Number n ? n.toString() : null;
    }

    private static boolean hasDetailType(Map<?, ?> error, String typeSuffix) {
        if (error == null || !(error.get("details") instanceof List<?> details)) {
            return false;
        }
        for (Object d : details) {
            if (d instanceof Map<?, ?> m && m.get("@type") instanceof String t && t.endsWith(typeSuffix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDetailReason(Map<?, ?> error, String reason) {
        if (error == null || !(error.get("details") instanceof List<?> details)) {
            return false;
        }
        for (Object d : details) {
            if (d instanceof Map<?, ?> m && reason.equals(m.get("reason"))) {
                return true;
            }
        }
        return false;
    }
}
