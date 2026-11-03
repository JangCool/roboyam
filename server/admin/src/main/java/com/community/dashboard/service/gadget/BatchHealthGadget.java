package com.community.dashboard.service.gadget;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchMessageCodec;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardBatchHealthView;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 배치 상태 — 최근 24시간에 시작해 실패한 실행(최근 차례, 최대 10) + 실행 중. 이름·문구는 배치 로그 목록({@code BatchQueryService})과 같다
 * (카탈로그에서 사라진 배치는 키 그대로, 문구 = 오류 문구가 있으면 그것, 없으면 결과 요약).
 */
@Component
public class BatchHealthGadget implements DashboardGadgetProvider {

    static final Duration WINDOW = Duration.ofHours(24);
    static final int MAX = 10;

    private final DashboardGadgetStore store;
    private final Messages messages;

    public BatchHealthGadget(DashboardGadgetStore store, Messages messages) {
        this.store = store;
        this.messages = messages;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.BATCH_HEALTH;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardBatchHealthView(
                store.failedBatchRuns(query.now().minus(WINDOW), MAX).stream()
                        .map(r -> new DashboardBatchHealthView.Failed(Long.toString(r.id()), nameOf(r.jobKey()), r.at(), message(r))).toList(),
                store.runningBatchRuns(MAX).stream().map(r -> new DashboardBatchHealthView.Running(Long.toString(r.id()), nameOf(r.jobKey()), r.at())).toList());
    }

    private String nameOf(String jobKey) {
        BatchCatalog definition = BatchCatalog.ofKey(jobKey);
        return definition == null ? jobKey : messages.get(new MessageRef(definition.nameKey()));
    }

    private String message(DashboardGadgetStore.BatchRun run) {
        if (run.errorMessage() != null) {
            return run.errorMessage();
        }
        MessageRef summary = BatchMessageCodec.decode(run.messageKey(), run.messageArgs());
        return summary == null ? null : messages.get(summary);
    }
}
