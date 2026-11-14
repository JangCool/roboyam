package com.community.emoticon.service;

import com.community.zconfiguration.StaticAssetUrls;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.entity.EmoticonImageFormat;
import com.community.emoticon.repository.master.EmoticonImageStore;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.ItemRow;
import com.community.emoticon.repository.master.EmoticonPackStore.RevisionRow;
import com.community.media.service.ImageStorage;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 판(이름·설명·태그·대표·차례)과 그 그림을 한 번에 읽어 응답 조각으로 바꾼다 — 공개·스튜디오·관리자가 같이 쓴다. 그림 주소는 저장소 키면 {@link ImageStorage#publicUrl},
 * 시드 그림이면 앱 정적 파일 주소 그대로(두 앱 public 의 {@code /emoticons/robotikon-…}).
 */
@Component
public class EmoticonContent {

    private final EmoticonPackStore packs;
    private final EmoticonImageStore images;
    private final ImageStorage storage;

    public EmoticonContent(EmoticonPackStore packs, EmoticonImageStore images, ImageStorage storage) {
        this.packs = packs;
        this.images = images;
        this.storage = storage;
    }

    /** 읽은 판 하나 — 차례와 그림(대표 포함) */
    public record Loaded(RevisionRow revision, List<ItemRow> items, Map<Long, ImageRow> images) {

        public ImageRow cover() {
            return revision.coverImageId() == null ? null : images.get(revision.coverImageId());
        }

        public ImageRow imageOf(ItemRow item) {
            return images.get(item.imageId());
        }
    }

    /** 판들을 읽는다(없는 판은 빠진다) — 입력 차례 */
    public Map<Long, Loaded> load(Collection<Long> revisionIds) {
        Map<Long, Loaded> out = new LinkedHashMap<>();
        Set<Long> ids = new HashSet<>(revisionIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, RevisionRow> revisions = packs.revisions(ids);
        Map<Long, List<ItemRow>> items = packs.items(revisions.keySet());
        Set<Long> imageIds = new HashSet<>();
        revisions.values().forEach(r -> {
            if (r.coverImageId() != null) {
                imageIds.add(r.coverImageId());
            }
        });
        items.values().forEach(list -> list.forEach(item -> imageIds.add(item.imageId())));
        Map<Long, ImageRow> loaded = images.byIds(imageIds);
        for (Long id : revisionIds) {
            RevisionRow revision = id == null ? null : revisions.get(id);
            if (revision != null) {
                out.put(id, new Loaded(revision, items.getOrDefault(id, List.of()), loaded));
            }
        }
        return out;
    }

    public Loaded load(long revisionId) {
        return load(List.of(revisionId)).get(revisionId);
    }

    // ── 주소 · 조각 ─────────────────────────────────────────────────────────

    public String url(ImageRow image) {
        if (image == null) {
            return null;
        }
        return url(image.storageKey(), image.staticPath());
    }

    public String url(String storageKey, String staticPath) {
        // 앱에 딸린 시드 그림은 내용 해시를 붙인다(1년 캐시 — StaticAssetUrls), 올린 그림은 저장소 주소(이미 새 이름)
        return staticPath != null ? StaticAssetUrls.of(staticPath) : storage.publicUrl(storageKey);
    }

    /** 움직이는 그림 — 여러 프레임 GIF */
    public static boolean animated(EmoticonImageFormat format, int frames) {
        return format == EmoticonImageFormat.GIF && frames > 1;
    }

    /** 공개 로보티콘 차례 */
    public List<EmoticonPublicView.Item> publicItems(Loaded loaded) {
        return loaded.items().stream().map(item -> {
            ImageRow image = loaded.imageOf(item);
            return new EmoticonPublicView.Item(Long.toString(item.emoticonId()), item.label(), url(image), image != null && animated(image.format(), image.frames()));
        }).toList();
    }

    /** 대표 그림 주소 — 대표가 없으면 첫 로보티콘(user-web 목업과 같다), 둘 다 없으면 null */
    public String coverUrl(Loaded loaded) {
        if (loaded == null) {
            return null;
        }
        if (loaded.cover() != null) {
            return url(loaded.cover());
        }
        return loaded.items().isEmpty() ? null : url(loaded.imageOf(loaded.items().get(0)));
    }

    public EmoticonStudioView.Image studioImage(ImageRow image) {
        return new EmoticonStudioView.Image(Long.toString(image.id()), url(image), image.format().name(), image.width(), image.height(), image.bytes(), image.frames());
    }

    public List<EmoticonStudioView.Emoticon> studioItems(Loaded loaded) {
        return loaded.items().stream().map(item -> {
            ImageRow image = loaded.imageOf(item);
            return new EmoticonStudioView.Emoticon(Long.toString(image.id()), url(image), image.format().name(), image.width(), image.height(), image.bytes(),
                    image.frames(), item.label());
        }).toList();
    }

    /** 관리자 로보티콘 한 개 — {@code id} 는 주어진 값(판의 차례면 로보티콘 ID, 올린 직후면 그림 ID) */
    public EmoticonAdminView.ItemView adminItem(String id, ImageRow image, String alt) {
        boolean gif = image.format() == EmoticonImageFormat.GIF;
        return new EmoticonAdminView.ItemView(id, url(image), alt, image.format().name(), image.width(), image.height(), gif ? image.frames() : null,
                gif ? image.loopForever() : null, image.bytes(), image.contentHash());
    }

    public EmoticonAdminView.CoverView adminCover(ImageRow image) {
        if (image == null) {
            return null;
        }
        return new EmoticonAdminView.CoverView(Long.toString(image.id()), url(image), image.format().name(), image.width(), image.height(), image.bytes());
    }

    public List<EmoticonAdminView.ItemView> adminItems(Loaded loaded) {
        return loaded.items().stream().map(item -> adminItem(Long.toString(item.emoticonId()), loaded.imageOf(item), item.label())).toList();
    }

    public EmoticonAdminView.VersionView adminVersion(Loaded loaded) {
        if (loaded == null) {
            return null;
        }
        RevisionRow r = loaded.revision();
        return new EmoticonAdminView.VersionView(r.version() == null ? 0 : r.version(), r.saleWish().name(), r.priceRequestMemo(), r.name(), r.description(), r.tags(),
                adminCover(loaded.cover()), adminItems(loaded));
    }
}
