package com.community.notification.service;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchMessageCodec;
import com.community.batch.entity.BatchRunEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.notification.config.NotificationProperties;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.repository.master.BatchRunWatchRepositoryQDSL;
import com.community.notificationtemplate.catalog.MessageCondition;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 끝난 배치 실행 줄을 보고 알림을 낸다 — 한 번에 한 묶음을 <b>한 트랜잭션</b>으로 처리한다: 줄을 잠그고({@code FOR UPDATE SKIP LOCKED}) 알림을 만들고({@code NotificationPublisher} 가 같은 트랜잭션에 참여)
 * 줄에 처리 시각({@code notified_at})을 채운다. 알림이 만들어졌는데 표시가 안 되거나 그 반대가 되는 일이 없다.
 *
 * <ul>
 *   <li>{@code FAIL} → {@link AdminNotificationEvent#BATCH_RUN_FAILED}(배치 이름·배치 키·오류 요약 — 오류 요약은 실행 로그에 이미 민감값을 가려 둔 문장이다).
 *       같은 배치의 연속 실패는 이벤트의 묶음 시간(30분) 안에 한 건이다.</li>
 *   <li>{@code OK} 이고 처리 건수가 0보다 큰 {@code grade-promotion} → {@link AdminNotificationEvent#GRADE_PROMOTION_RUN}(승급 인원).</li>
 *   <li>그 밖의 줄(성공·건너뜀·다른 배치)은 알리지 않고 표시만 한다. 끝난 지 오래된 줄({@code batch-watch-max-age-hours})도 알리지 않고 표시만 한다.</li>
 * </ul>
 * 알림을 못 만들어도(발행기는 던지지 않는다) 표시는 한다 — 같은 줄이 30초마다 다시 실패하며 로그를 채우지 않게. 실패는 발행기가 ERROR 로 남긴다.
 */
@Service
public class BatchRunNotifier {

    private static final Logger log = LoggerFactory.getLogger(BatchRunNotifier.class);

    /** 승급 알림이 가리키는 배치 */
    private static final String PROMOTION_JOB = BatchCatalog.GRADE_PROMOTION.key();

    private final BatchRunWatchRepositoryQDSL runs;
    private final NotificationPublisher publisher;
    private final NotificationProperties properties;

    public BatchRunNotifier(BatchRunWatchRepositoryQDSL runs, NotificationPublisher publisher, NotificationProperties properties) {
        this.runs = runs;
        this.publisher = publisher;
        this.properties = properties;
    }

    /**
     * 처리할 줄을 최대 {@code limit} 개 처리한다.
     *
     * @return 처리한 줄 수(알리지 않고 표시만 한 줄 포함). {@code limit} 와 같으면 더 있을 수 있다
     */
    @Transactional
    public int notifyPending(int limit) {
        List<BatchRunEntity> pending = runs.lockPending(limit);
        if (pending.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        Instant oldest = now.minus(Duration.ofHours(properties.batchWatchMaxAgeHours()));
        for (BatchRunEntity run : pending) {
            if (run.getFinishedAt().isBefore(oldest)) {
                continue; // 너무 오래된 줄 — 알리지 않고 표시만
            }
            if (run.getStatus() == BatchRunStatus.FAIL) {
                publishFailed(run);
            } else if (run.getStatus() == BatchRunStatus.OK && PROMOTION_JOB.equals(run.getJobKey()) && run.getAffectedCount() != null && run.getAffectedCount() > 0) {
                publisher.publish(AdminNotificationEvent.GRADE_PROMOTION_RUN, NotificationContext.empty().args(String.valueOf(run.getAffectedCount())));
            }
        }
        runs.markNotified(pending.stream().map(BatchRunEntity::getId).toList(), now);
        return pending.size();
    }

    private void publishFailed(BatchRunEntity run) {
        BatchCatalog definition = BatchCatalog.ofKey(run.getJobKey());
        Object name = definition == null ? run.getJobKey() : new MessageRef(definition.nameKey()); // 카탈로그에서 사라진 배치는 키 그대로
        // 사유 글이 없으면 실패 문구 키(실패 한도 넘음·서버 멈춤)를, 그것도 없으면 「사유 없음」
        MessageRef reason = BatchMessageCodec.decode(run.getMessageKey(), run.getMessageArgs());
        Object detail = run.getErrorMessage() != null && !run.getErrorMessage().isBlank() ? run.getErrorMessage()
                : reason != null ? reason : new MessageRef("notification.batchRunFailed.noDetail");
        int published = publisher.publish(AdminNotificationEvent.BATCH_RUN_FAILED,
                NotificationContext.empty().args(name, run.getJobKey(), detail).dedupe(run.getJobKey()).link("jobName", run.getJobKey())
                        .condition(MessageCondition.JOB_NAME, run.getJobKey()));
        log.info("배치 실패 알림 jobKey={} 받는 사람={}명", run.getJobKey(), published);
    }
}
