package com.community.batch.job;

import com.community.batch.catalog.BatchCatalog;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 쓰이지 않는 파일을 저장소와 장부에서 지운다. 대상은 둘이다.
 * <ol>
 *   <li>올리기만 하고 어디에도 지정하지 않은 채 오래된 파일({@code files.status = PENDING}, 기준 {@code roboyam.media.orphan-retention-hours} 기본 24시간) —
 *       사이트 로고 후보와 글에 넣지 않은(또는 고치면서 뺀) 글 이미지.</li>
 *   <li>쓰던 글이 더는 보이지 않는 글 이미지(IN_USE) — 글을 지웠거나 예약 삭제 시각이 지났거나 글 행이 없어졌다(V56, {@link FileRepositoryQDSL#abandonedPostImages}).</li>
 * </ol>
 * 지우기 전에 행을 쓰기 잠금으로 다시 읽어 상태를 확인한다 — 같은 이미지를 글 저장이 막 붙이고 있으면(그쪽도 같은 행을 잠근다) 기다렸다가 쓰는 중이면 건너뛴다.
 * 저장소에서 못 지운 파일은 장부 행을 남겨 다음 실행이 다시 시도한다. 처리 건수는 지운 파일 수. 한 번에 100건씩 돌며 지울 것이 없을 때까지 반복하되,
 * 저장소 오류로 못 지운 것만 남으면 거기서 멈춘다.
 */
@Component
public class FileOrphanCleanupJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(FileOrphanCleanupJob.class);
    private static final int CHUNK = 100;

    private final FileRepository files;
    private final FileRepositoryQDSL fileQueries;
    private final ImageStorage storage;
    private final MediaProperties properties;

    public FileOrphanCleanupJob(FileRepository files, FileRepositoryQDSL fileQueries, ImageStorage storage, MediaProperties properties) {
        this.files = files;
        this.fileQueries = fileQueries;
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.FILE_ORPHAN_CLEANUP;
    }

    @Override
    @Transactional
    public BatchResult execute() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(properties.orphanRetentionHours()));
        long deleted = 0;
        while (true) {
            List<Long> orphans = fileQueries.pendingBefore(cutoff, CHUNK);
            long round = deleteAll(orphans, f -> f.getStatus() == FileStatus.PENDING && f.getCreatedAt().isBefore(cutoff));
            deleted += round;
            if (orphans.size() < CHUNK || round == 0) {
                break;
            }
        }
        while (true) {
            List<Long> abandoned = fileQueries.abandonedPostImages(now, CHUNK);
            long round = deleteAll(abandoned, f -> f.getStatus() == FileStatus.IN_USE);
            deleted += round;
            if (abandoned.size() < CHUNK || round == 0) {
                break;
            }
        }
        return BatchResult.of(deleted, deleted == 0 ? new MessageRef("batch.result.fileOrphanCleanup.none") : new MessageRef("batch.result.fileOrphanCleanup", String.valueOf(deleted)));
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
