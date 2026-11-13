package com.community.media.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.media.service.PostImageUploads;
import com.community.media.service.PostThumbnailMaker;
import com.community.zconfiguration.message.MessageRef;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이미 올라간 글 이미지의 목록 썸네일 채우기(Java 배치 {@code post-image-thumbnail-backfill} — 한 단계 {@code main}, docs/features/list-thumbnail-contract.md
 * 「이미 올라간 이미지 채우기」). 그림을 디코딩해야 해서 DB 배치가 아니라 Java 배치이고, 예약은 <b>기본 꺼짐</b> — 사람이 적은 시간에 관리자 배치 화면에서 「지금 실행」한다
 * (도는 곳은 배치를 켠 관리자 서버 — {@code irobit.batch.enabled}).
 *
 * <p>대상: 쓰는 중(IN_USE)이고 {@code thumbnail_key} 가 비어 있는 글 이미지·대표 썸네일 그림({@link FileRepositoryQDSL#missingThumbnails} — id 순, 100장씩 끝까지).
 * 한 장씩: 장부 확인(짧은 읽기 트랜잭션) → 저장소에서 원본 읽기 → 썸네일 만들기({@link PostThumbnailMaker} — 처리 관문 {@code ImageGate} 아래라 올리기 요청과 자리를 나눈다)
 * → 썸네일 쓰기 → 행을 잠가 다시 보고 아직 비어 있으면 키 기록(아니면 — 그사이 지워졌거나 다른 실행이 채웠으면 — 방금 쓴 썸네일을 지운다). 그래서 다시 돌려도,
 * 두 번 겹쳐 돌아도 안전하다(실행기가 같은 배치를 한 번만 돌리기도 한다).
 * 원본이 없거나 그림을 읽지 못하면 건너뛴다(다음 실행에서 다시 집힌다 — 같은 실행 안에서는 키셋이라 한 번만). 저장소에 닿지 못하거나({@code STORAGE_UNAVAILABLE})
 * 처리 자리가 나지 않으면({@code IMAGE_BUSY}) 실행을 멈춘다(FAIL — 남은 것은 다음 실행이 이어서 한다).
 * 단계 건수: 읽음 = 살펴본 파일 수, 처리 = 만든 썸네일 수, 건너뜀 = 나머지.
 *
 * <p>덩어리 처리 도우미({@link BatchStep#chunks})를 쓰지 않는다 — 저장소 쓰기는 트랜잭션으로 되돌릴 수 없고, 그림 처리 동안 DB 연결을 붙잡지 않으려고
 * ({@link FileOrphanCleanupJob} 과 같은 까닭). 그래서 건별 실패 기록·「실패 지점부터 다시」는 없다.
 */
@Component
public class PostImageThumbnailBackfillJob implements BatchJob {

    private static final Logger log = LoggerFactory.getLogger(PostImageThumbnailBackfillJob.class);
    private static final int CHUNK = 100;

    private final FileRepository files;
    private final FileRepositoryQDSL fileQueries;
    private final ImageStorage storage;
    private final PostThumbnailMaker maker;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;

    public PostImageThumbnailBackfillJob(FileRepository files, FileRepositoryQDSL fileQueries, ImageStorage storage, PostThumbnailMaker maker,
                                         PlatformTransactionManager transactions) {
        this.files = files;
        this.fileQueries = fileQueries;
        this.storage = storage;
        this.maker = maker;
        this.tx = new TransactionTemplate(transactions);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Override
    public BatchCatalog definition() {
        return BatchCatalog.POST_IMAGE_THUMBNAIL_BACKFILL;
    }

    @Override
    public BatchResult run(BatchContext context) {
        BatchStep step = context.step(BatchStepDefinition.MAIN);
        long after = 0;
        long examined = 0;
        long made = 0;
        try {
            while (true) {
                long from = after;
                List<Long> ids = readOnly.execute(status -> fileQueries.missingThumbnails(from, CHUNK));
                if (ids == null || ids.isEmpty()) {
                    break;
                }
                for (Long id : ids) {
                    after = id;
                    examined++;
                    if (fill(id)) {
                        made++;
                    }
                }
                if (ids.size() < CHUNK) {
                    break;
                }
            }
        } finally {
            step.record(examined, made, examined - made);
        }
        return BatchResult.of(made == 0 ? new MessageRef("batch.result.postImageThumbnailBackfill.none")
                : new MessageRef("batch.result.postImageThumbnailBackfill", String.valueOf(made)));
    }

    /** 한 장 — 만들어 기록했으면 true. 저장소·처리 관문 오류는 그대로 던진다(실행을 멈춘다) */
    private boolean fill(long fileId) {
        Optional<String> key = readOnly.execute(status -> files.findById(fileId)
                .filter(f -> f.getStatus() == FileStatus.IN_USE && f.getThumbnailKey() == null).map(FileEntity::getStorageKey));
        if (key == null || key.isEmpty()) {
            return false;
        }
        String storageKey = key.get();
        Optional<byte[]> data = storage.readBytes(storageKey);
        if (data.isEmpty()) {
            log.warn("썸네일 채우기 — 저장소에 원본이 없다 fileId={}", fileId);
            return false;
        }
        Optional<PostThumbnailMaker.Thumbnail> thumb = maker.make(data.get(), "fileId=" + fileId);
        if (thumb.isEmpty()) {
            return false;
        }
        String thumbKey = PostImageUploads.thumbnailKeyOf(storageKey, thumb.get().format());
        storage.put(thumbKey, thumb.get().data());
        Boolean recorded;
        try {
            recorded = tx.execute(status -> {
                FileEntity file = files.findWithLockById(fileId).orElse(null);
                if (file == null || file.getThumbnailKey() != null || !storageKey.equals(file.getStorageKey())) {
                    return file != null && thumbKey.equals(file.getThumbnailKey());
                }
                file.recordThumbnail(thumbKey);
                return true;
            });
        } catch (RuntimeException e) {
            deleteQuietly(thumbKey);
            throw e;
        }
        if (!Boolean.TRUE.equals(recorded)) {
            deleteQuietly(thumbKey); // 그사이 지워졌거나 다른 키로 채워졌다 — 방금 쓴 것만 지운다
            return false;
        }
        return true;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("썸네일 채우기 — 쓰고 기록하지 못한 썸네일을 지우지 못했다 key={}", key, e);
        }
    }
}
