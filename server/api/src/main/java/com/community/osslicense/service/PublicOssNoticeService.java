package com.community.osslicense.service;

import com.community.cache.definition.CacheNames;
import com.community.osslicense.dto.response.OssNoticePublicResponse;
import com.community.zconfiguration.cache.CacheReloader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 공개 오픈소스 라이선스 고지({@code GET /api/site/oss-licenses})의 <b>캐시 사본</b> — 캐시 {@code oss.notice}(기본 60초, 공지 띠·팝업과 같은 틀 —
 * docs/features/cache-admin-contract.md). 관리자 저장은 감사 기록을 보고 세대 번호가 올라 몇 초 안에 보인다.
 * 응답에 사용자별 내용이 없어 모두가 같은 사본을 받는다. {@link Snapshot#etag()} 는 사본 내용의 지문이다.
 */
@Service
public class PublicOssNoticeService implements CacheReloader {

    private final OssNoticePublicReader reader;
    private final CacheManager caches;

    public PublicOssNoticeService(OssNoticePublicReader reader, CacheManager caches) {
        this.reader = reader;
        this.caches = caches;
    }

    /** 지금 쓸 수 있는 사본 — 캐시 {@code oss.notice} */
    public Snapshot snapshot() {
        return caches.getCache(CacheNames.OSS_NOTICE).get(CacheNames.SINGLE, this::load);
    }

    private Snapshot load() {
        OssNoticePublicResponse loaded = reader.read();
        return new Snapshot(loaded, fingerprint(loaded));
    }

    @Override
    public String cacheName() {
        return CacheNames.OSS_NOTICE;
    }

    /** TTL 이 지난 뒤의 다시 읽기({@link CacheReloader}) — 처음 담는 로더와 같은 값. 실패하면 캐시가 이전 값을 쓴다 */
    @Override
    public Object reload(Object key) {
        return load();
    }

    /** 사본 내용의 지문 — 레코드의 {@code toString} 은 칸 값을 모두 싣는다 */
    private static String fingerprint(OssNoticePublicResponse response) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(response.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 한 시점의 응답과 그 지문(ETag) */
    public record Snapshot(OssNoticePublicResponse response, String etag) {
    }
}
