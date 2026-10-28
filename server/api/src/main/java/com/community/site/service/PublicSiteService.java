package com.community.site.service;

import com.community.board.entity.BoardDefaultsEntity;
import com.community.board.repository.master.BoardDefaultsRepository;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.SiteThemeMenu;
import com.community.site.dto.response.BoardDisplayView;
import com.community.site.dto.response.SiteDisplayDefaultsView;
import com.community.site.dto.response.SiteIconResponse;
import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.dto.response.SiteThemeResponse;
import com.community.site.dto.response.WebManifestIcon;
import com.community.site.dto.response.WebManifestResponse;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.entity.SiteThemeMenuEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.repository.master.SiteThemeMenuRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 공개 사이트가 읽는 사이트 설정의 <b>짧은 캐시 사본</b>({@link PublicSiteSnapshot}) — {@code /api/site/info}·{@code /api/site/theme}·{@code /manifest.webmanifest}·{@code index.html} 머리,
 * 회원 표시 설정의 사이트 기본값({@code /api/display/effective}·{@code /api/me/display})이 같은 사본을 쓴다.
 * 요청마다 DB 를 읽지 않고 {@value #TTL_SECONDS}초마다 다시 읽는다(관리자 앱은 다른 서버라 설정을 바꿔도 이 서버에는 최대 그만큼 늦게 닿는다). 다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다.
 *
 * <p>주소는 관리자 {@code me} 와 같은 소스({@link SiteAssets})로 만든다. 설정 행이 없으면(설치 전) 이름·소개·로고·아이콘이 비어 있고 표시 기본값은 설치 직후의 처음 값이다.
 */
@Service
public class PublicSiteService {

    private static final Logger log = LoggerFactory.getLogger(PublicSiteService.class);

    static final int TTL_SECONDS = 20;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    /** 이름을 모를 때(설정 행 없음)의 매니페스트 이름 */
    private static final String FALLBACK_NAME = "Roboyam";
    private static final int SHORT_NAME_MAX = 12;
    private static final String MANIFEST_COLOR = "#ffffff";

    private final SiteSettingRepository settings;
    private final SiteAssets assets;
    private final BoardDefaultsRepository boardDefaults;
    private final SiteThemeMenuRepository themeMenus;
    private volatile Cached cached;

    public PublicSiteService(SiteSettingRepository settings, SiteAssets assets, BoardDefaultsRepository boardDefaults, SiteThemeMenuRepository themeMenus) {
        this.boardDefaults = boardDefaults;
        this.settings = settings;
        this.assets = assets;
        this.themeMenus = themeMenus;
    }

    /** 지금 쓸 수 있는 사본 — 메모리에 있고 {@value #TTL_SECONDS}초 안이면 그것, 아니면 다시 읽는다 */
    public PublicSiteSnapshot snapshot() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current.snapshot;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current.snapshot;
            }
            try {
                PublicSiteSnapshot loaded = load();
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("공개 사이트 설정을 다시 읽지 못해 이전 값을 쓴다: {}", e.toString());
                cached = new Cached(current.snapshot, now - TTL_NANOS + RETRY_NANOS);
                return current.snapshot;
            }
        }
    }

    private PublicSiteSnapshot load() {
        Optional<SiteSettingEntity> found = settings.findById(SiteSettingEntity.ROW_ID);
        String publicUrl = found.map(SiteSettingEntity::getPublicUrl).orElse(null);
        String siteName = found.map(SiteSettingEntity::getSiteName).orElse(null);
        String tagline = found.map(SiteSettingEntity::getTagline).orElse(null);
        String logoUrl = found.flatMap(assets::logoUrl).orElse(null);
        Optional<SiteIconResponse> icon = found.flatMap(assets::iconUrls).map(urls ->
                new SiteIconResponse(urls.version(), urls.favicon32(), urls.favicon48(), urls.appleTouch180(), urls.icon192(), urls.icon512()));
        Optional<String> customOg = found.flatMap(assets::ogUrl);
        String ogImageUrl = assets.absolute(customOg.orElse(SiteMediaKeys.DEFAULT_OG_PNG_URL), publicUrl);
        SiteDisplayDefaultsView display = found
                .map(row -> new SiteDisplayDefaultsView(row.getDisplayLanguage(), row.getDisplayZoneId(), row.getDisplayTimeFormat(), row.getDisplayDateFormat()))
                .orElseGet(() -> new SiteDisplayDefaultsView(SiteSettingEntity.DEFAULT_LANGUAGE, SiteSettingEntity.DEFAULT_ZONE_ID,
                        SiteSettingEntity.DEFAULT_TIME_FORMAT, SiteSettingEntity.DEFAULT_DATE_FORMAT));

        String dateDisplay = boardDefaults.findById(BoardDefaultsEntity.ROW_ID).map(row -> row.values().dateDisplay()).orElse("STANDARD");
        BoardDisplayView boardDisplay = new BoardDisplayView(dateDisplay, BoardDisplayView.NEW_POST_HOURS);
        String theme = SiteThemes.normalize(found.map(SiteSettingEntity::getTheme).orElse(null));
        SiteInfoResponse info = new SiteInfoResponse(siteName, tagline, logoUrl, icon.orElse(null), ogImageUrl, display, boardDisplay, theme);
        SiteThemeResponse themeView = themeView(theme, themeMenus.findById(theme).map(SiteThemeMenuEntity::toMenu).orElse(SiteThemeMenus.INITIAL));
        WebManifestResponse manifest = manifest(siteName, tagline, display.language(), icon.orElse(null));
        boolean ogCustom = customOg.isPresent();
        return new PublicSiteSnapshot(info, themeView, manifest, ogCustom, fingerprint(info + "|" + themeView, manifest, ogCustom));
    }

    /** 지금 테마 + 그 테마의 메뉴 설정(운영자 값 — 칸마다 THEME 또는 값, 저장한 적 없으면 모두 THEME) */
    private static SiteThemeResponse themeView(String theme, SiteThemeMenu m) {
        SiteThemeMenu.Style s = m.style();
        return new SiteThemeResponse(theme, m.menuPosition(), m.topItems(), m.barGroupIds(), m.menuWidth(), m.menuOverflow(), s.menuGroupStyle(),
                s.sideGroupStyle(), s.menuSize(), s.mobileMenu(), s.menuDescription(), s.stickyMenu(), s.mobileHeader());
    }

    /** 이름은 사이트 이름, 아이콘은 올린 192·512 PNG 또는 기본 SVG 아이콘 — 같은 서버가 서빙하는 주소라 상대 주소 그대로 둔다 */
    private static WebManifestResponse manifest(String siteName, String tagline, String language, SiteIconResponse icon) {
        String name = siteName == null ? FALLBACK_NAME : siteName;
        String shortName = name.codePointCount(0, name.length()) <= SHORT_NAME_MAX ? name : name.substring(0, name.offsetByCodePoints(0, SHORT_NAME_MAX));
        List<WebManifestIcon> icons = icon == null
                ? List.of(new WebManifestIcon(SiteMediaKeys.DEFAULT_ICON_URL, "any", "image/svg+xml", "any"))
                : List.of(new WebManifestIcon(icon.icon192(), "192x192", "image/png", "any"), new WebManifestIcon(icon.icon512(), "512x512", "image/png", "any"));
        return new WebManifestResponse(name, shortName, tagline, language, "/", "/", "standalone", MANIFEST_COLOR, MANIFEST_COLOR, icons);
    }

    /** 사본 내용의 지문 — 레코드의 {@code toString} 은 칸 값을 모두 싣는다 */
    private static String fingerprint(String info, WebManifestResponse manifest, boolean ogCustom) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((info + "|" + manifest + "|" + ogCustom).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Cached(PublicSiteSnapshot snapshot, long loadedAt) {
    }
}
