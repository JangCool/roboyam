package com.community.payment.service;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.time.ServiceTime;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 결제 이력·대사 이슈의 {@code detail}(jsonb)을 요청 언어로 푼 한 줄로 바꾼다(확정한 목업 가정 6) — 「이름: 값 · 이름: 값」. detail 에는 원래 개인정보·비밀값이 없다.
 * 이름은 번들 {@code payment.detail.<칸>}, 코드 값(상태·이상·종류 등)은 {@code payment.value.<값>}, 결제 수단은 {@code payment.method.<값>} 이고 번들에 없으면 그대로 보인다.
 * 금액 칸은 「12,000원」, 시각 칸은 KST {@code yyyy-MM-dd HH:mm}. 요청 스레드 안에서 쓴다({@link Messages} 가 요청 언어를 본다).
 */
@Component
public class PaymentDetailText {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> AMOUNTS = Set.of("amount", "paidAmount", "expectedAmount", "pgAmount", "pgCancelled", "localPaidAmount", "localCanceled");
    private static final Set<String> TIMES = Set.of("expiresAt", "nextAttemptAt");
    private static final Set<String> CODES = Set.of("anomaly", "kind", "via", "previousStatus", "reason", "pgStatus", "localStatus", "outcome", "by");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ServiceTime.ZONE);

    private final Messages messages;

    public PaymentDetailText(Messages messages) {
        this.messages = messages;
    }

    /** jsonb 글자 → 한 줄. 비었거나 읽을 수 없으면 null */
    public String render(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<?, ?> detail;
        try {
            if (!(JSON.readValue(json, Object.class) instanceof Map<?, ?> map) || map.isEmpty()) {
                return null;
            }
            detail = map;
        } catch (JacksonException e) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        detail.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            String name = String.valueOf(key);
            String label = messages.find(new MessageRef("payment.detail." + name));
            parts.add((label == null ? name : label) + ": " + value(name, value));
        });
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** 포트원 결제 수단 이름(예 {@code PaymentMethodCard}) → 표시 글자. 모르면 그대로 */
    public String payMethod(String raw) {
        if (raw == null) {
            return null;
        }
        String label = messages.find(new MessageRef("payment.method." + raw));
        return label == null ? raw : label;
    }

    private String value(String name, Object value) {
        if (AMOUNTS.contains(name) && value instanceof Number number) {
            return messages.get("payment.detail.won", NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(number.longValue()));
        }
        if (TIMES.contains(name) && value instanceof String text) {
            try {
                return TIME.format(Instant.parse(text));
            } catch (DateTimeParseException e) {
                return text;
            }
        }
        if (CODES.contains(name) && value instanceof String code) {
            String label = messages.find(new MessageRef("payment.value." + code));
            return label == null ? code : label;
        }
        return String.valueOf(value);
    }
}
