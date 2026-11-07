package com.community.popup.service;

import com.community.media.repository.master.FileRepository;
import com.community.media.service.ImageStorage;
import com.community.popup.dto.SitePopupPublicLine;
import com.community.popup.entity.SitePopupEntity;
import com.community.popup.repository.master.SitePopupRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 팝업 목록을 만든다({@code GET /api/site/popups}, api 의 짧은 사본이 부른다).
 *
 * <ul>
 *   <li>노출을 켰고 {@code now} 에 끝나지 않은 팝업만 차례대로 — 아직 시작 전인 것도 담는다(사본을 쓰는 동안 시작 시각이 오면 보여야 하므로 부르는 쪽이
 *       {@link SitePopupPublicLine#inPeriod} 로 매번 다시 거른다).</li>
 *   <li>그림 주소는 하이라이트 대표 이미지와 같이 저장소 공개 주소({@link ImageStorage#publicUrl}). 그림이 없는 IMAGE(파일 행이 지워졌다)는 건너뛴다.</li>
 *   <li>지금 종류가 아닌 칸은 싣지 않는다(null) — 방문자에게 보이지 않는 칸을 내보내지 않는다.</li>
 * </ul>
 */
@Component
public class SitePopupReader {

    private final SitePopupRepository popups;
    private final FileRepository files;
    private final ImageStorage storage;

    public SitePopupReader(SitePopupRepository popups, FileRepository files, ImageStorage storage) {
        this.popups = popups;
        this.files = files;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<SitePopupPublicLine> publicLines(Instant now) {
        List<SitePopupEntity> rows = popups.findAllByVisibleTrueOrderBySortOrderAscIdAsc().stream()
                .filter(p -> p.getShowUntil() == null || p.getShowUntil().isAfter(now))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> imageIds = rows.stream().filter(p -> SitePopups.isImage(p.getKind())).map(SitePopupEntity::getImageFileId).filter(Objects::nonNull).toList();
        Map<Long, String> imageUrls = new HashMap<>();
        if (!imageIds.isEmpty()) {
            files.findAllById(imageIds).forEach(f -> imageUrls.put(f.getId(), storage.publicUrl(f.getStorageKey())));
        }
        List<SitePopupPublicLine> out = new ArrayList<>(rows.size());
        for (SitePopupEntity p : rows) {
            boolean image = SitePopups.isImage(p.getKind());
            String imageUrl = image && p.getImageFileId() != null ? imageUrls.get(p.getImageFileId()) : null;
            if (image && imageUrl == null) {
                continue;
            }
            out.add(new SitePopupPublicLine(p.getId(), SitePopups.version(p), p.getKind(), imageUrl, image ? p.getImageAlt() : null, p.getLink(),
                    image ? null : p.getHeading(), image ? null : p.getBody(), image ? null : p.getButtonLabel(), p.getTarget(), SitePopups.devicesOf(p),
                    p.getHideOption(), p.getShowFrom(), p.getShowUntil()));
        }
        return out;
    }
}
