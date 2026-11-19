package com.community.site.service;

import com.community.media.entity.FileEntity;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.site.dto.SiteBranding;
import com.community.site.dto.SiteIconUrls;
import com.community.site.dto.SiteLogoUrls;
import com.community.site.entity.SiteLogoBundleEntity;
import com.community.site.entity.SiteLogoVariant;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteLogoBundleRepository;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.service.SiteMediaKeys.IconFile;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사이트 로고·아이콘·링크 미리보기 이미지의 <b>주소를 만드는 곳</b>. 관리자 앱의 설정 화면·{@code me} 와, 나중에 공개 사이트(탭 아이콘·manifest·링크 미리보기 메타·상단 로고)가
 * 같은 규칙으로 주소를 만들도록 shared 에 둔다. 설정 행이 없거나(설치 전) 올려 둔 것이 없으면 비어 있다 — 부르는 쪽은 기본 이미지를 그대로 둔다.
 *
 * <p>주소는 저장소의 공개 주소 그대로다(로컬 디스크는 {@code /media/…} 상대 주소 — 보는 쪽 서버가 같은 주소로 서빙한다). 절대 주소가 필요한 곳(링크 미리보기 메타·manifest)은
 * {@link #absolute}가 사이트 공개 주소({@code site_settings.public_url})를 앞에 붙인다. SVG 로고는 파일이 아니라 서버가 만든 data URI 이다.
 */
@Service
public class SiteAssets {

    private final SiteSettingRepository settings;
    private final FileRepository files;
    private final ImageStorage storage;
    private final SiteLogoBundleRepository bundles;

    public SiteAssets(SiteSettingRepository settings, FileRepository files, ImageStorage storage, SiteLogoBundleRepository bundles) {
        this.bundles = bundles;
        this.settings = settings;
        this.files = files;
        this.storage = storage;
    }

    /** 사이트 이름과 로고(평소 묶음 — 대표 한 장 · 4칸 · 이름 포함 여부) — 관리자 me 가 쓴다. 설정 행이 없으면(설치 전) 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<SiteBranding> branding() {
        return settings.findById(SiteSettingEntity.ROW_ID).map(row -> {
            Optional<SiteLogoBundleEntity> bundle = bundles.findById(row.getLogoDefaultBundleId());
            return new SiteBranding(row.getSiteName(), bundle.flatMap(this::representativeUrl).orElse(null), bundle.map(this::logoUrls).orElse(SiteLogoUrls.EMPTY),
                    bundle.map(SiteLogoBundleEntity::isNameInLogo).orElse(false));
        });
    }

    /** 지금 로고의 {@code <img src>} — 그림 파일이면 저장소 주소, SVG 면 data URI. 로고가 없거나 설정 행이 없으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<String> logoUrl() {
        return settings.findById(SiteSettingEntity.ROW_ID).flatMap(this::logoUrl);
    }

    /**
     * 이미 읽은 설정 행의 로고 주소 — <b>평소 로고 묶음</b>의 ① 한글·밝은 바탕용 칸(V249 — 예전 단일 로고·4칸의 ① 을 쓰던 관리자 me·기본 정보 {@code logoUrl}·메일 로고).
     * 기념일·성별 규칙은 타지 않는다. ① 이 빈 묶음이면 채워진 첫 칸을 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<String> logoUrl(SiteSettingEntity row) {
        return bundles.findById(row.getLogoDefaultBundleId()).flatMap(this::representativeUrl);
    }

    /** 묶음을 대표하는 그림 한 장 — ① 한글·밝은 칸, 비었으면 채워진 첫 칸. 모두 비었으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<String> representativeUrl(SiteLogoBundleEntity bundle) {
        for (SiteLogoVariant variant : SiteLogoVariant.values()) {
            Optional<String> url = logoUrl(bundle, variant);
            if (url.isPresent()) {
                return url;
            }
        }
        return Optional.empty();
    }

    /** 묶음 한 칸의 로고 주소 — 그림 파일이면 저장소 주소, SVG 면 data URI. 빈 칸이면 비어 있다(다른 칸으로 대신하지 않는다) */
    @Transactional(readOnly = true)
    public Optional<String> logoUrl(SiteLogoBundleEntity bundle, SiteLogoVariant variant) {
        String svg = bundle.svg(variant);
        if (svg != null) {
            return Optional.of(svgDataUri(svg));
        }
        Long fileId = bundle.fileId(variant);
        if (fileId != null) {
            return files.findById(fileId).map(FileEntity::getStorageKey).map(storage::publicUrl);
        }
        return Optional.empty();
    }

    /** 공개 사이트가 SVG 로고 한 칸을 받는 주소의 앞부분 — 뒤에 {@code {칸}.svg?b=묶음&v=지문} 이 붙는다(api 서버 {@code PublicSiteController}) */
    public static final String PUBLIC_LOGO_SVG_PATH = "/api/site/logos/";

    /**
     * 공개 사이트용 묶음 4칸 — 그림 파일 칸은 {@link #logoUrls} 와 같고, <b>SVG 칸은 data URI 대신 공개 주소</b>({@code /api/site/logos/koLight.svg?b=묶음&v=지문})다.
     * 사이트 정보는 첫 화면 HTML 에 실리므로 SVG 본문(칸마다 수 KB~수십 KB)을 싣지 않고 브라우저가 따로 받아 오래 캐시하게 한다(2026-11-16). 관리자 앱은 다른 서버라 data URI 를 그대로 쓴다.
     * 주소에 묶음 번호({@code b})를 넣어 기념일·성별 묶음의 SVG 도 같은 주소 꼴로 받는다(V249).
     */
    @Transactional(readOnly = true)
    public SiteLogoUrls publicLogoUrls(SiteLogoBundleEntity bundle) {
        return new SiteLogoUrls(publicLogoUrl(bundle, SiteLogoVariant.KO_LIGHT), publicLogoUrl(bundle, SiteLogoVariant.KO_DARK),
                publicLogoUrl(bundle, SiteLogoVariant.EN_LIGHT), publicLogoUrl(bundle, SiteLogoVariant.EN_DARK));
    }

    private String publicLogoUrl(SiteLogoBundleEntity bundle, SiteLogoVariant variant) {
        String svg = bundle.svg(variant);
        if (svg != null) {
            return PUBLIC_LOGO_SVG_PATH + variant.key() + ".svg?b=" + bundle.getId() + "&v=" + svgFingerprint(svg);
        }
        return logoUrl(bundle, variant).orElse(null);
    }

    /** SVG 본문의 지문(SHA-256 앞 16자) — 공개 주소의 {@code v} 값. 본문이 바뀌면 주소가 바뀌어 오래 캐시해도 낡은 그림이 남지 않는다 */
    public static String svgFingerprint(String svgSource) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(svgSource.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 묶음 4칸의 주소(관리자용 — SVG 는 data URI) — 빈 칸은 null 그대로 둔다(대체 순서는 화면 규칙) */
    @Transactional(readOnly = true)
    public SiteLogoUrls logoUrls(SiteLogoBundleEntity bundle) {
        return new SiteLogoUrls(logoUrl(bundle, SiteLogoVariant.KO_LIGHT).orElse(null), logoUrl(bundle, SiteLogoVariant.KO_DARK).orElse(null),
                logoUrl(bundle, SiteLogoVariant.EN_LIGHT).orElse(null), logoUrl(bundle, SiteLogoVariant.EN_DARK).orElse(null));
    }

    /** 올려 둔 사이트 아이콘의 주소들. 올려 둔 것이 없으면 비어 있다 */
    public Optional<SiteIconUrls> iconUrls(SiteSettingEntity row) {
        String version = row.getIconToken();
        return version == null ? Optional.empty() : Optional.of(iconUrls(version));
    }

    /** 한 버전의 아이콘 주소들 */
    public SiteIconUrls iconUrls(String version) {
        return new SiteIconUrls(version, url(version, IconFile.FAVICON_32), url(version, IconFile.FAVICON_48), url(version, IconFile.APPLE_TOUCH_180),
                url(version, IconFile.ICON_192), url(version, IconFile.ICON_512));
    }

    /** 올려 둔 링크 미리보기 이미지(1200×630 PNG)의 주소. 올려 둔 것이 없으면 비어 있다 */
    public Optional<String> ogUrl(SiteSettingEntity row) {
        return row.getOgToken() == null ? Optional.empty() : Optional.of(ogUrl(row.getOgToken()));
    }

    public String ogUrl(String version) {
        return storage.publicUrl(SiteMediaKeys.og(version));
    }

    /**
     * 절대 주소 — 공개 주소가 있고 {@code url} 이 상대 주소({@code /…})면 앞에 붙인다. 이미 절대 주소(S3 CDN)이거나 data URI 이면 그대로.
     * 링크 미리보기 메타·manifest 처럼 다른 도메인이 읽는 곳에서 쓴다.
     */
    public String absolute(String url, String publicUrl) {
        if (url == null || publicUrl == null || publicUrl.isBlank() || !url.startsWith("/")) {
            return url;
        }
        return publicUrl + url;
    }

    /** SVG 소스를 {@code <img src>} 한 줄로 그리게 하는 data URI */
    public static String svgDataUri(String svgSource) {
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svgSource.getBytes(StandardCharsets.UTF_8));
    }

    private String url(String version, IconFile file) {
        return storage.publicUrl(SiteMediaKeys.icon(version, file));
    }
}
