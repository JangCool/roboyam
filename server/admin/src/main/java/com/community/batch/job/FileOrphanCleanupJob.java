package com.community.batch.job;

import com.community.batch.catalog.BatchCatalog;
import com.community.media.config.MediaProperties;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 올리기만 하고 어디에도 지정하지 않은 채 오래된 파일({@code files.status = PENDING}, 기준 {@code roboyam.media.orphan-retention-hours} 기본 24시간)을 저장소와 장부에서 지운다.
 * 저장소에서 못 지운 파일은 장부 행을 남겨 다음 실행이 다시 시도한다. 처리 건수는 지운 파일 수. 한 번에 100건씩 돌며 지울 것이 없을 때까지 반복하되, 저장소 오류로 못 지운 것만 남으면 거기서 멈춘다.
 */
@Component
public class FileOrphanCleanupJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(FileOrphanCleanupJob.class);

    private final FileRepository files;
    private final ImageStorage storage;
    private final MediaProperties properties;

    public FileOrphanCleanupJob(FileRepository files, ImageStorage storage, MediaProperties properties) {
        this.files = files;
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
        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.orphanRetentionHours()));
        long deleted = 0;
        while (true) {
            List<FileEntity> orphans = files.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(FileStatus.PENDING, cutoff);
            long deletedInRound = 0;
            for (FileEntity orphan : orphans) {
                try {
                    storage.delete(orphan.getStorageKey());
                    files.delete(orphan);
                    deletedInRound++;
                } catch (RuntimeException e) {
                    log.warn("올리다 만 파일을 지우지 못했다(다음 실행이 다시 시도한다) fileId={}", orphan.getId(), e);
                }
            }
            files.flush();
            deleted += deletedInRound;
            if (orphans.size() < 100 || deletedInRound == 0) {
                break;
            }
        }
        return BatchResult.of(deleted, deleted == 0 ? new MessageRef("batch.result.fileOrphanCleanup.none") : new MessageRef("batch.result.fileOrphanCleanup", String.valueOf(deleted)));
    }
}
