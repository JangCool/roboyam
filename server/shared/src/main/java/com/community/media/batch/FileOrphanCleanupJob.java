package com.community.media.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.media.config.MediaProperties;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 쓰이지 않는 파일을 저장소와 장부에서 지운다(Java 배치 {@code file-orphan-cleanup} — 한 단계 {@code main}). 대상은 둘이다.
 * <ol>
 *   <li>올리기만 하고 어디에도 지정하지 않은 채 오래된 파일({@code files.status = PENDING}, 기준 {@code roboyam.media.orphan-retention-hours} 기본 24시간) —
 *       사이트 로고 후보와 글에 넣지 않은(또는 고치면서 뺀) 글 이미지.</li>
 *   <li>쓰던 글이 더는 보이지 않는 글 이미지(IN_USE) — 글을 지웠거나 예약 삭제 시각이 지났거나 글 행이 없어졌다(V56, {@link FileRepositoryQDSL#abandonedPostImages}).</li>
 * </ol>
 * 지우기 전에 행을 쓰기 잠금으로 다시 읽어 상태를 확인한다 — 같은 이미지를 글 저장이 막 붙이고 있으면(그쪽도 같은 행을 잠근다) 기다렸다가 쓰는 중이면 건너뛴다.
 * 저장소에서 못 지운 파일은 장부 행을 남겨 다음 실행이 다시 시도한다. 한 번에 100건씩 돌며 지울 것이 없을 때까지 반복하되, 저장소 오류로 못 지운 것만 남으면 거기서 멈춘다.
 * 단계 건수: 읽음 = 살펴본 후보 수, 처리 = 지운 파일 수, 건너뜀 = 나머지(이미 쓰는 중·못 지움).
 *
 * <p>덩어리 처리 도우미({@link BatchStep#chunks})를 쓰지 않는다 — 저장소 삭제는 트랜잭션으로 되돌릴 수 없어 「덩어리를 되돌리고 한 건씩 다시」가 맞지 않는다.
 * 그래서 건별 실패를 기록하지 않고 「실패 지점부터 다시」도 지원하지 않는다(다음 실행이 남은 것을 다시 집는다). 일 전체는 예전처럼 한 트랜잭션이다.
 */
@Component
public class FileOrphanCleanupJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(FileOrphanCleanupJob.class);
    private static final int CHUNK = 100;

    private final FileRepository files;
    private final FileRepositoryQDSL fileQueries;
    private final ImageStorage storage;
    private final MediaProperties properties;
    private final TransactionTemplate transaction;

    public FileOrphanCleanupJob(FileRepository files, FileRepositoryQDSL fileQueries, ImageStorage storage, MediaProperties properties,
                                PlatformTransactionManager transactions) {
        this.files = files;
        this.fileQueries = fileQueries;
        this.storage = storage;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.FILE_ORPHAN_CLEANUP;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        long[] counts = transaction.execute(status -> deleteOrphans());
        long examined = counts == null ? 0 : counts[0];
        long deleted = counts == null ? 0 : counts[1];
        step.record(examined, deleted, examined - deleted);
        return BatchResult.of(deleted == 0 ? new MessageRef("batch.result.fileOrphanCleanup.none") : new MessageRef("batch.result.fileOrphanCleanup", String.valueOf(deleted)));
    }

    /** {살펴본 후보 수, 지운 수} */
    private long[] deleteOrphans() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(properties.orphanRetentionHours()));
        long examined = 0;
        long deleted = 0;
        while (true) {
            List<Long> orphans = fileQueries.pendingBefore(cutoff, CHUNK);
            long round = deleteAll(orphans, f -> f.getStatus() == FileStatus.PENDING && f.getCreatedAt().isBefore(cutoff));
            examined += orphans.size();
            deleted += round;
            if (orphans.size() < CHUNK || round == 0) {
                break;
            }
        }
        while (true) {
            List<Long> abandoned = fileQueries.abandonedPostImages(now, CHUNK);
            long round = deleteAll(abandoned, f -> f.getStatus() == FileStatus.IN_USE);
            examined += abandoned.size();
            deleted += round;
            if (abandoned.size() < CHUNK || round == 0) {
                break;
            }
        }
        return new long[] {examined, deleted};
    }

    /** 잠가 다시 읽고 여전히 대상이면 저장소 → 장부 순으로 지운다. 지운 수 */
    private long deleteAll(List<Long> candidates, Predicate<FileEntity> stillOrphan) {
        long count = 0;
        for (Long candidate : candidates) {
            FileEntity file = files.findWithLockById(candidate).orElse(null);
            if (file == null || !stillOrphan.test(file)) {
                continue;
            }
            try {
                storage.delete(file.getStorageKey());
                files.delete(file);
                count++;
            } catch (RuntimeException e) {
                log.warn("쓰이지 않는 파일을 지우지 못했다(다음 실행이 다시 시도한다) fileId={}", file.getId(), e);
            }
        }
        files.flush();
        return count;
    }
}
