package com.community.popup.service;

import com.community.popup.dto.SitePopupPublicLine;
import com.community.popup.dto.response.PublicSitePopupView;
import com.community.popup.dto.response.SitePopupsResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 공개 팝업({@code GET /api/site/popups})의 <b>짧은 캐시 사본</b> — 기본 레이아웃이 앱을 열 때 한 번 부르므로 요청마다 DB 를 읽지 않고 {@value #TTL_SECONDS}초마다 다시 읽는다
 * (공지 띠 {@code PublicSiteNoticeService} 와 같은 방식: 다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도). 관리자 저장은 최대 그만큼 늦게 보인다.
 *
 * <ul>
 *   <li>사본은 노출을 켰고 읽은 때 끝나지 않은 팝업({@link SitePopupReader}, 시작 전인 것 포함)이다. 기간은 <b>요청마다 지금 시각으로 다시 거른다</b> — 시작·끝이
 *       사본 시간만큼 늦지 않다(새로 만들거나 고친 것만 사본 시간만큼 늦다).</li>
 *   <li>응답에 사용자별 내용이 없어 모두가 같은 값을 받는다. {@link Snapshot#etag()} 는 걸러 낸 응답 내용의 지문이다.</li>
 * </ul>
 */
@Service
public class PublicSitePopupService {

    private static final Logger log = LoggerFactory.getLogger(PublicSitePopupService.class);

    static final int TTL_SECONDS = 20;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final SitePopupReader reader;
    private volatile Cached cached;

    public PublicSitePopupService(SitePopupReader reader) {
        this.reader = reader;
    }

    /** 지금 보일 팝업 — 사본을 지금 시각으로 거른 것 */
    public Snapshot snapshot() {
        Instant now = Instant.now();
        Cached current = load(now);
        List<PublicSitePopupView> items = current.lines.stream().filter(line -> line.inPeriod(now)).map(PublicSitePopupService::view).toList();
        SitePopupsResponse response = new SitePopupsResponse(current.layout, items);
        return new Snapshot(response, fingerprint(response));
    }

    private Cached load(Instant now) {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current;
        }
        synchronized (this) {
            current = cached;
            long nanos = System.nanoTime();
            if (current != null && nanos - current.loadedAt < TTL_NANOS) {
                return current;
            }
            try {
                Cached loaded = new Cached(reader.publicLines(now), reader.layout(), nanos);
                cached = loaded;
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("팝업을 다시 읽지 못해 이전 값을 쓴다: {}", e.toString());
                Cached kept = new Cached(current.lines, current.layout, nanos - TTL_NANOS + RETRY_NANOS);
                cached = kept;
                return kept;
            }
        }
    }

    private static PublicSitePopupView view(SitePopupPublicLine line) {
        return new PublicSitePopupView(Long.toString(line.id()), line.version(), line.kind(), line.imageUrl(), line.imageAlt(), line.link(), line.heading(),
                line.body(), line.buttonLabel(), line.target(), line.devices(), line.hideOption(), line.windowNo());
    }

    /** 응답 내용의 지문 — 레코드의 {@code toString} 은 칸 값을 모두 싣는다 */
    private static String fingerprint(SitePopupsResponse response) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(response.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 한 시점의 팝업 응답과 그 지문(ETag) */
    public record Snapshot(SitePopupsResponse response, String etag) {
    }

    private record Cached(List<SitePopupPublicLine> lines, String layout, long loadedAt) {
    }
}
