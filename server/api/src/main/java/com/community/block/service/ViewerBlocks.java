package com.community.block.service;

import com.community.block.repository.master.BlockRepositoryQDSL;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 보는 사람의 차단·메모를 이미 있는 응답(게시판 글 목록 줄·댓글)에 싣는 도우미 — 목록 하나에 <b>질의 세 번</b>(차단한 작성자 · 차단 단어 · 메모)으로 끝낸다(N+1 없음).
 * docs/features/member-social-contract.md 「이미 있는 응답에 더할 칸」.
 *
 * <ul>
 *   <li>{@code blocked}: 작성 회원을 내가 차단했으면 {@code USER}, 아니면 본문(글은 제목·댓글은 본문)에 차단 단어가 들어 있으면(대소문자 무시·부분 일치) {@code WORD}, 아니면 null.
 *       <b>내 글·내 댓글은 늘 null</b>, 비로그인은 늘 null. 접는 것은 화면의 일이다.</li>
 *   <li>{@code memo}: 내가 그 작성 회원에게 남긴 메모, 없으면 null(비회원 작성자·비로그인 null).</li>
 * </ul>
 */
@Component
public class ViewerBlocks {

    public static final String USER = "USER";
    public static final String WORD = "WORD";

    private final BlockRepositoryQDSL queries;

    public ViewerBlocks(BlockRepositoryQDSL queries) {
        this.queries = queries;
    }

    /** 한 목록을 보는 동안의 차단·메모 사본 */
    public record Lens(Long viewerId, Set<Long> blocked, List<String> words, Map<Long, String> memos) {

        public static Lens none() {
            return new Lens(null, Set.of(), List.of(), Map.of());
        }

        /** @param text 단어를 찾을 글자(글 제목·댓글 본문 — 없으면 null) */
        public String blockedOf(Long authorId, String text) {
            if (viewerId == null || Objects.equals(viewerId, authorId)) {
                return null;
            }
            if (authorId != null && blocked.contains(authorId)) {
                return USER;
            }
            if (text != null && !words.isEmpty()) {
                String lower = text.toLowerCase(Locale.ROOT);
                for (String word : words) {
                    if (lower.contains(word)) {
                        return WORD;
                    }
                }
            }
            return null;
        }

        public String memoOf(Long authorId) {
            return viewerId == null || authorId == null ? null : memos.get(authorId);
        }
    }

    /**
     * @param viewerId  보는 회원(비로그인 null — 질의하지 않는다)
     * @param authorIds 이 목록의 작성 회원들
     */
    public Lens lens(Long viewerId, Collection<Long> authorIds) {
        if (viewerId == null) {
            return Lens.none();
        }
        List<Long> ids = authorIds.stream().filter(Objects::nonNull).filter(id -> !id.equals(viewerId)).distinct().toList();
        List<String> words = queries.words(viewerId).stream().map(w -> w.toLowerCase(Locale.ROOT)).toList();
        return new Lens(viewerId, queries.blockedAmong(viewerId, ids), words, queries.memosAmong(viewerId, ids));
    }
}
