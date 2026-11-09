package com.community.site.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.site.dto.SiteNoticeLine;
import com.community.site.dto.response.SiteNoticeBarResponse;
import com.community.site.dto.response.SiteNoticeItemView;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 공개 공지 띠({@code GET /api/site/notice-bar})의 <b>짧은 캐시 사본</b> — 모든 화면이 앱을 열 때 한 번 부르므로 요청마다 DB 를 읽지 않고 캐시 {@code site.noticeBar}(기본 20초)에 둔다
 * ({@link PublicSiteService} 와 같은 틀 — docs/features/cache-admin-contract.md). 관리자 저장은 몇 초 안에, 새 공지 글은 TTL(+ 게시판 사본) 안에 보인다.
 *
 * <ul>
 *   <li>설정 행이 없거나(설치 전) 꺼져 있으면 {@code { version: "off", items: [] }}.</li>
 *   <li>줄은 관리자 미리보기와 같은 판정({@link SiteNoticeBarReader}) — {@code BOARD} 는 손님에게 보이는 공지 게시판 최근 글, {@code CUSTOM} 은 문구 한 줄.</li>
 *   <li>응답에 사용자별 내용이 없어 모두가 같은 사본을 받는다. {@link Snapshot#etag()} 는 사본 내용의 지문이다.</li>
 * </ul>
 */
@Service
public class PublicSiteNoticeService {

    private static final SiteNoticeBarResponse OFF = new SiteNoticeBarResponse(SiteNoticeBars.VERSION_OFF, List.of());

    private final SiteSettingRepository settings;
    private final SiteNoticeBarReader reader;
    private final CacheManager caches;

    public PublicSiteNoticeService(SiteSettingRepository settings, SiteNoticeBarReader reader, CacheManager caches) {
        this.caches = caches;
        this.settings = settings;
        this.reader = reader;
    }

    /** 지금 쓸 수 있는 사본 — 캐시 {@code site.noticeBar} */
    public Snapshot snapshot() {
        return caches.getCache(CacheNames.SITE_NOTICE_BAR).get(CacheNames.SINGLE, () -> {
            SiteNoticeBarResponse loaded = load();
            return new Snapshot(loaded, fingerprint(loaded));
        });
    }

    private SiteNoticeBarResponse load() {
        Optional<SiteSettingEntity> found = settings.findById(SiteSettingEntity.ROW_ID);
        if (found.isEmpty() || !found.get().isNoticeBarEnabled()) {
            return OFF;
        }
        SiteSettingEntity row = found.get();
        List<SiteNoticeLine> lines = reader.lines(row);
        List<SiteNoticeItemView> items = lines.stream()
                .map(line -> new SiteNoticeItemView(line.postId() == null ? null : Long.toString(line.postId()), line.title(), line.link(), line.publishedAt()))
                .toList();
        return new SiteNoticeBarResponse(SiteNoticeBarReader.version(row, lines), items);
    }

    /** 사본 내용의 지문 — 레코드의 {@code toString} 은 칸 값을 모두 싣는다 */
    private static String fingerprint(SiteNoticeBarResponse response) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(response.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 한 시점의 공지 띠 응답과 그 지문(ETag) */
    public record Snapshot(SiteNoticeBarResponse response, String etag) {
    }

}
