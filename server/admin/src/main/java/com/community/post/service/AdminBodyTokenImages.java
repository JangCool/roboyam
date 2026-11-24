package com.community.post.service;

import com.community.emoticon.service.EmoticonGuard;
import com.community.emoticon.service.EmoticonShopService;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.jjal.service.JjalGuard;
import com.community.media.service.ImageStorage;
import com.community.post.dto.response.AdminBodyEmoticonView;
import com.community.post.dto.response.AdminBodyJjalView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 글·신고 서랍이 본문의 로보티콘 {@code :emo-ID:}·짤 {@code :jjal-ID:} 토큰을 그림으로 그리도록 본문에 나온 토큰의 그림 정보를 모은다 — admin-web 은 공개
 * 카탈로그({@code GET /api/emoticons/catalog})·짤 조회({@code GET /api/jjals})를 부를 수 없다.
 *
 * <ul>
 *   <li><b>토큰</b>: 서버 토큰 파서({@link EmoticonGuard#ids}·{@link JjalGuard#ids} — 화면 {@code util-rich-emoticon.ts}·{@code util-rich-jjal.ts} 와 같은 꼴)로 본문 전체 글자에서 찾는다.
 *       여러 본문(지금 본문 · 신고 당시 내용)을 합쳐 겹치면 한 번, 처음 나온 차례.</li>
 *   <li><b>로보티콘</b>: 공개 카탈로그와 같은 범위(판매 중 · 판매 중단 팩의 상점 판)·같은 {@code url}·{@code label}({@link EmoticonShopService#catalogItems}). 카탈로그에 없는 ID 는 빠진다.</li>
 *   <li><b>짤</b>: 공개 짤 조회와 같은 판정 — 지금 공개 주소로 내보낼 수 없는 짤({@link JjalFileEntity#servableAt} 거짓: 내려감 · 비공개 위치 · 불법촬영물로 지움)은
 *       {@code url null}·{@code blocked true}. 없는 ID 는 빠진다.</li>
 * </ul>
 */
@Component
@Transactional(readOnly = true)
public class AdminBodyTokenImages {

    /** 본문들의 토큰 그림 — 토큰이 없으면 둘 다 빈 목록 */
    public record Images(List<AdminBodyEmoticonView> emoticons, List<AdminBodyJjalView> jjals) {

        public static final Images EMPTY = new Images(List.of(), List.of());
    }

    private final EmoticonShopService emoticons;
    private final JjalFileRepository jjalFiles;
    private final ImageStorage storage;

    public AdminBodyTokenImages(EmoticonShopService emoticons, JjalFileRepository jjalFiles, ImageStorage storage) {
        this.emoticons = emoticons;
        this.jjalFiles = jjalFiles;
        this.storage = storage;
    }

    /**
     * @param bodies 본문들(null·빈 글자는 건너뛴다)
     */
    public Images of(String... bodies) {
        Set<Long> emoticonIds = new LinkedHashSet<>();
        Set<String> jjalIds = new LinkedHashSet<>();
        for (String body : bodies) {
            if (body == null || body.isEmpty()) {
                continue;
            }
            emoticonIds.addAll(EmoticonGuard.ids(body));
            jjalIds.addAll(JjalGuard.ids(body));
        }
        if (emoticonIds.isEmpty() && jjalIds.isEmpty()) {
            return Images.EMPTY;
        }
        return new Images(emoticonViews(emoticonIds), jjalViews(jjalIds, Instant.now()));
    }

    private List<AdminBodyEmoticonView> emoticonViews(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return emoticons.catalogItems(ids).stream().map(i -> new AdminBodyEmoticonView(i.id(), i.label(), i.url())).toList();
    }

    private List<AdminBodyJjalView> jjalViews(Set<String> ids, Instant now) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, JjalFileEntity> byId = jjalFiles.findAllByPublicIdIn(ids).stream()
                .collect(Collectors.toMap(JjalFileEntity::getPublicId, Function.identity(), (a, b) -> a));
        List<AdminBodyJjalView> out = new ArrayList<>(byId.size());
        for (String id : ids) {
            JjalFileEntity f = byId.get(id);
            if (f == null) {
                continue;
            }
            boolean blocked = !f.servableAt(now);
            out.add(new AdminBodyJjalView(f.getPublicId(), blocked ? null : storage.publicUrl(f.getStorageKey()), f.getWidth(), f.getHeight(), f.isAnimated(), blocked));
        }
        return out;
    }
}
