package com.community.hotdeal.og;

import com.community.hotdeal.entity.DealOgImageEntity;
import com.community.hotdeal.repository.master.DealOgImageRepository;
import com.community.hotdeal.service.DealHosts;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * OG 이미지 사본을 저장소와 {@code deal_og_images} 에 남긴다 — 저장 순서는 이미지 공통 규칙(저장소 쓰기 → DB 트랜잭션, DB 가 실패하면 새 파일을 지운다).
 * 키 {@code deal-og/{년월}/{무작위}.{확장자}} — 내용마다 새 키라 고치지 않는다.
 */
@Component
public class DealOgImageStore {

    private static final Logger log = LoggerFactory.getLogger(DealOgImageStore.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImageStorage storage;
    private final DealOgImageRepository images;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public DealOgImageStore(ImageStorage storage, DealOgImageRepository images, SnowflakeIdGenerator ids, PlatformTransactionManager txManager) {
        this.storage = storage;
        this.images = images;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 저장한 사본 */
    public record Saved(long id, String url) {
    }

    /**
     * @param originUrl 원본 그림 주소(공개 응답에 싣지 않는다)
     * @param memberId  판정을 부른 회원
     */
    public Saved save(DealOgImageEncoder.Encoded encoded, String originUrl, Long memberId, Instant now) {
        String key = "deal-og/" + MONTH.format(now) + "/" + token() + "." + encoded.format().extension();
        storage.put(key, encoded.data());
        String url = storage.publicUrl(key);
        long id = ids.nextId();
        try {
            tx.executeWithoutResult(status -> images.persist(DealOgImageEntity.create(id, originUrl, DealHosts.hostOf(originUrl), key, url,
                    encoded.format().contentType(), encoded.data().length, encoded.width(), encoded.height(), memberId, now)));
        } catch (RuntimeException e) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                log.warn("OG 사본 파일을 되돌리지 못했다 key={}", key);
            }
            throw e;
        }
        return new Saved(id, url);
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
