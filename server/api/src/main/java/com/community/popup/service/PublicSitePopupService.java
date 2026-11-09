package com.community.popup.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.popup.dto.SitePopupPublicLine;
import com.community.popup.dto.response.PublicSitePopupView;
import com.community.popup.dto.response.SitePopupsResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 공개 팝업({@code GET /api/site/popups})의 <b>짧은 캐시 사본</b> — 기본 레이아웃이 앱을 열 때 한 번 부르므로 요청마다 DB 를 읽지 않고 캐시 {@code site.popups}(기본 20초)에 둔다
 * (공지 띠 {@code PublicSiteNoticeService} 와 같은 틀). 관리자 저장은 세대 번호로 몇 초 안에 보인다.
 *
 * <ul>
 *   <li>사본은 노출을 켰고 읽은 때 끝나지 않은 팝업({@link SitePopupReader}, 시작 전인 것 포함)이다. 기간은 <b>요청마다 지금 시각으로 다시 거른다</b> — 시작·끝이
 *       사본 시간만큼 늦지 않다(새로 만들거나 고친 것은 저장 뒤 몇 초 안에 보인다).</li>
 *   <li>응답에 사용자별 내용이 없어 모두가 같은 값을 받는다. {@link Snapshot#etag()} 는 걸러 낸 응답 내용의 지문이다.</li>
 * </ul>
 */
@Service
public class PublicSitePopupService {


    private final SitePopupReader reader;
    private final CacheManager caches;

    public PublicSitePopupService(SitePopupReader reader, CacheManager caches) {
        this.caches = caches;
        this.reader = reader;
    }

    /** 지금 보일 팝업 — 사본을 지금 시각으로 거른 것 */
    public Snapshot snapshot() {
        Instant now = Instant.now();
        Loaded current = load(now);
        List<PublicSitePopupView> items = current.lines.stream().filter(line -> line.inPeriod(now)).map(PublicSitePopupService::view).toList();
        SitePopupsResponse response = new SitePopupsResponse(current.layout, items);
        return new Snapshot(response, fingerprint(response));
    }

    /** 사본 — 캐시 {@code site.popups}(노출을 켰고 읽은 때 끝나지 않은 팝업 + 배치) */
    private Loaded load(Instant now) {
        return caches.getCache(CacheNames.SITE_POPUPS).get(CacheNames.SINGLE, () -> new Loaded(reader.publicLines(now), reader.layout()));
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

    /** 캐시에 담는 값 */
    private record Loaded(List<SitePopupPublicLine> lines, String layout) {
    }
}
