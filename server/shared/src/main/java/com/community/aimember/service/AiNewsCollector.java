package com.community.aimember.service;

import com.community.aimember.entity.AiNewsItemEntity;
import com.community.aimember.entity.AiNewsSourceEntity;
import com.community.aimember.repository.master.AiNewsItemRepository;
import com.community.aimember.repository.master.AiNewsSourceRepository;
import com.community.batch.core.BatchLockService;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 뉴스 소재 수집(ai-news-reporter-contract 「소재 수집(키 없음)」) — admin 서버의 Java 배치 {@code ai-news-collect}(기본 30분마다)와 관리자 「지금 수집」이 부른다(api 서버에는 배치가 없다).
 *
 * <ul>
 *   <li>켜진 출처마다 피드를 받는다(<b>트랜잭션 밖</b> — 연결 {@value #CONNECT_SECONDS}초 · 응답 {@value #REQUEST_SECONDS}초 · 최대 {@value #MAX_BYTES}바이트, http·https 만).
 *       읽기는 {@link AiNewsFeeds#parse}(제목·링크·짧은 요약·출처 이름·발행 시각만 — 본문·사진은 받지 않는다).</li>
 *   <li>넣기: 이미 받은 링크 · 발행 시각이 {@value #KEEP_DAYS}일보다 오래된 항목은 버린다. 출처 줄에 마지막 수집 시각·읽은 수, 실패면 짧은 사유(HTTP 상태 · TIMEOUT · NOT_FEED …).</li>
 *   <li>끝에 받은 지 {@value #KEEP_DAYS}일 지난 항목을 지운다.</li>
 *   <li>배치와 「지금 수집」이 겹치지 않게 DB 어드바이저리 잠금({@value #LOCK_KEY}) — 못 잡으면 {@code busy}.</li>
 * </ul>
 */
@Component
public class AiNewsCollector {

    private static final Logger log = LoggerFactory.getLogger(AiNewsCollector.class);

    static final String LOCK_KEY = "ai-news-collect-run";
    static final int KEEP_DAYS = 7;
    static final int CONNECT_SECONDS = 10;
    static final int REQUEST_SECONDS = 20;
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; RSS reader)";

    private final AiNewsSourceRepository sources;
    private final AiNewsItemRepository items;
    private final BatchLockService locks;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final HttpClient http;

    public AiNewsCollector(AiNewsSourceRepository sources, AiNewsItemRepository items, BatchLockService locks, SnowflakeIdGenerator ids,
                           PlatformTransactionManager txManager) {
        this.sources = sources;
        this.items = items;
        this.locks = locks;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_SECONDS)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * 한 번 수집한 결과.
     *
     * @param sources 본 출처 수(켜진 것)
     * @param failed  실패한 출처 수
     * @param added   새로 넣은 항목 수
     * @param deleted 보관 기간이 지나 지운 항목 수
     * @param busy    다른 곳에서 수집 중이라 건너뛰었다
     */
    public record Result(int sources, int failed, int added, int deleted, boolean busy) {
    }

    /** 켜진 출처를 모두 받는다 */
    public Result collectAll() {
        try (BatchLockService.BatchLock lock = locks.tryAcquire(LOCK_KEY).orElse(null)) {
            if (lock == null) {
                return new Result(0, 0, 0, 0, true);
            }
            List<AiNewsSourceEntity> on = tx.execute(status -> sources.findByEnabledTrueOrderBySortOrderAscIdAsc());
            int failed = 0;
            int added = 0;
            for (AiNewsSourceEntity s : on == null ? List.<AiNewsSourceEntity>of() : on) {
                Fetched f = fetch(s);
                if (f.error() != null) {
                    failed++;
                    log.info("뉴스 출처 수집 실패 sourceId={} 사유={}", s.getId(), f.error());
                }
                Integer n = tx.execute(status -> store(s.getId(), f));
                added += n == null ? 0 : n;
            }
            Instant before = Instant.now().minus(Duration.ofDays(KEEP_DAYS));
            Integer deleted = tx.execute(status -> items.deleteFetchedBefore(before));
            return new Result(on == null ? 0 : on.size(), failed, added, deleted == null ? 0 : deleted, false);
        }
    }

    /** 받은 결과 — 실패면 {@code error}(짧은 사유) */
    private record Fetched(List<AiNewsFeeds.FeedItem> items, String error) {
    }

    /** 피드 받기·읽기(트랜잭션 밖) */
    private Fetched fetch(AiNewsSourceEntity s) {
        String url = AiNewsFeeds.feedUrl(s.getKind(), s.getAddress());
        if (url == null) {
            return new Fetched(List.of(), "BAD_ADDRESS");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(REQUEST_SECONDS)).header("User-Agent", USER_AGENT)
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5").GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                if (res.statusCode() / 100 != 2) {
                    return new Fetched(List.of(), "HTTP " + res.statusCode());
                }
                byte[] body = in.readNBytes(MAX_BYTES + 1);
                if (body.length > MAX_BYTES) {
                    return new Fetched(List.of(), "TOO_LARGE");
                }
                return new Fetched(AiNewsFeeds.parse(body, s.getKind(), s.getName()), null);
            }
        } catch (AiNewsFeeds.FeedException e) {
            return new Fetched(List.of(), e.code());
        } catch (HttpTimeoutException e) {
            return new Fetched(List.of(), "TIMEOUT");
        } catch (IOException | IllegalArgumentException e) {
            return new Fetched(List.of(), "NETWORK " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Fetched(List.of(), "INTERRUPTED");
        }
    }

    /** 넣기 + 출처 줄 결과 — 새로 넣은 수. 출처가 그사이 지워졌으면 0 */
    private int store(long sourceId, Fetched f) {
        AiNewsSourceEntity s = sources.findById(sourceId).orElse(null);
        if (s == null) {
            return 0;
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (f.error() != null) {
            s.failed(f.error(), now);
            return 0;
        }
        s.fetched(f.items().size(), now);
        Instant oldest = now.minus(Duration.ofDays(KEEP_DAYS));
        Map<String, AiNewsFeeds.FeedItem> byLink = new LinkedHashMap<>();
        for (AiNewsFeeds.FeedItem it : f.items()) {
            if (it.publishedAt() != null && it.publishedAt().isBefore(oldest)) {
                continue;
            }
            byLink.putIfAbsent(it.link(), it);
        }
        if (byLink.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(items.findExistingLinks(byLink.keySet()));
        List<AiNewsItemEntity> fresh = new ArrayList<>();
        for (AiNewsFeeds.FeedItem it : byLink.values()) {
            if (!existing.contains(it.link())) {
                fresh.add(AiNewsItemEntity.create(ids.nextId(), sourceId, it.title(), it.link(), it.summary(), it.sourceName().isBlank() ? s.getName() : it.sourceName(),
                        it.publishedAt() == null ? null : it.publishedAt().truncatedTo(ChronoUnit.MICROS), now));
            }
        }
        fresh.forEach(items::persist);
        return fresh.size();
    }
}
