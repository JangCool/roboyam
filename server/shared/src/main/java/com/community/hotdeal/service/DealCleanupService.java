package com.community.hotdeal.service;

import com.community.hotdeal.entity.DealOgImageEntity;
import com.community.hotdeal.repository.master.DealCleanupRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 핫딜 정리(배치 {@code hotdeal-cleanup}) — OG 사본(D1)과 딜 칸 수정 이력(D3). 덩어리마다 트랜잭션을 확정하고, 저장소 파일은 <b>확정 뒤</b> 지운다(되돌릴 수 없는 일 — 실패하면 로그만,
 * 장부는 이미 「지움」이라 다음 실행이 다시 집지 않는다 — 남은 파일은 저장소 정리 몫).
 *
 * <ul>
 *   <li><b>OG 사본</b>: 아직 지우지 않은 사본 중 그 사본을 쓰는 딜(같은 사본을 여러 글이 쓸 수 있다 — 끝난 딜에서 새 딜 올리기)이 <b>하나도 지키지 않는</b> 것. 딜이 사본을 지키는 때 =
 *       글이 남아 있고(지우지 않음) 딜이 진행 중(저장 상태 ACTIVE — 기간 지남 포함, 고칠 수 있다)이거나 종료 뒤 {@value #KEEP_DAYS}일 안 · 또는 글을 지운 지 {@value #KEEP_DAYS}일 안.
 *       글이 없어진 딜 줄은 지키지 않는다. 또 만든 지 {@value #ORPHAN_DAYS}일 안의 사본은 늘 남긴다(글쓰기 도중 — 판정만 하고 글에 붙지 않은 사본의 기한).
 *       지울 때 그 사본을 가리키던 (끝난) 딜의 이미지 칸을 비운다(없는 주소를 보이지 않게).</li>
 *   <li><b>수정 이력</b>: 만든 지 {@value #REVISION_MONTHS}개월(한국 달력) 지난 줄.</li>
 * </ul>
 */
@Service
public class DealCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DealCleanupService.class);

    /** 딜 종료·글 삭제 뒤 사본을 남기는 날 수(D1) */
    public static final int KEEP_DAYS = 90;
    /** 글에 붙지 않은(또는 아무 딜도 쓰지 않는) 사본을 남기는 날 수 — 계약에 없어 정했다(보고 「미확정」) */
    public static final int ORPHAN_DAYS = 7;
    /** 수정 이력 보존(D3) */
    public static final int REVISION_MONTHS = 6;
    static final int CHUNK = 100;
    static final int REVISION_CHUNK = 5000;

    private final DealCleanupRepositoryQDSL cleanup;
    private final ImageStorage storage;
    private final TransactionTemplate tx;

    public DealCleanupService(DealCleanupRepositoryQDSL cleanup, ImageStorage storage, PlatformTransactionManager transactions) {
        this.cleanup = cleanup;
        this.storage = storage;
        this.tx = new TransactionTemplate(transactions);
    }

    /** 사본 정리 결과 — 살펴본 · 지운 수 */
    public record OgTally(long examined, long removed) {
    }

    /** 사본 한 덩어리 — 지운 사본의 저장 키 */
    private record Batch(int picked, List<String> keys) {
    }

    /** OG 사본 정리 — 지울 것이 없을 때까지 덩어리로 */
    public OgTally cleanOgImages(Instant now) {
        long examined = 0;
        long removed = 0;
        while (true) {
            Batch batch = tx.execute(status -> ogChunk(now));
            if (batch == null || batch.picked() == 0) {
                break;
            }
            examined += batch.picked();
            removed += batch.keys().size();
            for (String key : batch.keys()) {
                try {
                    storage.delete(key);
                } catch (RuntimeException e) {
                    log.warn("OG 사본 파일을 지우지 못했다(장부는 지움) 원인={}", e.getClass().getSimpleName());
                }
            }
            if (batch.picked() < CHUNK) {
                break;
            }
        }
        return new OgTally(examined, removed);
    }

    private Batch ogChunk(Instant now) {
        Instant keepFrom = now.minus(Duration.ofDays(KEEP_DAYS));
        Instant orphanBefore = now.minus(Duration.ofDays(ORPHAN_DAYS));
        // 지킬 딜이 없는 사본을 잠가 고른다(다른 실행·글 저장과 겹치면 건너뛴다)
        List<DealOgImageEntity> picked = cleanup.lockRemovableOgImages(orphanBefore, keepFrom, CHUNK);
        if (picked.isEmpty()) {
            return new Batch(0, List.of());
        }
        // 고른 줄은 이 트랜잭션이 잠갔고 「아직 안 지움」으로 골랐다 — 표시하기 전에 읽은 저장 키가 표시한 줄의 키와 같다(예전 UPDATE … RETURNING 과 같은 결과)
        List<Long> ids = picked.stream().map(DealOgImageEntity::getId).toList();
        List<String> keys = picked.stream().map(DealOgImageEntity::getStorageKey).toList();
        cleanup.markOgImagesRemoved(ids, now);
        cleanup.clearDealImages(ids, now);
        return new Batch(ids.size(), keys);
    }

    /** 수정 이력 정리 — 지운 줄 수 */
    public long cleanRevisions(Instant now) {
        Instant before = ServiceTime.dateOf(now).atStartOfDay(ServiceTime.ZONE).minusMonths(REVISION_MONTHS).toInstant();
        long total = 0;
        while (true) {
            Long n = tx.execute(status -> {
                List<Long> ids = cleanup.oldRevisionIds(before, REVISION_CHUNK);
                return ids.isEmpty() ? 0L : cleanup.deleteRevisions(ids);
            });
            long deleted = n == null ? 0 : n;
            total += deleted;
            if (deleted < REVISION_CHUNK) {
                return total;
            }
        }
    }
}
