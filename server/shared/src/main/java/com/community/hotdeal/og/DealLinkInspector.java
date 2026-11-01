package com.community.hotdeal.og;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.og.DealPageFetcher.Outcome;
import com.community.hotdeal.og.DealPageFetcher.Page;
import com.community.hotdeal.service.DealHosts;
import com.community.hotdeal.service.DealSettingsReader;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 상품 링크 살펴보기(서버 규칙 1·2) — 리디렉션 체인(판정 대상)과 OG(제목·쇼핑몰 이름·그림 사본)를 가져와 <b>캐시</b>한다. link-check 와 글 저장(재판정)이 같은 결과를 쓴다.
 *
 * <ul>
 *   <li><b>전용 풀</b>: 가져오기는 동시 {@value #POOL_SIZE}개까지(대기 줄 {@value #QUEUE_SIZE}, 넘치면 바로 실패) — 요청 스레드는 결과를 기다리기만 한다.</li>
 *   <li><b>캐시</b>: 같은 주소는 {@link #OK_TTL}(24시간) 동안 다시 가져오지 않는다. 가져오지 못했으면 {@link #FAIL_TTL}(10분). 체인만 가져온 결과(글 저장)는 OG 가 필요한
 *       판정(link-check)이 오면 다시 가져온다. 서버 메모리 사본이라 서버마다 따로다(최대 {@value #CACHE_MAX}개).</li>
 *   <li><b>OG</b>: 설정 {@code ogFetchEnabled} 가 꺼져 있으면 {@code OFF}(체인은 그대로 따라간다 — 판정에 필요하다). 그림은 같은 클라이언트·같은 검사로 받아
 *       {@link DealOgImageEncoder} 로 사본을 만든다 — 원본 이미지 직접 링크 금지(D1).</li>
 * </ul>
 * 회원 분당 10회 한도는 link-check API 쪽({@code DealLinkCheckLimiter})이 본다.
 */
@Component
public class DealLinkInspector {

    private static final Logger log = LoggerFactory.getLogger(DealLinkInspector.class);

    public static final int POOL_SIZE = 4;
    static final int QUEUE_SIZE = 32;
    static final int CACHE_MAX = 5000;
    public static final Duration OK_TTL = Duration.ofHours(24);
    public static final Duration FAIL_TTL = Duration.ofMinutes(10);
    /** 요청 스레드가 기다리는 한도 — 페이지(6초) + 그림(6초) + 사본 만들기 */
    static final Duration WAIT = Duration.ofSeconds(15);

    public static final String OG_OK = "OK";
    public static final String OG_NONE = "NONE";
    public static final String OG_FAILED = "FAILED";
    public static final String OG_OFF = "OFF";

    /** OG 결과 — {@code imageUrl} 은 우리 서버 사본 주소 */
    public record Og(String status, Long imageId, String imageUrl, String title, String storeName) {

        static final Og OFF = new Og(OG_OFF, null, null, null, null);
        static final Og FAILED = new Og(OG_FAILED, null, null, null, null);
    }

    /**
     * 살펴본 결과.
     *
     * @param hosts 체인의 모든 호스트(처음 → 최종) — 가져오지 못했으면 처음 호스트 하나
     * @param og    OG(체인만 가져왔고 OG 를 원하지 않았으면 {@code OFF}·이전 결과)
     */
    public record Inspection(String url, List<String> hosts, Outcome outcome, Og og) {
    }

    /** 사본 저장(운영 {@link DealOgImageStore#save}) */
    @FunctionalInterface
    public interface OgSaver {
        DealOgImageStore.Saved save(DealOgImageEncoder.Encoded encoded, String originUrl, Long memberId, Instant now);
    }

    private record Entry(Inspection inspection, boolean ogLoaded, Instant expiresAt) {
    }

    private final DealPageFetcher fetcher;
    private final Function<byte[], DealOgImageEncoder.Encoded> encoder;
    private final OgSaver saver;
    private final Supplier<DealSettings> settings;
    private final Supplier<Clock> clock;
    private final ThreadPoolExecutor pool;
    private final Map<String, Entry> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CACHE_MAX;
        }
    };

    @Autowired
    public DealLinkInspector(DealPageFetcher fetcher, DealOgImageEncoder encoder, DealOgImageStore store, DealSettingsReader settings,
                             ObjectProvider<Clock> clocks) {
        this(fetcher, encoder::encode, store::save, settings::current, () -> clocks.getIfUnique(Clock::systemUTC), POOL_SIZE);
    }

    /** 시험용 — 가져오기·사본·설정·시계를 바꾼다 */
    public DealLinkInspector(DealPageFetcher fetcher, Function<byte[], DealOgImageEncoder.Encoded> encoder, OgSaver saver, Supplier<DealSettings> settings,
                             Supplier<Clock> clock, int poolSize) {
        this.fetcher = fetcher;
        this.encoder = encoder;
        this.saver = saver;
        this.settings = settings;
        this.clock = clock;
        AtomicInteger seq = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_SIZE), r -> {
            Thread t = new Thread(r, "deal-og-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        this.pool.allowCoreThreadTimeOut(true);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    /**
     * @param url      상품 링크(모양 검사는 부르는 쪽이 이미 했다)
     * @param memberId 부른 회원(사본의 {@code created_by})
     * @param wantOg   OG 까지 필요하다(link-check). 글 저장의 재판정은 false — 체인만
     */
    public Inspection inspect(String url, Long memberId, boolean wantOg) {
        Instant now = clock.get().instant();
        DealSettings s = settings.get();
        boolean readHead = wantOg && s.ogFetchEnabled();
        Entry hit;
        synchronized (cache) {
            hit = cache.get(url);
        }
        if (hit != null && now.isBefore(hit.expiresAt()) && (!readHead || hit.ogLoaded())) {
            return present(hit.inspection(), s.ogFetchEnabled());
        }
        Future<Inspection> future;
        try {
            future = pool.submit(() -> work(url, memberId, readHead));
        } catch (RejectedExecutionException e) {
            log.warn("핫딜 링크 가져오기 대기 줄이 찼다");
            return failed(url, s);
        }
        Inspection result;
        try {
            result = future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return failed(url, s);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return failed(url, s);
        } catch (ExecutionException e) {
            log.warn("핫딜 링크 가져오기가 실패했다 원인={}", e.getCause() == null ? "?" : e.getCause().getClass().getSimpleName());
            return failed(url, s);
        }
        boolean ok = (result.outcome() == Outcome.OK || result.outcome() == Outcome.NOT_HTML) && (result.og() == null || !OG_FAILED.equals(result.og().status()));
        Instant expires = clock.get().instant().plus(ok ? OK_TTL : FAIL_TTL);
        synchronized (cache) {
            cache.put(url, new Entry(result, readHead, expires));
        }
        return present(result, s.ogFetchEnabled());
    }

    /** 캐시를 비운다(시험·운영 확인용) */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    private Inspection work(String url, Long memberId, boolean readHead) {
        Page page = fetcher.fetchPage(url, readHead);
        List<String> hosts = page.hosts().isEmpty() ? hostsOf(url) : page.hosts();
        if (!readHead) {
            return new Inspection(url, hosts, page.outcome(), null);
        }
        Og og;
        if (page.outcome() == Outcome.NOT_HTML) {
            og = new Og(OG_NONE, null, null, null, null);
        } else if (page.outcome() != Outcome.OK || page.og() == null) {
            og = Og.FAILED;
        } else {
            og = image(page.og(), memberId);
        }
        return new Inspection(url, hosts, page.outcome(), og);
    }

    private Og image(DealPageFetcher.OgTags tags, Long memberId) {
        if (tags.imageUrl() == null) {
            return new Og(OG_NONE, null, null, tags.title(), tags.siteName());
        }
        DealPageFetcher.Image image = fetcher.fetchImage(tags.imageUrl());
        if (image.outcome() != Outcome.OK || image.data() == null) {
            return new Og(OG_FAILED, null, null, tags.title(), tags.siteName());
        }
        DealOgImageEncoder.Encoded encoded = encoder.apply(image.data());
        if (encoded == null) {
            return new Og(OG_NONE, null, null, tags.title(), tags.siteName());
        }
        try {
            DealOgImageStore.Saved saved = saver.save(encoded, tags.imageUrl(), memberId, clock.get().instant());
            return new Og(OG_OK, saved.id(), saved.url(), tags.title(), tags.siteName());
        } catch (RuntimeException e) {
            log.warn("OG 사본을 저장하지 못했다 원인={}", e.getClass().getSimpleName());
            return new Og(OG_FAILED, null, null, tags.title(), tags.siteName());
        }
    }

    /** 꺼진 설정이면 OG 는 OFF, 체인만 가져온 결과의 OG 도 OFF 로 보인다 */
    private static Inspection present(Inspection inspection, boolean ogEnabled) {
        if (!ogEnabled || inspection.og() == null) {
            return new Inspection(inspection.url(), inspection.hosts(), inspection.outcome(), Og.OFF);
        }
        return inspection;
    }

    private static Inspection failed(String url, DealSettings s) {
        return new Inspection(url, hostsOf(url), Outcome.FAILED, s.ogFetchEnabled() ? Og.FAILED : Og.OFF);
    }

    private static List<String> hostsOf(String url) {
        String host = DealHosts.hostOf(url);
        return host == null ? List.of() : List.of(host);
    }
}
