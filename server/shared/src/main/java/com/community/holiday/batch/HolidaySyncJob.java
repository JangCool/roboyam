package com.community.holiday.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.holiday.service.HolidaySyncService;
import com.community.zconfiguration.message.MessageRef;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 공공데이터 공휴일 받기 Java 배치 {@code holiday-sync}(사용자 지시 2026-11-05) — 매월 1일 새벽, 올해·내년을 받아 사이트 쉬는 날 표를 맞춘다({@link HolidaySyncService} — 맞추기 규칙은 거기).
 * 휴일 관리 화면 「지금 받기」와 같은 서비스다.
 *
 * <ul>
 *   <li>인증키 없음 — 실패가 아니라 건너뜀(SKIP, 결과 문구 {@code batch.result.holidaySync.noKey}).</li>
 *   <li>한 해라도 실패 — 반영한 해는 남기고 예외로 끝낸다(FAIL — 배치 실패 알림이 운영자에게 간다). 실패한 해의 표는 그대로다.</li>
 *   <li>바뀐 줄이 없으면 건수 0 이라 SKIP(문구 {@code …unchanged}), 있으면 OK.</li>
 * </ul>
 */
@Component
public class HolidaySyncJob implements BatchJob {

    private final HolidaySyncService sync;

    public HolidaySyncJob(HolidaySyncService sync) {
        this.sync = sync;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.HOLIDAY_SYNC;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        HolidaySyncService.Result result = sync.sync(null, null);
        BatchResult summary = summarize(result);
        boolean noKey = result.outcome() == HolidaySyncService.Outcome.NO_KEY;
        step.record(noKey ? 0 : result.appliedYears().size() + result.emptyYears().size(), noKey ? 0 : result.changed(), 0);
        return summary;
    }

    /**
     * 받기 결과 → 배치 결과 문구. 키 없음은 건너뜀 문구, 한 해라도 실패면 예외(사유 코드만 — 키·주소·응답 본문은 싣지 않는다).
     *
     * @throws IllegalStateException 실패한 해가 있다(PARTIAL · FAILED)
     */
    public static BatchResult summarize(HolidaySyncService.Result result) {
        if (result.outcome() == HolidaySyncService.Outcome.NO_KEY) {
            return BatchResult.of(new MessageRef("batch.result.holidaySync.noKey"));
        }
        if (result.outcome() != HolidaySyncService.Outcome.OK) {
            String failed = result.failures().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "(" + e.getValue() + ")").collect(Collectors.joining(", "));
            throw new IllegalStateException("공휴일 받기 실패한 해: " + failed + " — 반영한 해: " + years(result.appliedYears()) + ", 바뀐 줄 " + result.changed());
        }
        if (result.changed() == 0) {
            return BatchResult.of(new MessageRef("batch.result.holidaySync.unchanged", years(result.appliedYears()), years(result.emptyYears())));
        }
        return BatchResult.of(new MessageRef("batch.result.holidaySync", years(result.appliedYears()), String.valueOf(result.changed()), years(result.emptyYears())));
    }

    private static String years(List<Integer> years) {
        return years.isEmpty() ? "-" : years.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
