package com.community.post.repository.master;

import com.community.post.dto.response.TagCountView;
import com.community.post.entity.QPostEntity;
import com.community.post.entity.QPostTagDailyEntity;
import com.community.post.entity.QPostTagEntity;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.community.zconfiguration.time.ServiceTime;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.LocalDate;
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
 *   <li>{@link #popular}: 최근 많이 달린 태그 — 요청마다 세지 않고 집계 표 {@code post_tag_daily}(V216)를 날 범위로 합한다(post-tags-contract 「집계」).
 *       표는 DB 배치({@code R__batch_44_post_tag_daily.sql} — 10분마다 어제·오늘 + 지난 계산 뒤로 바뀐 글이 쓰인 날(최근 31일 안))가 보이는 글(지움·임시저장·예약 삭제 지남 아님) · 신고·운영 숨김 아님 ·
 *       섞인 목록에 나올 수 있는 글({@link PostRepositoryQDSL#alive}·{@link PostRepositoryQDSL#notHidden}·{@link PostRepositoryQDSL#feedVisible} 과 같은 조건을 SQL 로)만 세어 채운다
 *       — 그 판정을 고치면 배치 SQL 도 같이 고친다.</li>
 *   <li>{@link #suggestions}: 글쓰기 태그 추천 — 한 게시판에서 쓰인 태그 전체(집계 표의 모든 날 합, 많이 쓴 순).</li>
 *   <li>{@link #canonical}: 띄어쓰기만 다른 기존 태그 찾기 — 저장 때 그 게시판에서 이미 쓰는 모양으로 맞춘다({@link com.community.post.service.PostTagStore#replace}).</li>
 * </ul>
 */
@Repository
public class PostTagQueries extends IrobitRepositoryQDSL {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QPostTagDailyEntity DAILY = QPostTagDailyEntity.postTagDailyEntity;

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
     * 최근 많이 달린 태그 — 집계 표({@code post_tag_daily}, V216 — DB 배치가 미리 센다)에서 오늘(한국 날짜)을 포함한 최근 {@code days} 일의 글 수를 합해
     * 내림차순(같으면 열쇠 차례), 최대 {@code limit} 개. 보이는 이름은 그 열쇠의 모양 중 기간 안 글 수 합이 가장 큰 것(같으면 글자 차례).
     * 셀 글의 조건(보이는 글 · 숨김 아님 · 섞인 목록에 나올 수 있는 글)은 배치 SQL 이 가진다 — 최대 10분 늦을 수 있다.
     *
     * @param boardIds 게시판 범위 — null 이면 전체, 빈 목록이면 빈 결과
     * @param days     오늘 포함 날 수(1 이면 오늘만)
     * @param now      「오늘」의 기준 시각
     */
    public List<TagCountView> popular(Collection<Long> boardIds, int days, int limit, Instant now) {
        if (limit <= 0 || days <= 0 || boardIds != null && boardIds.isEmpty()) {
            return List.of();
        }
        LocalDate from = ServiceTime.dateOf(now).minusDays(days - 1L);
        BooleanExpression where = DAILY.day.goe(from);
        if (boardIds != null) {
            where = where.and(DAILY.boardId.in(boardIds));
        }
        return ranked(where, limit);
    }

    /**
     * 글쓰기 태그 추천 — 게시판 하나에서 쓰인 태그(집계 표의 모든 날 합 — 31일보다 앞선 날은 마지막으로 센 값) 많이 쓴 순, 최대 {@code limit} 개.
     * 화면이 한 번 받아 치는 동안 걸러 보인다(post-tags-contract 「글쓰기 추천」).
     */
    public List<TagCountView> suggestions(long boardId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return ranked(DAILY.boardId.eq(boardId), limit);
    }

    /**
     * 띄어쓰기를 뺀 열쇠({@code PostTags#compact}) → 그 게시판에서 이미 쓰는 모양(집계 표 — 띄어쓰기를 뺀 열쇠가 같은 모양 중 글 수 합이 가장 큰 것, 같으면 글자 차례).
     * 없는 열쇠는 맵에 없다. 집계는 최대 10분 늦다.
     */
    public Map<String, String> canonical(long boardId, Collection<String> compactKeys) {
        if (compactKeys.isEmpty()) {
            return Map.of();
        }
        StringExpression compact = Expressions.stringTemplate("replace({0}, ' ', '')", DAILY.tagKey);
        NumberExpression<Long> total = DAILY.postCount.longValue().sum();
        List<Tuple> found = query.select(compact, DAILY.tag, total).from(DAILY).where(DAILY.boardId.eq(boardId), compact.in(compactKeys))
                .groupBy(compact, DAILY.tag).fetch();
        Map<String, String> names = new HashMap<>();
        Map<String, Long> best = new HashMap<>();
        for (Tuple t : found) {
            String key = t.get(compact);
            String tag = t.get(DAILY.tag);
            Long n0 = t.get(total);
            long n = n0 == null ? 0 : n0;
            Long seen = best.get(key);
            if (seen == null || n > seen || n == seen && tag.compareTo(names.get(key)) < 0) {
                best.put(key, n);
                names.put(key, tag);
            }
        }
        return names;
    }

    /** 조건 안 열쇠마다 글 수 합 내림차순(같으면 열쇠 차례) — 이름은 그 열쇠의 모양 중 합이 가장 큰 것 */
    private List<TagCountView> ranked(BooleanExpression where, int limit) {
        NumberExpression<Long> total = DAILY.postCount.longValue().sum();
        List<Tuple> top = query.select(DAILY.tagKey, total).from(DAILY).where(where)
                .groupBy(DAILY.tagKey).orderBy(total.desc(), DAILY.tagKey.asc()).limit(limit).fetch();
        if (top.isEmpty()) {
            return List.of();
        }
        List<String> keys = top.stream().map(t -> t.get(DAILY.tagKey)).toList();
        // 열쇠마다 기간 안 글 수 합이 가장 큰 모양(같으면 글자 차례)
        Map<String, String> names = new HashMap<>();
        Map<String, Long> best = new HashMap<>();
        List<Tuple> spellings = query.select(DAILY.tagKey, DAILY.tag, total).from(DAILY).where(where.and(DAILY.tagKey.in(keys)))
                .groupBy(DAILY.tagKey, DAILY.tag).fetch();
        for (Tuple t : spellings) {
            String key = t.get(DAILY.tagKey);
            String tag = t.get(DAILY.tag);
            Long found = t.get(total);
            long n = found == null ? 0 : found;
            Long seen = best.get(key);
            if (seen == null || n > seen || n == seen && tag.compareTo(names.get(key)) < 0) {
                best.put(key, n);
                names.put(key, tag);
            }
        }
        List<TagCountView> out = new ArrayList<>(top.size());
        for (Tuple t : top) {
            String key = t.get(DAILY.tagKey);
            Long n = t.get(total);
            out.add(new TagCountView(names.getOrDefault(key, key), n == null ? 0 : n));
        }
        return out;
    }
}
