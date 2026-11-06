package com.community.pointshop.service;

import com.community.avatarupload.repository.master.AvatarUploadStore;
import com.community.media.service.ImageStorage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 회원의 지금 아바타 그림을 푼다 — {@code profiles.profile_image} 값 → 그림 주소·정지 그림·움직임(commerce-motion-avatar-contract 「표시」). 한 곳에서 판정한다:
 * <ul>
 *   <li>카탈로그 아바타({@code avatar:<id>}) — {@link AvatarCatalog}(움직이는 것은 정지 그림이 있다).</li>
 *   <li>내가 올린 아바타({@code avatar:upload-mine}) — 그 회원의 승인된 올리기 신청 파일({@code ImageStorage} 공개 주소, 움직이는 GIF 는 첫 장면 PNG 가 정지 그림). 승인된 그림이 없거나
 *       파일이 없으면 {@code basic-default}.</li>
 *   <li>그 밖(비었거나 카탈로그에 없는 값) — {@code basic-default}.</li>
 * </ul>
 * 여러 회원은 {@link #of(Map)} 로 한 번에 — 내가 올린 아바타를 쓰는 회원만 모아 승인된 신청을 한 번 읽는다(N+1 없음).
 */
@Component
public class AvatarLooks {

    /**
     * 그림 한 벌.
     *
     * @param id       카탈로그 아바타 ID · {@code upload-mine} · {@code basic-default}(고르지 않았거나 풀 수 없을 때)
     * @param stillUrl 움직이는 아바타의 정지 그림 — 정지 아바타는 null
     */
    public record Look(String id, String url, String stillUrl, boolean animated) {
    }

    private final AvatarCatalog catalog;
    private final AvatarUploadStore uploads;
    private final ImageStorage storage;

    public AvatarLooks(AvatarCatalog catalog, AvatarUploadStore uploads, ImageStorage storage) {
        this.catalog = catalog;
        this.uploads = uploads;
        this.storage = storage;
    }

    /** 한 회원 — 내가 올린 아바타를 쓰면 승인된 신청을 읽는다 */
    public Look of(long userId, String profileImage) {
        if (AvatarCatalog.isUpload(profileImage)) {
            return upload(uploads.currentOf(List.of(userId)).get(userId));
        }
        return catalogLook(profileImage);
    }

    /** 여러 회원(회원 ID → 고른 값) — 요청한 모든 ID 가 들어 있다 */
    public Map<Long, Look> of(Map<Long, String> profileImages) {
        List<Long> uploaders = profileImages.entrySet().stream().filter(e -> AvatarCatalog.isUpload(e.getValue())).map(Map.Entry::getKey).toList();
        Map<Long, AvatarUploadStore.Row> current = uploaders.isEmpty() ? Map.of() : uploads.currentOf(uploaders);
        Map<Long, Look> out = new HashMap<>();
        profileImages.forEach((id, image) -> out.put(id, AvatarCatalog.isUpload(image) ? upload(current.get(id)) : catalogLook(image)));
        return out;
    }

    /** 승인된 내가 올린 아바타의 그림(없거나 파일이 없으면 기본 아바타) */
    public Look upload(AvatarUploadStore.Row current) {
        if (current == null || !current.hasFiles()) {
            return defaultLook();
        }
        return new Look(AvatarCatalog.UPLOAD_ID, storage.publicUrl(current.imageKey()), current.stillKey() == null ? null : storage.publicUrl(current.stillKey()),
                current.animated());
    }

    /** 고른 값이 없는 회원(프로필 행이 없음 등)·풀 수 없는 값 */
    public Look defaultLook() {
        AvatarCatalog.Avatar avatar = catalog.defaultAvatar();
        return new Look(avatar.id(), avatar.url(), null, false);
    }

    private Look catalogLook(String profileImage) {
        return catalog.selected(profileImage).map(a -> new Look(a.id(), a.url(), a.stillUrl(), a.animated())).orElseGet(this::defaultLook);
    }
}
