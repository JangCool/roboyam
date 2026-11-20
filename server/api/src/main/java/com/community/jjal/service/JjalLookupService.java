package com.community.jjal.service;

import com.community.jjal.dto.response.JjalLookupView;
import com.community.jjal.dto.response.JjalView;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.jjal.repository.master.JjalRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 글·댓글 속 짤의 표시 정보 {@code GET /api/jjals?ids=a,b&postId=} — 비회원도 부른다(글·댓글을 그리는 곳). 모르는 ID·모양이 틀린 ID 는 빠진다(화면은 토큰 글자 그대로).
 * 한 번에 {@value #IDS_MAX}개까지({@code 400 fields.ids}). 내려간 짤은 그림 주소를 내지 않는다({@code url null}, {@code blocked true}).
 *
 * <p><b>{@code collectable}</b>(my-jjal-contract 「화면 확정 결과」 — 서버가 정한다): 내려간 짤은 false. 맥락 글 {@code postId} 를 주면 그 글이 보는 사람에게 보이고(읽기 판정은 글 보기와 같다)
 * 19금 게시판·19금 글이 아니고 그 짤이 그 글(본문·댓글)에 쓰였을 때만 true — 담기({@code POST /api/me/jjals/collect})가 같은 판정을 한 번 더 한다. {@code postId} 를 주지 않으면
 * 내려가지 않은 짤은 true(맥락을 모른다 — 담기가 판정한다).
 */
@Service
public class JjalLookupService {

    static final int IDS_MAX = 100;

    private final JjalFileRepository files;
    private final JjalRepositoryQDSL queries;
    private final JjalPostContext context;
    private final ImageStorage storage;

    public JjalLookupService(JjalFileRepository files, JjalRepositoryQDSL queries, JjalPostContext context, ImageStorage storage) {
        this.files = files;
        this.queries = queries;
        this.context = context;
        this.storage = storage;
    }

    public JjalLookupView lookup(String idsRaw, String postIdRaw, Long viewerId) {
        Set<String> wanted = new LinkedHashSet<>();
        if (idsRaw != null) {
            for (String part : idsRaw.split(",")) {
                String id = part.strip();
                if (JjalGuard.validPublicId(id)) {
                    wanted.add(id);
                }
            }
        }
        if (wanted.size() > IDS_MAX) {
            throw ValidationException.of("ids", new MessageRef("jjal.ids.tooMany", IDS_MAX));
        }
        Long postId = null;
        if (postIdRaw != null && !postIdRaw.isBlank()) {
            postId = PostQueryService.parseId(postIdRaw);
            if (postId == null) {
                throw ValidationException.of("postId", new MessageRef("jjal.postId.invalid"));
            }
        }
        if (wanted.isEmpty()) {
            return new JjalLookupView(List.of());
        }
        Instant now = Instant.now();
        Map<String, JjalFileEntity> byId = files.findAllByPublicIdIn(wanted).stream()
                .collect(Collectors.toMap(JjalFileEntity::getPublicId, Function.identity(), (a, b) -> a));
        Set<Long> usedHere = null;
        boolean contextOpen = true;
        if (postId != null) {
            JjalPostContext.Visible visible = context.post(postId, viewerId, now);
            contextOpen = visible != null && !visible.adult();
            usedHere = contextOpen ? queries.usedInPost(byId.values().stream().map(JjalFileEntity::getId).toList(), postId) : Set.of();
        }
        List<JjalView> out = new ArrayList<>(byId.size());
        for (String id : wanted) {
            JjalFileEntity f = byId.get(id);
            if (f == null) {
                continue;
            }
            boolean blocked = !f.servableAt(now); // 내려갔거나 공개 위치로 옮기기 전
            boolean collectable = !blocked && contextOpen && (usedHere == null || usedHere.contains(f.getId()));
            out.add(new JjalView(f.getPublicId(), blocked ? null : storage.publicUrl(f.getStorageKey()), f.getWidth(), f.getHeight(), f.isAnimated(), blocked,
                    collectable));
        }
        return new JjalLookupView(out);
    }
}
