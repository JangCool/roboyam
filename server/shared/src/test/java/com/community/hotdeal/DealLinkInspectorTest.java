package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.og.DealLinkInspector;
import com.community.hotdeal.og.DealLinkInspector.Inspection;
import com.community.hotdeal.og.DealOgImageEncoder;
import com.community.hotdeal.og.DealOgImageStore;
import com.community.hotdeal.og.DealPageFetcher;
import com.community.media.service.ImageFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 서버 규칙 1 — 캐시 24시간(실패 10분) · 전용 풀 동시 4 · OG 꺼짐이면 OFF(체인은 그대로) · OG 그림은 사본으로(없음·실패 상태). 가져오기는 가짜(외부 호출 없음).
 */
class DealLinkInspectorTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-01T03:00:00Z"));
    private final AtomicReference<DealSettings> settings = new AtomicReference<>(DealSettings.DEFAULTS);
    private final AtomicInteger saved = new AtomicInteger();

    private static DealSettings withOg(boolean on) {
        DealSettings d = DealSettings.DEFAULTS;
        return new DealSettings(d.endRequestThreshold(), d.endGraceHours(), d.endRequestDailyLimit(), d.endRequestPerAuthorLimit(), d.endRequestPerAuthorDays(),
                d.keepMaxCount(), d.memberDailyPostLimit(), d.sameStoreIntervalMinutes(), d.autoExpireDays(), d.priceMaxWon(), d.discountWarnPercent(),
                d.hotLikeThreshold(), d.suspicionMinPosts(), d.suspicionWindowDays(), d.storeSuspicionMembers(), d.storeSuspicionWindowDays(),
                d.storeSuspicionOtherRatioPercent(), on, d.affiliateMode());
    }

    /** 가짜 가져오기 — 부른 수를 센다 */
    private static final class FakeFetcher implements DealPageFetcher {
        final AtomicInteger pages = new AtomicInteger();
        final AtomicInteger images = new AtomicInteger();
        final List<Boolean> readHeads = new ArrayList<>();
        volatile Outcome outcome = Outcome.OK;
        volatile String image = "https://cdn.example.com/a.png";

        @Override
        public synchronized Page fetchPage(String url, boolean readHead) {
            pages.incrementAndGet();
            readHeads.add(readHead);
            return new Page(List.of("short.example", "shop.example.com"), url, outcome,
                    outcome == Outcome.OK && readHead ? new OgTags("제목", "몰", image) : null, 10);
        }

        @Override
        public Image fetchImage(String url) {
            images.incrementAndGet();
            return new Image(Outcome.OK, new byte[] {1, 2, 3});
        }
    }

    private DealLinkInspector inspector(DealPageFetcher fetcher, java.util.function.Function<byte[], DealOgImageEncoder.Encoded> encoder) {
        return new DealLinkInspector(fetcher, encoder, (encoded, origin, member, at) -> new DealOgImageStore.Saved(saved.incrementAndGet(), "/media/deal-og/" + saved.get()),
                settings::get, () -> Clock.fixed(now.get(), ZoneOffset.UTC), DealLinkInspector.POOL_SIZE);
    }

    private static DealOgImageEncoder.Encoded jpeg(byte[] data) {
        return new DealOgImageEncoder.Encoded(ImageFormat.JPEG, data, 600, 300);
    }

    @Test
    void 성공은_24시간_캐시하고_지나면_다시_가져온다() {
        FakeFetcher fetcher = new FakeFetcher();
        DealLinkInspector inspector = inspector(fetcher, DealLinkInspectorTest::jpeg);
        Inspection first = inspector.inspect("https://short.example/a", 1L, true);
        assertThat(first.og().status()).isEqualTo("OK");
        assertThat(first.og().imageUrl()).isEqualTo("/media/deal-og/1");
        assertThat(first.hosts()).containsExactly("short.example", "shop.example.com");

        now.set(now.get().plus(Duration.ofHours(23)));
        Inspection cached = inspector.inspect("https://short.example/a", 2L, true);
        assertThat(cached.og().imageUrl()).as("같은 사본").isEqualTo("/media/deal-og/1");
        assertThat(fetcher.pages.get()).isEqualTo(1);

        now.set(now.get().plus(Duration.ofHours(1).plusSeconds(1)));
        inspector.inspect("https://short.example/a", 2L, true);
        assertThat(fetcher.pages.get()).isEqualTo(2);
    }

    @Test
    void 실패는_10분만_캐시한다() {
        FakeFetcher fetcher = new FakeFetcher();
        fetcher.outcome = DealPageFetcher.Outcome.FAILED;
        DealLinkInspector inspector = inspector(fetcher, DealLinkInspectorTest::jpeg);
        assertThat(inspector.inspect("https://x.example/fail", 1L, true).og().status()).isEqualTo("FAILED");
        now.set(now.get().plus(Duration.ofMinutes(9)));
        inspector.inspect("https://x.example/fail", 1L, true);
        assertThat(fetcher.pages.get()).isEqualTo(1);
        now.set(now.get().plus(Duration.ofMinutes(1).plusSeconds(1)));
        inspector.inspect("https://x.example/fail", 1L, true);
        assertThat(fetcher.pages.get()).isEqualTo(2);
    }

    @Test
    void OG_를_끄면_OFF_이고_체인은_그대로_따라간다_머리는_읽지_않는다() {
        settings.set(withOg(false));
        FakeFetcher fetcher = new FakeFetcher();
        Inspection result = inspector(fetcher, DealLinkInspectorTest::jpeg).inspect("https://short.example/off", 1L, true);
        assertThat(result.og().status()).isEqualTo("OFF");
        assertThat(result.hosts()).containsExactly("short.example", "shop.example.com");
        assertThat(fetcher.readHeads).containsExactly(false);
        assertThat(fetcher.images.get()).isZero();
    }

    @Test
    void 체인만_가져온_결과는_OG_가_필요하면_다시_가져온다() {
        FakeFetcher fetcher = new FakeFetcher();
        DealLinkInspector inspector = inspector(fetcher, DealLinkInspectorTest::jpeg);
        assertThat(inspector.inspect("https://short.example/save", 1L, false).og().status()).isEqualTo("OFF");
        inspector.inspect("https://short.example/save", 1L, false);
        assertThat(fetcher.pages.get()).isEqualTo(1);
        assertThat(inspector.inspect("https://short.example/save", 1L, true).og().status()).isEqualTo("OK");
        assertThat(fetcher.readHeads).containsExactly(false, true);
    }

    @Test
    void OG_그림이_없거나_사본을_못_만들면_NONE() {
        FakeFetcher fetcher = new FakeFetcher();
        fetcher.image = null;
        assertThat(inspector(fetcher, DealLinkInspectorTest::jpeg).inspect("https://short.example/noimg", 1L, true).og().status()).isEqualTo("NONE");
        FakeFetcher other = new FakeFetcher();
        Inspection none = inspector(other, data -> null).inspect("https://short.example/bad", 1L, true);
        assertThat(none.og().status()).isEqualTo("NONE");
        assertThat(none.og().title()).isEqualTo("제목");
        assertThat(saved.get()).isZero();
    }

    @Test
    void 전용_풀은_동시에_4개까지만_가져온다() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        DealPageFetcher blocking = new DealPageFetcher() {
            @Override
            public Page fetchPage(String url, boolean readHead) {
                int n = running.incrementAndGet();
                peak.accumulateAndGet(n, Math::max);
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    running.decrementAndGet();
                }
                return new Page(List.of("a.example"), url, Outcome.OK, null, 0);
            }

            @Override
            public Image fetchImage(String url) {
                return new Image(Outcome.FAILED, null);
            }
        };
        DealLinkInspector inspector = inspector(blocking, DealLinkInspectorTest::jpeg);
        ExecutorService callers = Executors.newFixedThreadPool(8);
        List<Future<Inspection>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String url = "https://a.example/" + i;
            futures.add(callers.submit(() -> inspector.inspect(url, 1L, false)));
        }
        Thread.sleep(400);
        assertThat(running.get()).isEqualTo(4);
        release.countDown();
        for (Future<Inspection> f : futures) {
            assertThat(f.get(5, TimeUnit.SECONDS).hosts()).containsExactly("a.example");
        }
        assertThat(peak.get()).isEqualTo(DealLinkInspector.POOL_SIZE);
        callers.shutdownNow();
    }
}
