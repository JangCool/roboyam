package com.community.post.repository.master;

import com.community.post.dto.response.TagCountView;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.QPostTagEntity;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 글 태그 질의(V215 {@code post_tags} — docs/features/post-tags-contract.md).
 *
 * <ul>
 *   <li>{@link #hasTag}: 글 목록 거르기 조건(게시판 목록 {@code ?tag=} · 가젯 태그 탭 · 매거진 {@code ?tag=}) — 열쇠가 같은 태그 행이 있는 글({@code EXISTS}, 색인 (tag_key, post_id)).</li>
 *   <li>{@link #popular}: 최근 많이 달린 태그 — 보이는 글(지움·임시저장·예약 삭제 지남 아님) · 신고·운영 숨김 아님 · 섞인 목록에 나올 수 있는 글({@link PostRepositoryQDSL#feedVisible} —
 *       비밀글·비멤버에게 안 보이는 소모임 글 빼고)만 센다. 「최근」은 {@code created_at ≥ since} 이고 id 범위로 글 월 조각을 가지치기한다.</li>
 * </ul>
 */
@Repository
public class PostTagQueries extends IrobitRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QPostTagEntity TAG = QPostTagEntity.postTagEntity;

    public PostTagQueries(JPAQueryFactory query) {
        super(query);
    }

    /**
     * 글 {@code p} 에 열쇠 {@code tagKey} 인 태그가 달렸는가. {@code tagKey} 가 null 이면 null(거르지 않음 — QueryDSL 은 null 조건을 건너뛴다).
     */
    public static BooleanExpression hasTag(QPostEntity p, String tagKey) {
        if (tagKey == null) {
            return null;
        }
        QPostTagEntity t = new QPostTagEntity("tagFilter");
        return JPAExpressions.selectOne().from(t).where(t.postId.eq(p.id), t.tagKey.eq(tagKey)).exists();
    }

    /**
     * 최근 많이 달린 태그 — 달린 글 수 내림차순(같으면 열쇠 차례), 최대 {@code limit} 개. 보이는 이름은 그 태그(열쇠) 중 가장 많이 쓴 모양(같으면 글자 차례).
     *
     * @param boardIds 게시판 범위 — null 이면 전체, 빈 목록이면 빈 결과
     * @param since    이 시각 뒤에 쓴 글만
     */
    public List<TagCountView> popular(Collection<Long> boardIds, Instant since, int limit, Instant now) {
        if (limit <= 0 || boardIds != null && boardIds.isEmpty()) {
            return List.of();
        }
        long floor = SnowflakeIdGenerator.floorIdAt(since);
        BooleanExpression where = TAG.postId.goe(floor).and(POST.id.goe(floor)).and(POST.createdAt.goe(since))
                .and(PostRepositoryQDSL.alive(POST, now)).and(PostRepositoryQDSL.notHidden(POST, now)).and(PostRepositoryQDSL.feedVisible(POST));
        if (boardIds != null) {
            where = where.and(POST.boardId.in(boardIds));
        }
        NumberExpression<Long> count = TAG.postId.count();
        List<Tuple> top = query.select(TAG.tagKey, count).from(TAG).join(POST).on(POST.id.eq(TAG.postId)).where(where)
                .groupBy(TAG.tagKey).orderBy(count.desc(), TAG.tagKey.asc()).limit(limit).fetch();
        if (top.isEmpty()) {
            return List.of();
        }
        List<String> keys = top.stream().map(t -> t.get(TAG.tagKey)).toList();
        // 열쇠마다 가장 많이 쓴 모양(같으면 글자 차례)
        Map<String, String> names = new HashMap<>();
        Map<String, Long> best = new HashMap<>();
        List<Tuple> spellings = query.select(TAG.tagKey, TAG.tag, count).from(TAG).join(POST).on(POST.id.eq(TAG.postId)).where(where.and(TAG.tagKey.in(keys)))
                .groupBy(TAG.tagKey, TAG.tag).fetch();
        for (Tuple t : spellings) {
            String key = t.get(TAG.tagKey);
            String tag = t.get(TAG.tag);
            Long found = t.get(count);
            long n = found == null ? 0 : found;
            Long seen = best.get(key);
            if (seen == null || n > seen || n == seen && tag.compareTo(names.get(key)) < 0) {
                best.put(key, n);
                names.put(key, tag);
            }
        }
        List<TagCountView> out = new ArrayList<>(top.size());
        for (Tuple t : top) {
            String key = t.get(TAG.tagKey);
            Long n = t.get(count);
            out.add(new TagCountView(names.getOrDefault(key, key), n == null ? 0 : n));
        }
        return out;
    }
}
