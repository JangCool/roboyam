package com.community.site.service;

import com.community.media.entity.FileEntity;
import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.site.dto.SiteBranding;
import com.community.site.dto.SiteIconUrls;
import com.community.site.entity.SiteSettingEntity;
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

    public SiteAssets(SiteSettingRepository settings, FileRepository files, ImageStorage storage) {
        this.settings = settings;
        this.files = files;
        this.storage = storage;
    }

    /** 사이트 이름과 로고 — 관리자 me · 공개 사이트의 상단 표시가 쓴다. 설정 행이 없으면(설치 전) 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<SiteBranding> branding() {
        return settings.findById(SiteSettingEntity.ROW_ID).map(row -> new SiteBranding(row.getSiteName(), logoUrl(row).orElse(null)));
    }

    /** 지금 로고의 {@code <img src>} — 그림 파일이면 저장소 주소, SVG 면 data URI. 로고가 없거나 설정 행이 없으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<String> logoUrl() {
        return settings.findById(SiteSettingEntity.ROW_ID).flatMap(this::logoUrl);
    }

    /** 이미 읽은 설정 행의 로고 주소 */
    @Transactional(readOnly = true)
    public Optional<String> logoUrl(SiteSettingEntity row) {
        if (row.getLogoSvg() != null) {
            return Optional.of(svgDataUri(row.getLogoSvg()));
        }
        if (row.getLogoFileId() != null) {
            return files.findById(row.getLogoFileId()).map(FileEntity::getStorageKey).map(storage::publicUrl);
        }
        return Optional.empty();
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
