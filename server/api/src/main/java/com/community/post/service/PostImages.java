package com.community.post.service;

import com.community.board.service.RichText;
import com.community.media.entity.FileEntity;
import com.community.media.entity.FileStatus;
import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * 글 본문의 이미지 표식({@code <img data-image-id="…">})을 확인하고 서버가 채운다 — community {@code PostMedia} 를 옮겼다.
 *
 * <ul>
 *   <li><b>쓸 수 있는 이미지</b>: 글쓴이가 올린 아직 쓰이지 않은(PENDING) 글 이미지, 또는 (고치기) 이 글이 이미 쓰던 이미지. 그 밖(없는 ID·남의 이미지·다른 글이 쓰는 이미지)은
 *       구별해 알리지 않고 {@code 400 VALIDATION fields.content}(「이미지를 찾을 수 없어요」) — 표식을 조용히 지우지 않는다.</li>
 *   <li>표식은 서버가 만든 태그로 바뀐다: {@code <img src="(저장소 주소)" data-image-id="ID" width height alt="" loading="lazy">} — 사용자 입력이 한 글자도 섞이지 않는다.</li>
 *   <li>저장 트랜잭션 안에서 이미지 행을 쓰기 잠금으로 다시 읽어 확인하고 글에 붙인다(IN_USE + 글 ID). 정리 배치도 같은 행을 잠그고 상태를 다시 보므로 붙이는 중인 이미지를 지우지 못한다.
 *       고치면서 본문에서 빠진 이미지는 PENDING 으로 돌아가 정리 배치가 지운다. 지운 글의 이미지도 정리 배치가 지운다({@code FileRepositoryQDSL#abandonedPostImages}).</li>
 * </ul>
 */
@Component
public class PostImages {

    private final FileRepositoryQDSL files;
    private final ImageStorage storage;

    public PostImages(FileRepositoryQDSL files, ImageStorage storage) {
        this.files = files;
        this.storage = storage;
    }

    /** 이 글이 지금 쓰는 이미지 ID(잠그지 않음) — 고치기에서 「새로 넣은 이미지」를 가른다 */
    public Set<Long> attachedTo(long postId) {
        return new LinkedHashSet<>(files.idsByPost(postId));
    }

    /** 저장 전 확인(잠그지 않음) — 쓸 수 없는 이미지가 하나라도 있으면 칸 오류 */
    public void check(Collection<Long> ids, Long authorId, Long postId) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.isEmpty()) {
            return;
        }
        Map<Long, FileEntity> found = files.postImages(distinct).stream().collect(Collectors.toMap(FileEntity::getId, f -> f));
        requireUsable(distinct, found, authorId, postId);
    }

    /**
     * 저장 트랜잭션 안에서 — 이미지 행을 잠가 다시 확인하고, 표식을 채운 본문을 돌려주며, 글에 붙인다. 고치기({@code editing})면 빠진 이미지를 떼어 낸다.
     *
     * @param safeHtml 정화한(금지어까지 가린) 본문 — 표식이 아직 채워지지 않은 것
     */
    public String bind(String safeHtml, List<Long> ids, Long authorId, long postId, boolean editing, Instant now) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        List<FileEntity> current = editing ? files.lockByPost(postId) : List.of();
        Map<Long, FileEntity> found = new HashMap<>();
        files.lockPostImages(distinct).forEach(f -> found.put(f.getId(), f));
        requireUsable(distinct, found, authorId, postId);
        String html = RichText.expandImages(safeHtml, id -> tag(found.get(id)));
        for (FileEntity file : found.values()) {
            file.attachToPost(postId, now);
        }
        for (FileEntity file : current) {
            if (!distinct.contains(file.getId())) {
                file.release();
            }
        }
        return html;
    }

    /** 글을 평문으로 고쳤다 — 쓰던 이미지를 모두 떼어 낸다(저장 트랜잭션 안) */
    public void releaseAll(long postId) {
        files.lockByPost(postId).forEach(FileEntity::release);
    }

    private static void requireUsable(Set<Long> ids, Map<Long, FileEntity> found, Long authorId, Long postId) {
        for (Long id : ids) {
            FileEntity file = found.get(id);
            if (file == null || !usable(file, authorId, postId)) {
                throw ValidationException.of("content", new MessageRef("board.content.image.missing"));
            }
        }
    }

    private static boolean usable(FileEntity file, Long authorId, Long postId) {
        if (postId != null && postId.equals(file.getPostId()) && file.getStatus() == FileStatus.IN_USE) {
            return true; // 이 글이 이미 쓰던 이미지
        }
        return file.getStatus() == FileStatus.PENDING && authorId != null && authorId.equals(file.getUploadedByUser());
    }

    /** 서버가 정하는 이미지 태그 — 주소는 저장소가, 크기는 장부가 준다 */
    private String tag(FileEntity file) {
        if (file == null) {
            return null;
        }
        return "<img src=\"" + HtmlUtils.htmlEscape(storage.publicUrl(file.getStorageKey())) + "\" data-image-id=\"" + file.getId() + "\" width=\""
                + file.getWidth() + "\" height=\"" + file.getHeight() + "\" alt=\"\" loading=\"lazy\">";
    }
}
