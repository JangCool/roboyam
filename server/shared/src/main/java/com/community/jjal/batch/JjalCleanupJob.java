package com.community.jjal.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.jjal.config.JjalProperties;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.entity.JjalFileStatus;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.jjal.repository.master.JjalRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 내 짤 정리(Java 배치 {@code jjal-cleanup} — 한 단계 {@code main}, 「언제 돌릴지」는 관리자 서버의 배치 실행기). 저장소 파일을 지우므로 DB 배치가 아니다
 * (my-jjal-contract 정책 4 — 파일은 가리키는 보관함 줄도, 쓰는 글·댓글도 없을 때 지운다).
 * <ol>
 *   <li>죽은 쓰임 — 지운 글(예약 삭제 시각 지남 · 글 행 없음)·지운 댓글의 {@code jjal_uses} 줄을 지운다.</li>
 *   <li>안 쓰는 파일 — 보이는(ACTIVE) 파일 중 만든 지 {@code irobit.jjal.orphan-hours}(24시간)가 지났고 보관함 줄도 쓰임도 없는 것. 파일 행을 쓰기 잠금으로 다시 읽어 확인한 뒤
 *       장부 행을 지우고(짧은 트랜잭션) 커밋 뒤 저장소 파일을 지운다(못 지우면 경고 로그 — 장부에 없는 저장소 파일이 남을 뿐 주소는 다시 쓰이지 않는다).
 *       내려간(BLOCKED) 파일은 지우지 않는다(같은 해시 다시 올리기 거절에 해시가 필요하다).</li>
 *   <li>올리기 속도 기록 — {@code irobit.jjal.upload-log-days}(2일)보다 오래된 줄.</li>
 * </ol>
 * 덩어리 처리 도우미를 쓰지 않는다(저장소 삭제는 되돌릴 수 없다 — {@code FileOrphanCleanupJob} 과 같은 까닭). 단계 건수: 읽음 = 살펴본 파일 후보 수, 처리 = 지운 파일 수, 건너뜀 = 나머지.
 */
@Component
public class JjalCleanupJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(JjalCleanupJob.class);
    private static final int CHUNK = 100;

    private final JjalFileRepository files;
    private final JjalRepositoryQDSL queries;
    private final ImageStorage storage;
    private final JjalProperties props;
    private final TransactionTemplate tx;

    public JjalCleanupJob(JjalFileRepository files, JjalRepositoryQDSL queries, ImageStorage storage, JjalProperties props, PlatformTransactionManager txManager) {
        this.files = files;
        this.queries = queries;
        this.storage = storage;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.JJAL_CLEANUP;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        Instant now = Instant.now();
        long deadUses = 0;
        while (true) {
            Long removed = tx.execute(status -> {
                List<Long> dead = queries.deadUses(now, CHUNK * 10);
                return dead.isEmpty() ? 0L : queries.deleteUsesById(dead);
            });
            long n = removed == null ? 0 : removed;
            deadUses += n;
            if (n < CHUNK * 10L) {
                break;
            }
        }

        Instant cutoff = now.minus(Duration.ofHours(props.orphanHours()));
        long examined = 0;
        long deleted = 0;
        Long lastSeen = null;
        while (true) {
            List<Long> candidates = tx.execute(status -> queries.orphanFiles(cutoff, CHUNK));
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            long round = 0;
            for (Long id : candidates) {
                examined++;
                String key = tx.execute(status -> deleteRow(id, cutoff));
                if (key != null) {
                    round++;
                    deleteStorageQuietly(id, key);
                }
            }
            deleted += round;
            Long last = candidates.get(candidates.size() - 1);
            if (candidates.size() < CHUNK || round == 0 || last.equals(lastSeen)) {
                break; // 다 봤거나 이번에 지운 것이 없다(남은 것은 그사이 다시 쓰이게 됐다)
            }
            lastSeen = last;
        }

        Long uploads = tx.execute(status -> queries.deleteUploadsBefore(now.minus(Duration.ofDays(props.uploadLogDays()))));
        step.record(examined, deleted, examined - deleted);
        log.info("내 짤 정리 deadUses={} examined={} deletedFiles={} uploadLogs={}", deadUses, examined, deleted, uploads);
        return BatchResult.of(deleted == 0 ? new MessageRef("batch.result.jjalCleanup.none") : new MessageRef("batch.result.jjalCleanup", String.valueOf(deleted)));
    }

    /** 잠가 다시 읽고 여전히 지울 것이면 장부 행을 지운다 — 지웠으면 저장소 키, 아니면 null */
    private String deleteRow(long id, Instant cutoff) {
        JjalFileEntity file = files.findWithLockById(id).orElse(null);
        if (file == null || file.getStatus() != JjalFileStatus.ACTIVE || !file.getCreatedAt().isBefore(cutoff) || queries.referenced(id)) {
            return null;
        }
        String key = file.getStorageKey();
        files.delete(file);
        files.flush();
        return key;
    }

    private void deleteStorageQuietly(long id, String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("짤 파일을 저장소에서 지우지 못했다(장부 행은 지웠다) fileId={}", id, e);
        }
    }
}
