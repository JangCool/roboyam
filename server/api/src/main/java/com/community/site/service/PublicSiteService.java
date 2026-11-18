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
import com.community.site.dto.SiteLogoUrls;
import com.community.site.dto.SiteOperatorValues;
import com.community.site.dto.SiteThemeMenu;
import com.community.site.dto.response.BoardDisplayView;
import com.community.site.dto.response.SiteDisplayDefaultsView;
import com.community.site.dto.response.SiteGenderLogosView;
import com.community.site.dto.response.SiteLogoSetView;
import com.community.site.entity.SiteLogoBundleEntity;
import com.community.site.entity.SiteLogoVariant;
import com.community.site.repository.master.SiteLogoBundleRepository;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.cache.Cache;
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
 * <p><b>로고는 날짜에 따라 바뀐다</b>(V249 로고 보이기 규칙 — 기념일). 사본에 든 로고는 읽은 날(서비스 시간대 날짜)의 것이라, 날짜가 바뀐 뒤 첫 요청이 사본을 버리고 다시 읽는다
 * ({@link #snapshot} — 서버마다 자기 사본을 스스로 갈므로 서버가 여러 대여도 배치·신호가 필요 없다). 쉬는 날 표가 바뀐 것은 TTL 안에 닿는다.
 *
 * <p>주소는 관리자 {@code me} 와 같은 소스({@link SiteAssets})로 만든다. 설정 행이 없으면(설치 전) 이름·소개·로고·아이콘이 비어 있고 표시 기본값은 설치 직후의 처음 값이다.
 */
@Service
public class PublicSiteService implements CacheReloader {

    /** 이름을 모를 때(설정 행 없음)의 매니페스트 이름 */
    private static final String FALLBACK_NAME = "IROBIT";
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
    /** 오늘 보일 로고 묶음(V249). 시험처럼 없이 만들면(null) 로고 없음 */
    private final SiteLogoResolver logoResolver;
    private final SiteLogoBundleRepository logoBundles;
    private final Clock clock = Clock.systemUTC();
    /** 지금 사본의 로고를 고른 날(서비스 시간대) — 날짜가 바뀌면 사본을 버린다. 아직 읽은 적 없으면 null */
    private volatile LocalDate logoDate;

    public PublicSiteService(SiteSettingRepository settings, SiteAssets assets, BoardDefaultsRepository boardDefaults, SiteThemeMenuRepository themeMenus,
                             StatsCollectionSettings statsSettings, SiteOperatorReader operators, CacheManager caches) {
        this(settings, assets, boardDefaults, themeMenus, statsSettings, operators, caches, null, null, null);
    }

    @Autowired
    public PublicSiteService(SiteSettingRepository settings, SiteAssets assets, BoardDefaultsRepository boardDefaults, SiteThemeMenuRepository themeMenus,
                             StatsCollectionSettings statsSettings, SiteOperatorReader operators, CacheManager caches, GradeRepositoryQDSL grades,
                             SiteLogoResolver logoResolver, SiteLogoBundleRepository logoBundles) {
        this.logoResolver = logoResolver;
        this.logoBundles = logoBundles;
        this.grades = grades;
        this.caches = caches;
        this.operators = operators;
        this.statsSettings = statsSettings;
        this.boardDefaults = boardDefaults;
        this.settings = settings;
        this.assets = assets;
        this.themeMenus = themeMenus;
    }

    /**
     * 지금 쓸 수 있는 사본 — 캐시 {@code site.info} 에 있으면 그것, 아니면 읽어 담는다(같은 서버에서 동시에 비면 한 번만 읽는다).
     * 사본의 로고를 고른 날이 오늘(서비스 시간대)이 아니면 먼저 버린다 — 자정이 지난 뒤 첫 요청이 그날의 로고로 다시 읽는다.
     */
    public PublicSiteSnapshot snapshot() {
        Cache cache = caches.getCache(CacheNames.SITE_INFO);
        LocalDate loaded = logoDate;
        if (loaded != null && !loaded.equals(ServiceTime.today(clock))) {
            cache.evict(CacheNames.SINGLE);
        }
        return cache.get(CacheNames.SINGLE, this::load);
    }

    @Override
    public String cacheName() {
        return CacheNames.SITE_INFO;
    }

    /**
     * SVG 로고 한 칸의 본문 — 공개 주소 {@code /api/site/logos/{칸}.svg?b=묶음}. 칸 이름·묶음을 모르거나 그 칸이 SVG 가 아니면 비어 있다.
     * 묶음 번호가 없으면(예전 주소) 오늘의 성별 무관 묶음에서 찾는다. 주소에 지문({@code v})이 붙어 브라우저가 오래 캐시하므로 요청이 드물어 사본 없이 DB 를 읽는다.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<String> logoSvg(String variantKey, Long bundleId) {
        Optional<SiteLogoVariant> variant = SiteLogoVariant.fromKey(variantKey);
        if (variant.isEmpty() || logoResolver == null) {
            return Optional.empty();
        }
        Optional<SiteLogoBundleEntity> bundle = bundleId != null ? logoBundles.findById(bundleId)
                : logoResolver.on(ServiceTime.today(clock)).map(SiteLogoResolver.Today::bundle);
        return bundle.map(found -> found.svg(variant.get()));
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
        // 로고(V249 보이기 규칙) — 오늘의 성별 무관 묶음(기념일이 걸리면 그 묶음, 아니면 평소 로고)의 원본 4칸. 대체 순서는 화면 규칙, 옛 logoUrl 은 ① 칸 그대로
        // SVG 칸은 data URI 대신 공개 주소(/api/site/logos/{칸}.svg?b=묶음&v=지문) — 첫 화면 HTML 에 SVG 본문을 싣지 않는다(2026-11-16)
        // 기념일이 안 걸린 날에만 성별 묶음을 더한다 — 로그인한 회원의 성별로 고르는 것은 화면이다
        LocalDate today = ServiceTime.today(clock);
        logoDate = today;
        Optional<SiteLogoResolver.Today> todayLogo = logoResolver == null ? Optional.empty() : logoResolver.on(today);
        SiteLogoUrls logos = todayLogo.map(t -> assets.publicLogoUrls(t.bundle())).orElse(SiteLogoUrls.EMPTY);
        String logoUrl = logos.koLight();
        boolean logoNameIncluded = todayLogo.map(t -> t.bundle().isNameInLogo()).orElse(false);
        SiteGenderLogosView genderLogos = todayLogo.filter(t -> t.male() != null || t.female() != null)
                .map(t -> new SiteGenderLogosView(logoSet(t.male()), logoSet(t.female()))).orElse(null);
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
        // 비회원 댓글 공개 지연 안내 문구 방식(V199) — 사이트 전체 값 하나
        String commentDelayNotice = boardValues.map(BoardDefaultsValues::commentDelayNotice).orElse(BoardFields.DELAY_NOTICE_TIME);
        BoardDisplayView boardDisplay = new BoardDisplayView(dateDisplay, BoardDisplayView.NEW_POST_HOURS, listChipPosition, likeHighlight, viewHighlight,
                highlightColor, listPrefixStyle, commentDelayNotice);
        String theme = SiteThemes.normalize(found.map(SiteSettingEntity::getTheme).orElse(null));
        // 사이트 소개(V203) — 관리자가 저장할 때 소독했다. 본문은 사본에만 두고 info 에는 있는지만 싣는다
        String aboutHtml = found.map(SiteSettingEntity::getAboutHtml).filter(SiteAbouts::present).orElse(null);
        // 방문 수집 스위치 — 설정 사본(30초)에서. 이 사본(20초)의 지문(ETag)에도 들어간다
        SiteInfoResponse info = new SiteInfoResponse(siteName, tagline, logoUrl, icon.orElse(null), ogImageUrl, display, boardDisplay, theme,
                SiteAnalyticsView.of(statsSettings.current()), operatorView(operators.current(), operators.footerNotice()),
                // 등급 목록(V196) — 이 사본(20초)과 지문(ETag)에 함께 든다. 등급 저장 뒤 길어야 사본 시간만큼 늦게 보인다
                grades == null ? List.of() : grades.findAllOrdered().stream()
                        .map(g -> new SiteGradeView(g.getGradeKey(), g.getLabel(), g.getLevel(), g.getSprite())).toList(),
                aboutHtml != null, logos, logoNameIncluded, genderLogos);
        SiteThemeResponse themeView = themeView(theme, themeMenus.findById(theme).map(SiteThemeMenuEntity::toMenu).orElseGet(() -> SiteThemeMenus.initialOf(theme)));
        WebManifestResponse manifest = manifest(siteName, tagline, display.language(), icon.orElse(null));
        boolean ogCustom = customOg.isPresent();
        // 지문에 소개 본문도 넣는다 — 본문만 바뀌어도 /api/site/about 이 304 로 옛 본문을 돌려주지 않게
        return new PublicSiteSnapshot(info, themeView, manifest, ogCustom, aboutHtml, publicUrl,
                fingerprint(info + "|" + themeView + "|" + (aboutHtml == null ? "" : aboutHtml), manifest, ogCustom));
    }

    /** 성별 묶음 하나의 공개 모양 — 정하지 않았으면 null */
    private SiteLogoSetView logoSet(SiteLogoBundleEntity bundle) {
        return bundle == null ? null : new SiteLogoSetView(assets.publicLogoUrls(bundle), bundle.isNameInLogo());
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
