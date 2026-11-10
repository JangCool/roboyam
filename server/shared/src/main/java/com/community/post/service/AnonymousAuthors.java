package com.community.post.service;

import com.community.post.entity.PostAnonymousNumberEntity;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostAnonymousNumberRepository;
import com.community.post.repository.master.PostCounterQueries;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 익명 글·댓글의 작성자 표시(anonymous-board-contract 정책 3·5) — 글쓴이 표시({@code op})와 글 안 번호(익명1, 익명2 …).
 *
 * <ul>
 *   <li>번호는 그 글에 <b>처음 익명 댓글을 다는 회원</b> 차례로 매긴다({@link #assign}) — 댓글 저장 트랜잭션 안에서 글 단위 잠금({@code PostCounterQueries#lockWriter}
 *       {@code anon:<글ID>}, 작성 간격 잠금과 같은 도우미) 아래 최대값 + 1. 표의 UNIQUE(post_id, number)가 마지막으로 막는다. 같은 회원은 그 글에서 늘 같은 번호다(지우고 다시 달아도).</li>
 *   <li>글쓴이(익명 글의 작성 회원)는 번호를 받지 않는다 — {@code op = true, number = null}. 익명 글이 아니면(게시판이 나중에 익명이 됨) 글쓴이도 번호를 받는다
 *       (닉네임 글의 글쓴이 표시가 익명 댓글의 신원을 드러내지 않게).</li>
 *   <li>번호는 다른 글에서 새로 매긴다.</li>
 * </ul>
 */
@Component
public class AnonymousAuthors {

    private final PostAnonymousNumberRepository numbers;
    private final PostCounterQueries counters;

    public AnonymousAuthors(PostAnonymousNumberRepository numbers, PostCounterQueries counters) {
        this.numbers = numbers;
        this.counters = counters;
    }

    /** 익명 작성자 표시 — 응답 칸 {@code anonymous}·{@code viewerAnonymous}({@code number} 는 번호, 글쓴이·받기 전이면 null) */
    public record Tag(Integer number, boolean op) {

        /** 익명 글의 글쓴이 */
        public static final Tag OP = new Tag(null, true);
        /** 번호를 받기 전(댓글 입력 칸 「익명」) */
        public static final Tag NONE = new Tag(null, false);
    }

    /** 이 글에 새로 다는 댓글을 익명으로 쓰는가 — 글이 익명이거나 게시판이 지금 익명이면(정책 2 — 익명 글의 글쓴이가 닉네임 댓글로 드러나지 않게) */
    public static boolean anonymousComment(PostEntity post, boolean boardAnonymous) {
        return post.isAnonymous() || boardAnonymous;
    }

    /** 이 회원이 이 글의 (익명) 글쓴이인가 — 글이 익명일 때만 글쓴이 표시를 한다 */
    public static boolean op(PostEntity post, Long userId) {
        return post.isAnonymous() && userId != null && Objects.equals(post.getAuthorId(), userId);
    }

    /** 익명 댓글을 쓰는 회원의 표시 — 글쓴이면 {@link Tag#OP}, 아니면 이 글의 번호(없으면 지금 매긴다). <b>댓글 저장 트랜잭션 안에서</b> 부른다 */
    public Tag assign(PostEntity post, long userId) {
        if (op(post, userId)) {
            return Tag.OP;
        }
        counters.lockWriter("anon:" + post.getId()); // 글 단위 — 같은 글의 동시 첫 댓글이 같은 번호를 받지 않게(커밋까지)
        PostAnonymousNumberEntity mine = numbers.findByPostIdAndUserId(post.getId(), userId).orElse(null);
        if (mine != null) {
            return new Tag(mine.getNumber(), false);
        }
        int next = numbers.maxNumber(post.getId()) + 1;
        numbers.persist(PostAnonymousNumberEntity.of(post.getId(), userId, next));
        return new Tag(next, false);
    }

    /** 글 하나의 회원 → 번호(질의 한 번) */
    public Map<Long, Integer> numbersOf(long postId) {
        Map<Long, Integer> out = new HashMap<>();
        numbers.findByPostId(postId).forEach(n -> out.put(n.getUserId(), n.getNumber()));
        return out;
    }

    /** 여러 글의 (글 ID → 회원 → 번호) — 질의 한 번, 글이 없으면 읽지 않는다 */
    public Map<Long, Map<Long, Integer>> numbersOf(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, Integer>> out = new HashMap<>();
        for (PostAnonymousNumberEntity n : numbers.findByPostIdIn(List.copyOf(postIds))) {
            out.computeIfAbsent(n.getPostId(), k -> new HashMap<>()).put(n.getUserId(), n.getNumber());
        }
        return out;
    }

    /** 익명 댓글 작성자의 표시 — 글쓴이면 OP, 아니면 번호(번호 표에 없으면 null) */
    public static Tag commentTag(PostEntity post, Long authorId, Map<Long, Integer> numbersOfPost) {
        if (op(post, authorId)) {
            return Tag.OP;
        }
        return new Tag(authorId == null ? null : numbersOfPost.get(authorId), false);
    }

    /** 보는 사람의 이 글에서의 표시(댓글 목록 {@code viewerAnonymous}) — 글쓴이면 OP, 번호가 있으면 번호, 받기 전이면 {@link Tag#NONE} */
    public static Tag viewerTag(PostEntity post, long userId, Map<Long, Integer> numbersOfPost) {
        if (op(post, userId)) {
            return Tag.OP;
        }
        Integer n = numbersOfPost.get(userId);
        return n == null ? Tag.NONE : new Tag(n, false);
    }
}
