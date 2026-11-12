package com.community.installation.service;

import com.community.media.service.ImageCodec;
import com.community.media.service.ImageFormat;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.site.service.SiteBrandRender;
import com.community.site.service.SiteImageFiles;
import com.community.site.service.SiteMediaKeys;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치 구성의 <b>브랜드 그림</b> — 사이트 아이콘(512)·링크 미리보기 이미지(1200×630)를 관리자 「사이트 아이콘」·「링크 미리보기」에 미리 넣는다.
 * 구성이 {@code siteBrand: true}(아이로빗)일 때 설치가 커밋된 직후 {@link InstallationService#complete} 가 부른다 — <b>데모 포함 여부와 무관</b>
 * (사용자 2026-11-13 「아이콘도 늘 넣게」 — 그 전에는 데모를 넣을 때만 {@code DemoDataInstaller} 가 넣었다).
 *
 * <ul>
 *   <li>그림: {@code install-presets/brand/site-icon.png} · {@code site-og.png}(원본 {@code design/brand/}). 관리자 올리기와 같은 규칙({@link SiteBrandRender})으로
 *       아이콘 여러 크기·미리보기 PNG 를 만들어 저장소에 쓰고 사이트 설정의 버전 토큰을 바꾼다.</li>
 *   <li>아직 기본(올린 것 없음)일 때만 — 이미 있으면 두기. 그래서 다시 불러도 바뀌는 것이 없다.</li>
 *   <li>덤이라 실패해도 설치는 그대로다(경고만, 이번에 쓴 파일은 지운다). 부르는 쪽도 예외를 삼킨다.</li>
 * </ul>
 */
@Service
public class SiteBrandInstaller {

    private static final Logger log = LoggerFactory.getLogger(SiteBrandInstaller.class);

    private final SiteSettingRepository siteSettings;
    private final SiteImageFiles imageFiles;
    private final ImageCodec codec;

    public SiteBrandInstaller(SiteSettingRepository siteSettings, SiteImageFiles imageFiles, ImageCodec codec) {
        this.siteSettings = siteSettings;
        this.imageFiles = imageFiles;
        this.codec = codec;
    }

    /**
     * 사이트 아이콘·링크 미리보기 이미지가 아직 기본이면 브랜드 그림을 넣는다.
     *
     * @return 넣은 그림 수(아이콘 1 + 미리보기 1, 이미 있거나 실패하면 그만큼 적다)
     */
    @Transactional
    public int applyIfDefault(long rootOperatorId, Instant now) {
        SiteSettingEntity row = siteSettings.findById(SiteSettingEntity.ROW_ID).orElse(null);
        if (row == null) {
            return 0;
        }
        int done = 0;
        if (row.getIconToken() == null) {
            Map<String, byte[]> files = new LinkedHashMap<>();
            try {
                String version = imageFiles.newVersion();
                byte[] source = brandFile("site-icon.png");
                codec.limited(() -> SiteBrandRender.iconFiles(codec.decode(source, ImageFormat.PNG))).forEach((icon, bytes) -> files.put(SiteMediaKeys.icon(version, icon), bytes));
                imageFiles.writeAll(files);
                row.changeIconToken(version, rootOperatorId, now);
                done++;
            } catch (IOException | RuntimeException e) {
                imageFiles.deleteQuietly(List.copyOf(files.keySet()));
                log.warn("설치 — 사이트 아이콘을 넣지 못했다(설치는 그대로)", e);
            }
        }
        if (row.getOgToken() == null) {
            String key = null;
            try {
                String version = imageFiles.newVersion();
                byte[] source = brandFile("site-og.png");
                byte[] png = codec.limited(() -> SiteBrandRender.ogPng(codec.decode(source, ImageFormat.PNG)));
                key = SiteMediaKeys.og(version);
                imageFiles.writeAll(Map.of(key, png));
                row.changeOgToken(version, rootOperatorId, now);
                done++;
            } catch (IOException | RuntimeException e) {
                if (key != null) {
                    imageFiles.deleteQuietly(List.of(key));
                }
                log.warn("설치 — 링크 미리보기 이미지를 넣지 못했다(설치는 그대로)", e);
            }
        }
        if (done > 0) {
            log.info("설치 — 브랜드 그림 {}장을 사이트 아이콘·링크 미리보기에 넣었다", done);
        }
        return done;
    }

    private static byte[] brandFile(String name) throws IOException {
        try (InputStream in = SiteBrandInstaller.class.getResourceAsStream("/install-presets/brand/" + name)) {
            if (in == null) {
                throw new IOException("브랜드 그림이 없다: " + name);
            }
            return in.readAllBytes();
        }
    }
}
