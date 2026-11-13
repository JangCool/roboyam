package com.community.post.service;

import com.community.media.entity.FileEntity;
import com.community.media.entity.FilePurpose;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepository;
import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.post.entity.PostThumbnailEntity;
import com.community.post.entity.QPostEntity;
import com.community.post.exception.ThumbnailLockedException;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.post.repository.master.PostThumbnailRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.querydsl.core.Tuple;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 글의 대표 썸네일 — 목록 썸네일 결정·보기·바꾸기를 한 곳에서(docs/features/list-thumbnail-contract.md 「2단계」). 공개 서버(목록 {@code PostThumbnails} · 글 보기 ·
 * 글쓴이 저장)와 관리자 서버(글 상세 · {@code PUT /api/admin/posts/{id}/thumbnail})가 같이 쓴다.
 *
 * <h2>목록 결정({@link #listUrls})</h2>
 * 선택 행({@code post_thumbnails})이 있으면 — {@code NONE} → 썸네일 없음(YouTube 도 쓰지 않는다) · {@code IMAGE} → 그 본문 이미지 · {@code UPLOAD} → 올린 그림.
 * 그 파일이 그 글에 쓰이는 중(IN_USE + 같은 글 + 맞는 용도)이 아니면 자동으로 본다. 행이 없으면 자동 = 첫 본문 이미지, 없으면 본문 첫 YouTube 영상({@code hqdefault}).
 * 파일 주소는 목록 썸네일 키 ?? 원본 키(V206). 한 쪽의 글을 질의 네 번까지(선택 · 파일 · 첫 이미지 · YouTube 본문) — 글 수에 비례해 늘지 않는다.
 *
 * <h2>바꾸기</h2>
 * 부르는 쪽이 <b>글 행을 잠근 트랜잭션 안에서</b> 부른다(글쓴이 고치기와 운영자 조치가 줄을 선다). 올린 그림은 정하면 그 글에 IN_USE, 바뀌거나 자동으로 되돌리면 PENDING 으로
 * 돌아가 「올리다 만 파일 정리」가 지운다. 본문 이미지(IMAGE)는 본문 동기화({@code PostImages#bind})가 주인이라 여기서 붙이거나 떼지 않는다.
 */
@Component
public class PostThumbnailChoices {

    /** 본문의 YouTube 영상 표식 — 목록 썸네일은 첫 영상의 {@code hqdefault} */
    private static final Pattern YOUTUBE = Pattern.compile("data-youtube-id=\"([A-Za-z0-9_-]{11})\"");
    /** 본문의 이미지 표식(서버가 채운 태그) — 관리자 고르기 격자의 본문 차례 */
    private static final Pattern IMAGE_ID = Pattern.compile("data-image-id=\"([0-9]{1,19})\"");
    private static final QPostEntity POST = QPostEntity.postEntity;
    /** 자동 그림의 출처 */
    public static final String FROM_IMAGE = "IMAGE";
    public static final String FROM_VIDEO = "VIDEO";

    private final PostThumbnailRepository choices;
    private final FileRepository files;
    private final FileRepositoryQDSL fileQueries;
    private final PostRepositoryQDSL posts;
    private final ImageStorage storage;

    public PostThumbnailChoices(PostThumbnailRepository choices, FileRepository files, FileRepositoryQDSL fileQueries, PostRepositoryQDSL posts,
                                ImageStorage storage) {
        this.choices = choices;
        this.files = files;
        this.fileQueries = fileQueries;
        this.posts = posts;
        this.storage = storage;
    }

    /** 글에 붙은 본문 이미지 하나 — 관리자 고르기 격자 */
    public record Image(long imageId, String url) {
    }

    /**
     * 글 하나의 대표 썸네일 상태.
     *
     * @param mode     AUTO · IMAGE · UPLOAD · NONE — 고른 파일이 더는 그 글에 쓰이지 않으면 AUTO 로 보인다
     * @param setBy    AUTHOR · OPERATOR, 자동이면 null(운영자가 고른 파일이 빠져 자동으로 보이는 동안에도 OPERATOR — 잠금은 그대로)
     * @param imageId  IMAGE 일 때 본문 이미지 ID
     * @param fileId   UPLOAD 일 때 올린 그림 ID
     * @param url      지금 목록에 보이는 썸네일 주소(없으면 null)
     * @param autoUrl  자동이면 쓰일 그림(첫 본문 이미지 → 첫 YouTube 영상), 없으면 null
     * @param autoFrom {@link #FROM_IMAGE} · {@link #FROM_VIDEO} · null
     * @param images   글에 붙은 본문 이미지(본문 차례)
     */
    public record Detail(String mode, String setBy, Long imageId, Long fileId, String url, String autoUrl, String autoFrom, List<Image> images) {
    }

    // ── 목록 ──────────────────────────────────────────────────────────────

    /** 글 ID → 목록 썸네일 주소(없는 글은 빠진다) */
    public Map<Long, String> listUrls(Collection<Long> postIds) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        Set<Long> decided = new HashSet<>();
        List<PostThumbnailEntity> rows = choices.findByPostIdIn(new LinkedHashSet<>(postIds));
        Map<Long, FileEntity> byId = filesOf(rows.stream().map(PostThumbnailEntity::getFileId).filter(Objects::nonNull).toList());
        for (PostThumbnailEntity row : rows) {
            if (PostThumbnailEntity.NONE.equals(row.getMode())) {
                decided.add(row.getPostId());
                continue;
            }
            FileEntity file = byId.get(row.getFileId());
            if (valid(row, file)) {
                out.put(row.getPostId(), urlOf(file));
                decided.add(row.getPostId());
            }
        }
        List<Long> auto = postIds.stream().filter(id -> !decided.contains(id)).distinct().toList();
        fileQueries.firstImageKeys(auto).forEach((postId, key) -> out.put(postId, storage.publicUrl(key)));
        List<Long> rest = auto.stream().filter(id -> !out.containsKey(id)).toList();
        for (Tuple t : posts.youtubeContents(rest)) {
            String video = youtubeOf(t.get(POST.content));
            if (video != null) {
                out.put(t.get(POST.id), video);
            }
        }
        return out;
    }

    // ── 보기 ──────────────────────────────────────────────────────────────

    /**
     * 글 하나의 상태 — 질의 두세 번(선택 · 본문 이미지 · 고른 그림).
     *
     * @param content 글 본문(HTML — 이미지 차례·YouTube 를 본다, null 이면 없음)
     */
    public Detail detail(long postId, String content) {
        PostThumbnailEntity row = choices.findById(postId).orElse(null);
        Map<Long, FileEntity> attached = new LinkedHashMap<>();
        fileQueries.usedPostImages(postId).forEach(f -> attached.put(f.getId(), f));
        List<Image> images = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (content != null) {
            Matcher m = IMAGE_ID.matcher(content);
            while (m.find()) {
                Long id = parseLong(m.group(1));
                FileEntity f = id == null ? null : attached.get(id);
                if (f != null && seen.add(id)) {
                    images.add(new Image(id, urlOf(f)));
                }
            }
        }
        attached.values().stream().filter(f -> seen.add(f.getId())).forEach(f -> images.add(new Image(f.getId(), urlOf(f)))); // 본문 표식에 없는 것(딜 이미지 등)은 뒤에
        // 자동 그림 = 목록과 같은 규칙(먼저 올린 본문 이미지 → 첫 YouTube)
        FileEntity first = attached.values().stream().findFirst().orElse(null);
        String video = content == null ? null : youtubeOf(content);
        String autoUrl = first != null ? urlOf(first) : video;
        String autoFrom = first != null ? FROM_IMAGE : video != null ? FROM_VIDEO : null;
        if (row == null) {
            return new Detail(PostThumbnailEntity.AUTO, null, null, null, autoUrl, autoUrl, autoFrom, images);
        }
        if (PostThumbnailEntity.NONE.equals(row.getMode())) {
            return new Detail(PostThumbnailEntity.NONE, row.getSetBy(), null, null, null, autoUrl, autoFrom, images);
        }
        FileEntity file = PostThumbnailEntity.IMAGE.equals(row.getMode()) ? attached.get(row.getFileId())
                : files.findById(row.getFileId()).orElse(null);
        if (!valid(row, file)) {
            // 고른 파일이 더는 그 글에 쓰이지 않는다(본문에서 뺀 이미지 등) — 목록은 자동. 운영자가 정한 것이면 잠금은 남긴다
            return new Detail(PostThumbnailEntity.AUTO, row.byOperator() ? PostThumbnailEntity.OPERATOR : null, null, null, autoUrl, autoUrl, autoFrom, images);
        }
        boolean image = PostThumbnailEntity.IMAGE.equals(row.getMode());
        return new Detail(row.getMode(), row.getSetBy(), image ? file.getId() : null, image ? null : file.getId(), urlOf(file), autoUrl, autoFrom, images);
    }

    // ── 글쓴이 ─────────────────────────────────────────────────────────────

    /** 요청의 방식 글자 → 정해진 값(대문자). 모르는 값·빈 값이면 null */
    public static String mode(String raw) {
        if (raw == null) {
            return null;
        }
        String m = raw.strip().toUpperCase(Locale.ROOT);
        return switch (m) {
            case PostThumbnailEntity.AUTO, PostThumbnailEntity.IMAGE, PostThumbnailEntity.UPLOAD, PostThumbnailEntity.NONE -> m;
            default -> null;
        };
    }

    /** ID 글자 → 숫자(아니면 null) */
    public static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 글쓴이 입력 검사(트랜잭션 밖, 잠그지 않음) — 틀리면 {@code fields.thumbnail} 에 문구를 넣는다(다른 칸 오류와 함께 400). 방식이 맞지 않음 · IMAGE 인데 그 본문에 없는 이미지 ·
     * UPLOAD 인데 내가 올린 쓰이지 않은 대표 그림(또는 이 글이 이미 쓰던 그 그림)이 아님.
     *
     * @param bodyImageIds 저장할 본문의 이미지 ID
     * @param postId       고치기면 글 ID, 쓰기면 null
     */
    public void checkAuthor(String mode, Long imageId, Long fileId, Collection<Long> bodyImageIds, long authorId, Long postId, Map<String, MessageRef> fields) {
        if (mode == null) {
            fields.put("thumbnail", new MessageRef("post.thumbnail.mode"));
            return;
        }
        if (PostThumbnailEntity.IMAGE.equals(mode) && (imageId == null || !bodyImageIds.contains(imageId))) {
            fields.put("thumbnail", new MessageRef("post.thumbnail.image"));
        } else if (PostThumbnailEntity.UPLOAD.equals(mode)) {
            FileEntity file = fileId == null ? null : files.findById(fileId).orElse(null);
            if (!usableUploadByAuthor(file, authorId, postId)) {
                fields.put("thumbnail", new MessageRef("post.thumbnail.file"));
            }
        }
    }

    /** 운영자가 정한 글이면 409 THUMBNAIL_LOCKED(잠그지 않고 본다 — 저장 트랜잭션 안에서 {@link #applyAuthor} 가 다시 본다) */
    public void requireNotLocked(long postId) {
        if (choices.findById(postId).filter(PostThumbnailEntity::byOperator).isPresent()) {
            throw new ThumbnailLockedException();
        }
    }

    /**
     * 글쓴이가 정한다 — 글 행을 잠근 저장 트랜잭션 안에서, 본문 이미지를 붙인 <b>뒤</b>({@code PostImages#bind}) 부른다. 운영자가 정한 글이면 409 THUMBNAIL_LOCKED,
     * 파일이 그사이 쓸 수 없게 됐으면 400 {@code fields.thumbnail}. AUTO 는 행을 지운다.
     */
    public void applyAuthor(long postId, long authorId, String mode, Long imageId, Long fileId, Instant now) {
        PostThumbnailEntity row = choices.findById(postId).orElse(null);
        if (row != null && row.byOperator()) {
            throw new ThumbnailLockedException();
        }
        Long target = switch (mode) {
            case PostThumbnailEntity.IMAGE -> {
                FileEntity file = files.findById(imageId).orElse(null);
                if (file == null || file.getPurpose() != FilePurpose.POST_IMAGE || file.getStatus() != FileStatus.IN_USE || !Objects.equals(file.getPostId(), postId)) {
                    throw ValidationException.of("thumbnail", new MessageRef("post.thumbnail.image"));
                }
                yield imageId;
            }
            case PostThumbnailEntity.UPLOAD -> {
                FileEntity file = files.findWithLockById(fileId).orElse(null);
                if (!usableUploadByAuthor(file, authorId, postId)) {
                    throw ValidationException.of("thumbnail", new MessageRef("post.thumbnail.file"));
                }
                file.attachToPost(postId, now);
                yield fileId;
            }
            default -> null;
        };
        store(row, postId, mode, target, PostThumbnailEntity.AUTHOR, authorId, now);
    }

    // ── 운영자 ─────────────────────────────────────────────────────────────

    /** 운영자 저장 전후(감사 기록용) */
    public record Change(String fromMode, String fromSetBy, Long fromFileId, String toMode, Long toFileId) {
    }

    /**
     * 운영자가 정한다(게시판 옵션과 무관) — 글 행을 잠근 트랜잭션 안에서. AUTO 는 운영자·글쓴이 설정을 모두 지우고(행 삭제) 자동으로 되돌린다(잠금이 풀린다).
     * IMAGE 는 그 글에 쓰이는 본문 이미지만(아니면 400 {@code fields.imageId}), UPLOAD 는 운영자가 올린 쓰이지 않은 대표 그림 또는 이 글이 이미 쓰는 그 그림만(400 {@code fields.fileId}).
     *
     * @param mode 정해진 값({@link #mode}) — 모르는 값이면 400 {@code fields.mode}
     */
    public Change applyOperator(long postId, long operatorId, String mode, Long imageId, Long fileId, Instant now) {
        if (mode == null) {
            throw ValidationException.of("mode", new MessageRef("post.thumbnail.mode"));
        }
        PostThumbnailEntity row = choices.findById(postId).orElse(null);
        String fromMode = row == null ? PostThumbnailEntity.AUTO : row.getMode();
        String fromSetBy = row == null ? null : row.getSetBy();
        Long fromFile = row == null ? null : row.getFileId();
        Long target = switch (mode) {
            case PostThumbnailEntity.IMAGE -> {
                FileEntity file = imageId == null ? null : files.findById(imageId).orElse(null);
                if (file == null || file.getPurpose() != FilePurpose.POST_IMAGE || file.getStatus() != FileStatus.IN_USE || !Objects.equals(file.getPostId(), postId)) {
                    throw ValidationException.of("imageId", new MessageRef("post.thumbnail.image"));
                }
                yield imageId;
            }
            case PostThumbnailEntity.UPLOAD -> {
                FileEntity file = fileId == null ? null : files.findWithLockById(fileId).orElse(null);
                boolean mine = file != null && file.getPurpose() == FilePurpose.POST_THUMBNAIL
                        && ((file.getStatus() == FileStatus.PENDING && file.getUploadedBy() != null)
                        || (file.getStatus() == FileStatus.IN_USE && Objects.equals(file.getPostId(), postId)));
                if (!mine) {
                    throw ValidationException.of("fileId", new MessageRef("post.thumbnail.file"));
                }
                file.attachToPost(postId, now);
                yield fileId;
            }
            default -> null;
        };
        store(row, postId, mode, target, PostThumbnailEntity.OPERATOR, operatorId, now);
        return new Change(fromMode, fromSetBy, fromFile, mode, target);
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 행을 바꾸고, 빠지게 된 예전 올린 그림을 PENDING 으로 돌린다(정리 배치가 지운다) */
    private void store(PostThumbnailEntity row, long postId, String mode, Long fileId, String setBy, Long setById, Instant now) {
        Long oldUpload = row != null && PostThumbnailEntity.UPLOAD.equals(row.getMode()) ? row.getFileId() : null;
        if (PostThumbnailEntity.AUTO.equals(mode)) {
            if (row != null) {
                choices.delete(row);
            }
        } else if (row == null) {
            choices.persist(PostThumbnailEntity.create(postId, mode, fileId, setBy, setById, now));
        } else {
            row.change(mode, fileId, setBy, setById, now);
        }
        boolean keepOld = oldUpload != null && PostThumbnailEntity.UPLOAD.equals(mode) && oldUpload.equals(fileId);
        if (oldUpload != null && !keepOld) {
            files.findWithLockById(oldUpload)
                    .filter(f -> f.getPurpose() == FilePurpose.POST_THUMBNAIL && f.getStatus() == FileStatus.IN_USE && Objects.equals(f.getPostId(), postId))
                    .ifPresent(FileEntity::release);
        }
    }

    private static boolean usableUploadByAuthor(FileEntity file, long authorId, Long postId) {
        if (file == null || file.getPurpose() != FilePurpose.POST_THUMBNAIL) {
            return false;
        }
        if (postId != null && file.getStatus() == FileStatus.IN_USE && postId.equals(file.getPostId())) {
            return true; // 이 글이 이미 쓰는 그 그림
        }
        return file.getStatus() == FileStatus.PENDING && Objects.equals(file.getUploadedByUser(), authorId);
    }

    /** 고른 파일이 그 글에 쓰이는 중인가(용도까지) */
    private static boolean valid(PostThumbnailEntity row, FileEntity file) {
        if (file == null || file.getStatus() != FileStatus.IN_USE || !Objects.equals(file.getPostId(), row.getPostId())) {
            return false;
        }
        FilePurpose wanted = PostThumbnailEntity.IMAGE.equals(row.getMode()) ? FilePurpose.POST_IMAGE : FilePurpose.POST_THUMBNAIL;
        return file.getPurpose() == wanted;
    }

    private Map<Long, FileEntity> filesOf(List<Long> ids) {
        Map<Long, FileEntity> map = new HashMap<>();
        fileQueries.byIds(ids).forEach(f -> map.put(f.getId(), f));
        return map;
    }

    /** 목록 썸네일 주소 — 썸네일 키 ?? 원본 키 */
    private String urlOf(FileEntity file) {
        return storage.publicUrl(file.getThumbnailKey() != null ? file.getThumbnailKey() : file.getStorageKey());
    }

    private static String youtubeOf(String content) {
        if (content == null) {
            return null;
        }
        Matcher m = YOUTUBE.matcher(content);
        return m.find() ? "https://i.ytimg.com/vi/" + m.group(1) + "/hqdefault.jpg" : null;
    }
}
