package com.community.pointshop.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프로필 아바타 카탈로그(결정 14 · 움직이는 아바타 commerce-motion-avatar-contract) — 배포되는 자산 {@code avatars/manifest.json}(정지 328종 — community 와 같은 무료 22 · 유료 306,
 * + 움직이는 아바타 8종 카테고리 {@code motion})을 메모리에 한 번 올린다. DB 설정이 아니다. 순서·이름·카테고리·움직임은 이 파일 그대로, 가격·판매 여부는 아바타 품목
 * ({@code point_shop_items} 의 {@code PROFILE_AVATAR}, V86·V157 시드)에서 읽는다.
 *
 * <ul>
 *   <li><b>무료 판정</b>: 카테고리 {@code basic}·{@code hobby} 이면 무료(이 한 곳). 무료 아바타는 품목 행이 없다. 움직이는 아바타는 모두 유료다.</li>
 *   <li><b>그림 주소</b>: 정지 {@code /avatars/roboyam/<id>.svg} · 움직이는 것 {@code /avatars/motion/<id>.svg} + 정지 그림 {@code /avatars/motion/<id>-still.svg}
 *       (두 앱의 {@code public/avatars/…}). 화면은 {@code <img>} 로만 그린다.</li>
 *   <li><b>고른 아바타</b>: {@code profiles.profile_image} 에 {@code avatar:<id>}. 비었거나 카탈로그에 없는 값이면 {@link #DEFAULT_ID} 로 그린다.
 *       {@code avatar:upload-mine}({@link #UPLOAD_ID} — 승인된 내가 올린 아바타)은 카탈로그 밖이라 {@link AvatarLooks} 가 푼다.</li>
 * </ul>
 */
@Component
public class AvatarCatalog {

    public static final String RESOURCE = "avatars/manifest.json";
    /** 고르지 않은 회원·카탈로그에 없는 값의 표시 */
    public static final String DEFAULT_ID = "basic-default";
    public static final Set<String> FREE_CATEGORIES = Set.of("basic", "hobby");
    public static final String URL_PREFIX = "/avatars/roboyam/";
    /** 움직이는 아바타 그림의 머리(정지 그림은 {@code <id>-still.svg}) */
    public static final String MOTION_URL_PREFIX = "/avatars/motion/";
    /** {@code profiles.profile_image} 의 아바타 값 접두 */
    public static final String PROFILE_IMAGE_PREFIX = "avatar:";
    /** 아바타 품목 코드 접두({@code avatar-<id>}) */
    public static final String ITEM_CODE_PREFIX = "avatar-";
    /** 승인된 「내가 올린 아바타」의 자리 ID(카탈로그에 없고 상점에 없다) */
    public static final String UPLOAD_ID = "upload-mine";
    /** 내가 올린 아바타의 카테고리(아바타 목록에 승인된 그림이 있을 때만 생긴다) */
    public static final String UPLOAD_CATEGORY = "upload";

    /** 아바타 하나 — 카탈로그 순서 {@code order}(0부터) */
    public record Avatar(String id, String name, String category, boolean free, boolean animated, int order) {

        public String url() {
            return animated ? MOTION_URL_PREFIX + id + ".svg" : URL_PREFIX + id + ".svg";
        }

        /** 움직이는 아바타의 정지 그림(움직임 줄이기·글 목록) — 정지 아바타는 null */
        public String stillUrl() {
            return animated ? MOTION_URL_PREFIX + id + "-still.svg" : null;
        }
    }

    /** 카테고리 하나(파일 차례) */
    public record Category(String id, boolean free, boolean animated, int count) {
    }

    private final List<Avatar> avatars;
    private final Map<String, Avatar> byId;
    private final List<Category> categories;

    public AvatarCatalog() {
        JsonNode root;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            root = JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("아바타 카탈로그를 읽지 못했다: " + RESOURCE, e);
        }
        List<Avatar> list = new ArrayList<>();
        Map<String, Avatar> map = new LinkedHashMap<>();
        int order = 0;
        for (JsonNode item : root.get("items")) {
            String category = item.get("category").asString();
            boolean animated = item.has("animated") && item.get("animated").asBoolean();
            Avatar avatar = new Avatar(item.get("id").asString(), item.get("name").asString(), category, FREE_CATEGORIES.contains(category), animated, order++);
            if (map.put(avatar.id(), avatar) != null) {
                throw new IllegalStateException("아바타 ID 가 겹친다: " + avatar.id());
            }
            if (avatar.id().equals(UPLOAD_ID) || (animated && avatar.free())) {
                throw new IllegalStateException("아바타 카탈로그에 쓸 수 없는 줄: " + avatar.id());
            }
            list.add(avatar);
        }
        List<Category> cats = new ArrayList<>();
        for (JsonNode category : root.get("categories")) {
            String id = category.get("id").asString();
            boolean animated = category.has("animated") && category.get("animated").asBoolean();
            int count = (int) list.stream().filter(avatar -> avatar.category().equals(id)).count();
            if (count != category.get("count").asInt() || list.stream().anyMatch(avatar -> avatar.category().equals(id) && avatar.animated() != animated)) {
                throw new IllegalStateException("아바타 카테고리 개수·움직임이 목록과 다르다: " + id);
            }
            cats.add(new Category(id, FREE_CATEGORIES.contains(id), animated, count));
        }
        if (list.size() != root.get("total").asInt() || !map.containsKey(DEFAULT_ID)) {
            throw new IllegalStateException("아바타 카탈로그의 합계가 맞지 않거나 기본 아바타가 없다");
        }
        this.avatars = List.copyOf(list);
        this.byId = Map.copyOf(map);
        this.categories = List.copyOf(cats);
    }

    /** 카탈로그 순서 전부(338 — 정지 330 + 움직이는 8, 목수 2종은 V183 에서 맨 뒤에 더했다) */
    public List<Avatar> all() {
        return avatars;
    }

    /** 유료(무료 카테고리가 아닌 것 — 정지 306 + 움직이는 8), 카탈로그 순서 */
    public List<Avatar> paid() {
        return avatars.stream().filter(avatar -> !avatar.free()).toList();
    }

    public List<Category> categories() {
        return categories;
    }

    public Optional<Avatar> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Optional<Category> category(String id) {
        return categories.stream().filter(category -> category.id().equals(id)).findFirst();
    }

    /** 기본 아바타 */
    public Avatar defaultAvatar() {
        return byId.get(DEFAULT_ID);
    }

    /** 그 품목 코드의 아바타 ID({@code avatar-<id>} — 아니면 빈 값) */
    public static Optional<String> avatarIdOfCode(String code) {
        return code != null && code.startsWith(ITEM_CODE_PREFIX) ? Optional.of(code.substring(ITEM_CODE_PREFIX.length())) : Optional.empty();
    }

    /** {@code profiles.profile_image} 값이 가리키는 카탈로그 아바타(없거나 아바타 값이 아니거나 카탈로그에 없으면 빈 값 — {@code upload-mine} 도 빈 값) */
    public Optional<Avatar> selected(String profileImage) {
        if (profileImage == null || !profileImage.startsWith(PROFILE_IMAGE_PREFIX)) {
            return Optional.empty();
        }
        return find(profileImage.substring(PROFILE_IMAGE_PREFIX.length()));
    }

    /** {@code profiles.profile_image} 값이 내가 올린 아바타인가 */
    public static boolean isUpload(String profileImage) {
        return profileImageOf(UPLOAD_ID).equals(profileImage);
    }

    /** 그릴 아바타 주소 — 고른 것이 카탈로그에 있으면 그것, 아니면 {@link #DEFAULT_ID}(내가 올린 아바타는 {@link AvatarLooks}) */
    public String urlOf(String profileImage) {
        return selected(profileImage).map(Avatar::url).orElse(defaultAvatar().url());
    }

    /** 그 아바타 ID 의 그림 주소(카탈로그에 없으면 null) — 품목·내 아이템 줄 */
    public String urlOfId(String avatarId) {
        return find(avatarId).map(Avatar::url).orElse(null);
    }

    public static String profileImageOf(String avatarId) {
        return PROFILE_IMAGE_PREFIX + avatarId;
    }
}
