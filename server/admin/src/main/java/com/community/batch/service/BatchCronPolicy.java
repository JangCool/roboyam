package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * 배치 cron 식의 검증과 다음 실행 시각 계산. 형식은 Spring 6칸(초 분 시 일 월 요일), 시간대는 {@code roboyam.batch.zone}.
 *
 * <p>안전장치(가정): ① 6칸이 아니거나 해석할 수 없으면 거절 ② 앞으로 실행될 시각이 하나도 없으면 거절(예: 2월 30일) ③ 앞으로 {@value #SAMPLE}번의 실행에서 인접한 실행
 * 간격이 {@code roboyam.batch.min-interval-seconds}(기본 60초)보다 짧으면 거절 — 초 칸에 여러 값이나 {@code *} 를 쓰는 식이 여기에 걸린다. 입력은 {@code schedule} 칸의 오류(400
 * {@code VALIDATION})로 알린다.
 */
@Component
public class BatchCronPolicy {

    private static final int MAX_LENGTH = 100;
    private static final int SAMPLE = 64;

    private static final Pattern PG_SECONDS = Pattern.compile("(\\d{1,3}) seconds?");
    private static final Pattern PG_FIELD = Pattern.compile("[0-9A-Za-z*/,\\-]+");
    private static final Pattern PG_NAME = Pattern.compile("[A-Za-z]+");
    private static final Set<String> PG_NAMES = Set.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
            "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");

    private final BatchProperties properties;

    public BatchCronPolicy(BatchProperties properties) {
        this.properties = properties;
    }

    /** 식을 검사하고 공백을 한 칸으로 정리해 돌려준다. 틀리면 {@code schedule} 칸 오류로 던진다 */
    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("batch.schedule.required");
        }
        String cron = String.join(" ", raw.strip().split("\\s+"));
        if (cron.length() > MAX_LENGTH || cron.split(" ").length != 6 || !CronExpression.isValidExpression(cron)) {
            throw invalid("batch.schedule.format");
        }
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime previous = expression.next(ZonedDateTime.now(properties.zoneId()));
        if (previous == null) {
            throw invalid("batch.schedule.never");
        }
        Duration min = Duration.ofSeconds(properties.minIntervalSeconds());
        for (int i = 1; i < SAMPLE; i++) {
            ZonedDateTime next = expression.next(previous);
            if (next == null) {
                break;
            }
            if (Duration.between(previous, next).compareTo(min) < 0) {
                throw ValidationException.of("schedule", new MessageRef("batch.schedule.tooFrequent", String.valueOf(properties.minIntervalSeconds())));
            }
            previous = next;
        }
        return cron;
    }

    /** 이 식의 다음 실행 시각({@code from} 이후). 식이 틀렸거나 더는 실행되지 않으면 null */
    public ZonedDateTime next(String cron, ZonedDateTime from) {
        if (!CronExpression.isValidExpression(cron)) {
            return null;
        }
        return CronExpression.parse(cron).next(from);
    }

    /**
     * <b>DB 배치(pg_cron)</b> 식의 검증. 형식은 표준 5칸(분 시 일 월 요일)이고 시간대는 pg_cron 의 {@code cron.timezone} 이다. 공백을 한 칸으로 정리해 돌려준다.
     *
     * <p>pg_cron 이 받아들이는 문법 중 이 서버가 허용하는 범위: 숫자·{@code *}·{@code ,}·{@code -}·{@code /}, 월·요일 영문 이름(JAN~DEC · SUN~SAT), 일 칸의 {@code $}(말일).
     * Spring 에는 있고 pg_cron 에는 없는 {@code ? L W #} 와 {@code @daily} 같은 약어는 거절한다. {@code "N seconds"} 형식은 인식하되 간격 하한({@code min-interval-seconds})보다
     * 짧아 거절한다(초 단위 실행은 하한 때문에 쓸 수 없다). 안전장치는 Java 배치와 같다 — 앞으로 실행 시각이 없거나 앞으로 64번의 실행에서 인접 간격이 하한보다 짧으면 거절한다.
     */
    public String validatePgCron(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("batch.schedule.required");
        }
        String cron = String.join(" ", raw.strip().split("\\s+"));
        if (cron.length() > MAX_LENGTH) {
            throw invalid("batch.schedule.formatDb");
        }
        Matcher seconds = PG_SECONDS.matcher(cron);
        if (seconds.matches()) {
            int value = Integer.parseInt(seconds.group(1));
            if (value < 1 || value > 59) {
                throw invalid("batch.schedule.formatDb");
            }
            if (value < properties.minIntervalSeconds()) {
                throw ValidationException.of("schedule", new MessageRef("batch.schedule.tooFrequent", String.valueOf(properties.minIntervalSeconds())));
            }
            return cron;
        }
        CronExpression expression = pgCronExpression(cron);
        if (expression == null) {
            throw invalid("batch.schedule.formatDb");
        }
        ZonedDateTime previous = expression.next(ZonedDateTime.now(properties.zoneId()));
        if (previous == null) {
            throw invalid("batch.schedule.never");
        }
        Duration min = Duration.ofSeconds(properties.minIntervalSeconds());
        for (int i = 1; i < SAMPLE; i++) {
            ZonedDateTime next = expression.next(previous);
            if (next == null) {
                break;
            }
            if (Duration.between(previous, next).compareTo(min) < 0) {
                throw ValidationException.of("schedule", new MessageRef("batch.schedule.tooFrequent", String.valueOf(properties.minIntervalSeconds())));
            }
            previous = next;
        }
        return cron;
    }

    /** DB 배치 식({@code zone} 의 시간대 기준)의 다음 실행 시각. 읽을 수 없거나 초 단위 식이거나 더는 실행되지 않으면 null */
    public ZonedDateTime nextPgCron(String cron, ZonedDateTime from) {
        CronExpression expression = pgCronExpression(cron);
        return expression == null ? null : expression.next(from);
    }

    /** pg_cron 5칸 식을 Spring 식으로 옮겨 해석한다. pg_cron 이 받지 않는 문법이면 null */
    private static CronExpression pgCronExpression(String cron) {
        String[] fields = cron.split(" ");
        if (fields.length != 5) {
            return null;
        }
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            boolean lastDay = i == 2 && field.equals("$"); // 일 칸의 $ = 말일(Spring 의 L)
            if (!lastDay && !PG_FIELD.matcher(field).matches()) {
                return null;
            }
            Matcher names = PG_NAME.matcher(field);
            while (names.find()) {
                if (!(i == 3 || i == 4) || !PG_NAMES.contains(names.group().toUpperCase(Locale.ROOT))) {
                    return null; // 월·요일 칸의 영문 이름만 허용(L · W 같은 Spring 전용 글자 거절)
                }
            }
            fields[i] = lastDay ? "L" : field;
        }
        String spring = "0 " + String.join(" ", fields);
        return CronExpression.isValidExpression(spring) ? CronExpression.parse(spring) : null;
    }

    private static ValidationException invalid(String key) {
        return ValidationException.of("schedule", new MessageRef(key));
    }
}
