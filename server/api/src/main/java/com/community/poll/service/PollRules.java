package com.community.poll.service;

import com.community.poll.dto.request.PollInput;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 투표 설정 검사(community {@code PollService#validate} 를 옮긴 것 — 임시저장·예약 발행은 없다). docs/features/post-poll-contract.md 「규칙」.
 * 오류는 글 칸 오류와 같은 맵에 {@code poll.question}·{@code poll.options}·{@code poll.closesAt} 로 모은다(칸마다 첫 문구).
 */
public final class PollRules {

    public static final int QUESTION_MAX = 100;
    public static final int OPTIONS_MIN = 2;
    public static final int OPTIONS_MAX = 6;
    public static final int LABEL_MAX = 50;
    /** 마감 시각은 지금부터 이만큼 뒤 이후 */
    public static final Duration CLOSE_LEAD_MIN = Duration.ofMinutes(1);
    /** 마감 시각은 지금부터 이 날수 안 */
    public static final int CLOSE_LEAD_MAX_DAYS = 365;

    /**
     * 검사를 마친 투표 설정.
     *
     * @param options  빈 칸을 뺀 선택지(차례대로)
     * @param closesAt 마감 시각(마이크로초까지 — DB 와 같은 정밀도라 다시 보낸 같은 값이 같게 보인다), 없으면 null
     */
    public record Valid(String question, List<String> options, boolean multiple, Instant closesAt, boolean changeAllowed) {
    }

    private PollRules() {
    }

    /**
     * @param unchangedClose 고치기면 지금 투표의 마감 시각 — 같은 값이면 이미 지났어도 다시 검사하지 않는다(고치지 않은 것). 쓰기면 null
     * @return 검사한 값(칸 오류가 있어도 돌려준다 — 부르는 쪽이 {@code fields} 를 보고 거절한다)
     */
    public static Valid check(PollInput input, Instant now, Instant unchangedClose, Map<String, MessageRef> fields) {
        String question = clean(input.question());
        int questionLength = question.codePointCount(0, question.length());
        if (questionLength == 0) {
            fields.put("poll.question", new MessageRef("poll.question.required"));
        } else if (questionLength > QUESTION_MAX) {
            fields.put("poll.question", new MessageRef("poll.question.tooLong", QUESTION_MAX));
        }

        List<String> options = new ArrayList<>();
        for (String raw : input.options() == null ? List.<String>of() : input.options()) {
            String label = clean(raw);
            if (!label.isEmpty()) {
                options.add(label);
            }
        }
        if (options.size() < OPTIONS_MIN) {
            fields.put("poll.options", new MessageRef("poll.options.tooFew", OPTIONS_MIN));
        } else if (options.size() > OPTIONS_MAX) {
            fields.put("poll.options", new MessageRef("poll.options.tooMany", OPTIONS_MAX));
        } else if (options.stream().anyMatch(o -> o.codePointCount(0, o.length()) > LABEL_MAX)) {
            fields.put("poll.options", new MessageRef("poll.options.tooLong", LABEL_MAX));
        } else if (options.stream().map(o -> o.toLowerCase(Locale.ROOT)).distinct().count() != options.size()) {
            fields.put("poll.options", new MessageRef("poll.options.duplicate"));
        }

        Instant closesAt = null;
        if (input.closesAt() != null && !input.closesAt().isBlank()) {
            try {
                closesAt = OffsetDateTime.parse(input.closesAt().strip()).toInstant().truncatedTo(ChronoUnit.MICROS);
            } catch (DateTimeParseException e) {
                fields.put("poll.closesAt", new MessageRef("poll.closesAt.invalid"));
            }
            if (closesAt != null && !closesAt.equals(unchangedClose)) {
                if (closesAt.isBefore(now.plus(CLOSE_LEAD_MIN))) {
                    fields.put("poll.closesAt", new MessageRef("poll.closesAt.tooSoon", CLOSE_LEAD_MIN.toMinutes()));
                } else if (closesAt.isAfter(now.plus(Duration.ofDays(CLOSE_LEAD_MAX_DAYS)))) {
                    fields.put("poll.closesAt", new MessageRef("poll.closesAt.tooFar", CLOSE_LEAD_MAX_DAYS));
                }
            }
        }
        return new Valid(question, List.copyOf(options), Boolean.TRUE.equals(input.multiple()), closesAt, Boolean.TRUE.equals(input.changeAllowed()));
    }

    /** 이 칸 오류 맵에 투표 칸 오류가 있는가 */
    static boolean hasErrors(Map<String, MessageRef> fields) {
        return fields.keySet().stream().anyMatch(k -> k.equals("poll") || k.startsWith("poll."));
    }

    /** 앞뒤·겹친 공백을 하나로, NUL 은 뺀다 */
    private static String clean(String raw) {
        return raw == null ? "" : raw.replace("\u0000", "").replaceAll("\\s+", " ").strip();
    }
}
