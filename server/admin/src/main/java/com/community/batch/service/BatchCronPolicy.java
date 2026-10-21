package com.community.batch.service;

import com.community.batch.config.BatchProperties;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.ZonedDateTime;
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

    private static ValidationException invalid(String key) {
        return ValidationException.of("schedule", new MessageRef(key));
    }
}
