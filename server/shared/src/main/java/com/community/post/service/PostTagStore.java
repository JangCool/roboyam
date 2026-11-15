package com.community.post.service;

import com.community.post.entity.PostEntity;
import com.community.post.entity.PostTagDirtyDayEntity;
import com.community.post.entity.PostTagEntity;
import com.community.post.repository.master.PostTagDirtyDayRepository;
import com.community.post.repository.master.PostTagRepository;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 글 태그 읽기·쓰기(V215 {@code post_tags} — docs/features/post-tags-contract.md). 글자 규칙은 {@link PostTags}(부르는 쪽이 정리·검사한 값을 넘긴다).
 * 쓰기는 글 저장 트랜잭션 안에서 부른다(글 행을 잠근 고치기 · 방금 넣은 쓰기). 태그가 실제로 바뀌면 그 글의 「작성일(한국 날짜) · 게시판」을
 * {@code post_tag_dirty_days}(V216)에 표시해 집계 배치(R__batch_44)가 그 날을 다시 세게 한다(post-tags-contract 「집계」 — 오래된 글에서 태그를 모두 뺀 경우도 잡힌다).
 */
@Component
public class PostTagStore {

    private final PostTagRepository tags;
    private final PostTagDirtyDayRepository dirtyDays;

    public PostTagStore(PostTagRepository tags, PostTagDirtyDayRepository dirtyDays) {
        this.tags = tags;
        this.dirtyDays = dirtyDays;
    }

    /**
     * 글의 태그를 {@code cleaned}(정리한 태그 — 적은 차례, 겹침 없음)로 맞춘다. 빈 목록이면 모두 지운다. 빠진 태그 행은 지우고, 남은 태그는 모양·차례만 고치고, 새 태그는 넣는다
     * (지울 열쇠와 넣을 열쇠가 겹치지 않아 쓰기 차례와 상관없이 키가 부딪치지 않는다). 넣기·빼기·표기 변경이 하나라도 있으면(차례만 바뀐 것은 아니다) 바뀐 날 표시를 하나 넣는다.
     */
    public void replace(PostEntity post, List<String> cleaned, Instant now) {
        long postId = post.getId();
        List<PostTagEntity> current = tags.findByPostIdOrderBySortOrderAsc(postId);
        Map<String, String> wanted = new LinkedHashMap<>();
        cleaned.forEach(t -> wanted.putIfAbsent(PostTags.key(t), t));
        List<PostTagEntity> removed = new ArrayList<>();
        Map<String, PostTagEntity> kept = new HashMap<>();
        for (PostTagEntity e : current) {
            if (wanted.containsKey(e.getTagKey())) {
                kept.put(e.getTagKey(), e);
            } else {
                removed.add(e);
            }
        }
        if (!removed.isEmpty()) {
            tags.deleteAll(removed);
        }
        boolean changed = !removed.isEmpty();
        int order = 0;
        for (Map.Entry<String, String> w : wanted.entrySet()) {
            PostTagEntity e = kept.get(w.getKey());
            if (e != null) {
                changed |= !e.getTag().equals(w.getValue());
                e.change(w.getValue(), order);
            } else {
                tags.persist(PostTagEntity.create(postId, w.getKey(), w.getValue(), order, now));
                changed = true;
            }
            order++;
        }
        if (changed) {
            dirtyDays.persist(PostTagDirtyDayEntity.create(ServiceTime.dateOf(post.getCreatedAt()), post.getBoardId(), now));
        }
    }

    /** 한 글의 태그(단 차례) — 없으면 빈 목록 */
    public List<String> of(long postId) {
        return tags.findByPostIdOrderBySortOrderAsc(postId).stream().map(PostTagEntity::getTag).toList();
    }

    /** 여러 글의 태그(글마다 단 차례) — 태그가 없는 글은 맵에 없다 */
    public Map<Long, List<String>> of(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<PostTagEntity> found = new ArrayList<>(tags.findByPostIdIn(new HashSet<>(postIds)));
        found.sort(Comparator.comparing(PostTagEntity::getPostId).thenComparingInt(PostTagEntity::getSortOrder));
        Map<Long, List<String>> out = new HashMap<>();
        for (PostTagEntity e : found) {
            out.computeIfAbsent(e.getPostId(), k -> new ArrayList<>()).add(e.getTag());
        }
        return out;
    }
}
