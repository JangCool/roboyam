package com.community.site.service;

import com.community.analytics.settings.StatsCollectionSettings;
import com.community.cache.definition.CacheNames;
import com.community.zconfiguration.cache.CacheReloader;
import com.community.board.dto.BoardDefaultsValues;
import com.community.board.entity.BoardDefaultsEntity;
import com.community.board.service.BoardFields;
import com.community.site.dto.response.SiteAnalyticsView;
import com.community.board.repository.master.BoardDefaultsRepository;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.SiteFooterNotice;
import com.community.site.dto.SiteOperatorValues;
import com.community.site.dto.SiteThemeMenu;
import com.community.site.dto.response.BoardDisplayView;
import com.community.site.dto.response.SiteDisplayDefaultsView;
import com.community.site.dto.response.SiteGradeView;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import org.springframework.beans.factory.annotation.Autowired;
import com.community.site.dto.response.SiteIconResponse;
import com.community.site.dto.response.SiteInfoResponse;
import com.community.site.dto.response.SiteOperatorPublicView;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 공개 사이트가 읽는 사이트 설정의 <b>짧은 캐시 사본</b>({@link PublicSiteSnapshot}) — {@code /api/site/info}·{@code /api/site/theme}·{@code /manifest.webmanifest}·{@code index.html} 머리,
 * 회원 표시 설정의 사이트 기본값({@code /api/display/effective}·{@code /api/me/display})이 같은 사본을 쓴다.
 * 요청마다 DB 를 읽지 않고 캐시 {@code site.info}(기본 TTL 20초 — 관리자 캐시 관리에서 바꾼다)에 둔다. 관리자 저장은 세대 번호로 몇 초 안에, 그 밖의 변화는 TTL 안에 닿는다
 * (docs/features/cache-admin-contract.md). 처음 읽다 실패하면 담지 않고 그 요청이 실패한다 — 다음 요청이 다시 읽는다. TTL 이 지나 다시 읽다 실패하면 이전 값을 쓴다({@link CacheReloader}).
 *
 * <p>주소는 관리자 {@code me} 와 같은 소스({@link SiteAssets})로 만든다. 설정 행이 없으면(설치 전) 이름·소개·로고·아이콘이 비어 있고 표시 기본값은 설치 직후의 처음 값이다.
 */
@Service
public class PublicSiteService implements CacheReloader {

    /** 이름을 모를 때(설정 행 없음)의 매니페스트 이름 */
    private static final String FALLBACK_NAME = "Roboyam";
    private static final int SHORT_NAME_MAX = 12;
    private static final String MANIFEST_COLOR = "#ffffff";

    private final SiteSettingRepository settings;
    private final SiteAssets assets;
    private final BoardDefaultsRepository boardDefaults;
    private final SiteThemeMenuRepository themeMenus;
    private final StatsCollectionSettings statsSettings;
    private final SiteOperatorReader operators;
    private final CacheManager caches;
    /** 등급 목록(V196 — {@code grades}). 시험처럼 없이 만들면(null) 빈 목록 */
    private final GradeRepositoryQDSL grades;

    public PublicSiteService(SiteSettingRepository settings, SiteAssets assets, BoardDefaultsRepository boardDefaults, SiteThemeMenuRepository themeMenus,
                             StatsCollectionSettings statsSettings, SiteOperatorReader operators, CacheManager caches) {
        this(settings, assets, boardDefaults, themeMenus, statsSettings, operators, caches, null);
    }

    @Autowired
    public PublicSiteService(SiteSettingRepository settings, SiteAssets assets, BoardDefaultsRepository boardDefaults, SiteThemeMenuRepository themeMenus,
                             StatsCollectionSettings statsSettings, SiteOperatorReader operators, CacheManager caches, GradeRepositoryQDSL grades) {
        this.grades = grades;
        this.caches = caches;
        this.operators = operators;
        this.statsSettings = statsSettings;
        this.boardDefaults = boardDefaults;
        this.settings = settings;
        this.assets = assets;
        this.themeMenus = themeMenus;
    }

    /** 지금 쓸 수 있는 사본 — 캐시 {@code site.info} 에 있으면 그것, 아니면 읽어 담는다(같은 서버에서 동시에 비면 한 번만 읽는다) */
    public PublicSiteSnapshot snapshot() {
        return caches.getCache(CacheNames.SITE_INFO).get(CacheNames.SINGLE, this::load);
    }

    @Override
    public String cacheName() {
        return CacheNames.SITE_INFO;
    }

    /** TTL 이 지난 뒤의 다시 읽기({@link CacheReloader}) — 처음 담는 로더와 같은 값. 실패하면 캐시가 이전 값을 쓴다 */
    @Override
    public Object reload(Object key) {
        return load();
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

        Optional<BoardDefaultsValues> boardValues = boardDefaults.findById(BoardDefaultsEntity.ROW_ID).map(BoardDefaultsEntity::values);
        String dateDisplay = boardValues.map(BoardDefaultsValues::dateDisplay).orElse("STANDARD");
        // 글 목록 칩 위치의 사이트 기본(V169) — 섞인 목록이 쓴다. 이 사본(20초)과 지문(ETag)에 함께 든다
        String listChipPosition = boardValues.map(BoardDefaultsValues::listChipPosition).orElse(BoardFields.CHIP_ABOVE);
        // 목록 윗줄 추천·조회 숫자 강조(V170) — 기준(0 = 안 함)과 색
        int likeHighlight = boardValues.map(BoardDefaultsValues::listLikeHighlight).orElse(BoardFields.LIKE_HIGHLIGHT_DEFAULT);
        int viewHighlight = boardValues.map(BoardDefaultsValues::listViewHighlight).orElse(BoardFields.VIEW_HIGHLIGHT_DEFAULT);
        String highlightColor = boardValues.map(BoardDefaultsValues::listHighlightColor).orElse(BoardFields.HIGHLIGHT_COLOR_DEFAULT);
        // 글 목록 말머리 모양의 사이트 기본(V195) — 섞인 목록이 쓴다
        String listPrefixStyle = boardValues.map(BoardDefaultsValues::listPrefixStyle).orElse(BoardFields.PREFIX_STYLE_TEXT);
        BoardDisplayView boardDisplay = new BoardDisplayView(dateDisplay, BoardDisplayView.NEW_POST_HOURS, listChipPosition, likeHighlight, viewHighlight,
                highlightColor, listPrefixStyle);
        String theme = SiteThemes.normalize(found.map(SiteSettingEntity::getTheme).orElse(null));
        // 방문 수집 스위치 — 설정 사본(30초)에서. 이 사본(20초)의 지문(ETag)에도 들어간다
        SiteInfoResponse info = new SiteInfoResponse(siteName, tagline, logoUrl, icon.orElse(null), ogImageUrl, display, boardDisplay, theme,
                SiteAnalyticsView.of(statsSettings.current()), operatorView(operators.current(), operators.footerNotice()),
                // 등급 목록(V196) — 이 사본(20초)과 지문(ETag)에 함께 든다. 등급 저장 뒤 길어야 사본 시간만큼 늦게 보인다
                grades == null ? List.of() : grades.findAllOrdered().stream()
                        .map(g -> new SiteGradeView(g.getGradeKey(), g.getLabel(), g.getLevel(), g.getSprite())).toList());
        SiteThemeResponse themeView = themeView(theme, themeMenus.findById(theme).map(SiteThemeMenuEntity::toMenu).orElseGet(() -> SiteThemeMenus.initialOf(theme)));
        WebManifestResponse manifest = manifest(siteName, tagline, display.language(), icon.orElse(null));
        boolean ogCustom = customOg.isPresent();
        return new PublicSiteSnapshot(info, themeView, manifest, ogCustom, fingerprint(info + "|" + themeView, manifest, ogCustom));
    }

    /** 운영 주체의 공개 칸 — 단계 · 연락 이메일(빈 글자는 null) · 푸터 안내 줄(없으면 null)과 보이기. 이 사본(20초)과 지문(ETag)에 함께 든다 */
    private static SiteOperatorPublicView operatorView(SiteOperatorValues v, SiteFooterNotice notice) {
        return new SiteOperatorPublicView(v.stage().name(), v.contactEmail() == null || v.contactEmail().isBlank() ? null : v.contactEmail(), notice.linesOrNull(),
                notice.scope().name(), notice.businessApply());
    }

    /** 지금 테마 + 그 테마의 메뉴 설정(운영자 값 — 칸마다 THEME 또는 값, 저장한 적 없으면 모두 THEME · 메뉴 글자 기본 굵게는 테마의 처음 값) */
    private static SiteThemeResponse themeView(String theme, SiteThemeMenu m) {
        SiteThemeMenu.Style s = m.style();
        return new SiteThemeResponse(theme, m.menuPosition(), m.topItems(), m.barGroupIds(), m.menuWidth(), m.menuOverflow(), s.menuGroupStyle(),
                s.sideGroupStyle(), s.menuSize(), s.mobileMenu(), s.menuDescription(), s.stickyMenu(), s.mobileHeader(), m.menuBold());
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
}
