package com.community.post.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.AuthorMembers;
import com.community.board.service.AuthorViews;
import com.community.debate.dto.DebateVotesView;
import com.community.debate.entity.PostDebateEntity;
import com.community.debate.repository.master.DebateQueries;
import com.community.debate.repository.master.PostDebateRepository;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupRepository;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.service.DealReader;
import com.community.hotdeal.service.DealWriter;
import com.community.poll.repository.master.PollQueries;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostFeedDebateView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostGroupView;
import com.community.post.dto.response.PostRowView;
import com.community.post.entity.PostProcessStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 게시판이 섞인 목록의 줄({@link PostFeedRowView}) 만들기 — 줄 모양은 게시판 글 목록({@code PostQueryService#row})과 같고 그 글의 게시판을 더한다.
 * 작성자 프로필은 목록마다 한 번에 읽는다. 게시판이 사본에 없거나 꺼진 글은 뺀다(사본은 15초 — 게시판을 막 끈 경우).
 * 보는 사람의 차단·메모({@code blocked}·작성자 {@code memo})도 게시판 목록과 같게 싣는다 — 쪽마다 {@link ViewerBlocks} 질의 세 번(줄 수와 무관). 차단된 줄도 빼지 않는다(화면이 접는다).
 * 투표가 붙은 글({@code hasPoll})과 찬반 토론 요약({@code debate} — {@link #debates}, 질의 두 번)도 쪽마다 한 번에 읽는다. 소모임 글(비멤버에게 보이는 것만 이 목록들에 온다)의 소모임 표시({@code group})는 쪽마다 소모임 질의 한 번(소모임 6단계).
 */
@Component
public class PostFeedRows {

    private final AuthorViews authors;
    private final ViewerBlocks viewerBlocks;
    private final PollQueries polls;
    private final GroupRepository groups;
    private final DealReader deals;
    private final PostThumbnails thumbnails;

    private final PostDebateRepository debateRows;
    private final DebateQueries debateQueries;

    public PostFeedRows(AuthorViews authors, ViewerBlocks viewerBlocks, PollQueries polls, GroupRepository groups, DealReader deals, PostThumbnails thumbnails,
                        PostDebateRepository debateRows, DebateQueries debateQueries) {
        this.debateRows = debateRows;
        this.debateQueries = debateQueries;
        this.thumbnails = thumbnails;
        this.deals = deals;
        this.groups = groups;
        this.authors = authors;
        this.viewerBlocks = viewerBlocks;
        this.polls = polls;
    }

    /**
     * 주어진 순서대로 줄을 만든다. 게시판이 없거나 꺼진 글은 빠진다.
     *
     * @param viewerId 로그인한 회원(비로그인 null — {@code blocked}·{@code memo} 모두 null)
     */
    public List<PostFeedRowView> of(List<PostRowData> rows, BoardCatalogSnapshot s, Instant now, Long viewerId) {
        List<Long> authorIds = rows.stream().map(PostRowData::authorId).toList();
        AuthorMembers members = authors.members(authorIds);
        ViewerBlocks.Lens lens = rows.isEmpty() ? ViewerBlocks.Lens.none() : viewerBlocks.lens(viewerId, authorIds);
        Set<Long> withPoll = polls.postIdsWithPoll(rows.stream().map(PostRowData::id).toList());
        Map<Long, String> thumbs = thumbnails.of(rows.stream().filter(r -> !r.hiddenAt(now)).map(PostRowData::id).toList());
        Map<Long, PostGroupView> groupRefs = groupRefs(rows);
        Map<Long, PostDealView> dealRows = dealRows(rows, s, now, viewerId);
        Map<Long, PostFeedDebateView> debates = debates(rows.stream().filter(r -> !r.hiddenAt(now)).map(PostRowData::id).toList());
        List<PostFeedRowView> out = new ArrayList<>(rows.size());
        for (PostRowData r : rows) {
            EffectiveBoard b = s.board(r.boardId()).filter(EffectiveBoard::active).orElse(null);
            if (b == null) {
                continue;
            }
            out.add(PostFeedRowView.of(row(r, b, s, members, now, lens, withPoll, dealRows.get(r.id()), thumbs, false),
                    new PostBoardRefView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.color()), r.groupId() == null ? null : groupRefs.get(r.groupId()),
                    debates.get(r.id())));
        }
        return out;
    }

    /**
     * 글들의 찬반 토론 요약(feed-debate-counts-contract) — 토론이 열린 글({@code post_debates} 질의 한 번)과 입장 수({@code post_stances} 묶어 세기 한 번 —
     * 글 상세 {@code debate.votes} 와 같은 {@link DebateQueries#votesOf}). 토론이 열렸거나 표가 있는 글만 담는다(그 밖은 없음 = 줄의 칸 null). 숨긴 글은 부르는 쪽이 뺀다.
     * 열린 토론이 중립을 받지 않으면 중립 null, 닫힌 토론(표만 남음)은 글 상세처럼 중립을 받는 것으로 본다.
     */
    public Map<Long, PostFeedDebateView> debates(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = postIds.stream().distinct().toList();
        Map<Long, Boolean> neutralById = new HashMap<>();
        for (PostDebateEntity d : debateRows.findAllById(ids)) {
            neutralById.put(d.getPostId(), d.isNeutralEnabled());
        }
        Map<Long, DebateVotesView> votes = debateQueries.votesOf(ids);
        Map<Long, PostFeedDebateView> out = new HashMap<>();
        for (Long id : ids) {
            DebateVotesView v = votes.getOrDefault(id, DebateVotesView.EMPTY);
            Boolean neutral = neutralById.get(id);
            if (neutral != null || v.total() > 0) {
                out.put(id, PostFeedDebateView.of(v, neutral == null || neutral));
            }
        }
        return out;
    }

    /**
     * 공용 줄(보는 사람 없이 만든 줄 — 캐시에 담긴 값)에 덧입힐 보는 사람 몫을 한 번에 읽는다 — 작성자 메모·차단({@link ViewerBlocks} 질의 세 번, 게시판 목록과 같은 판정)과
     * 딜의 내 종료 요청(딜 줄이 있을 때만 한 번). 비로그인이면 아무것도 읽지 않는다.
     * 섞인 목록·가젯의 줄은 읽는 질의가 신고로 숨긴 글을 미리 거르므로({@code notHidden} — {@link #of} 의 숨긴 줄 판정과 같은 시각 조건) 덧입힘 결과가 {@link #of} 에 보는 사람을 넘긴 것과 같다.
     */
    public Overlay overlay(Collection<PostFeedRowView> rows, Long viewerId) {
        if (viewerId == null || rows.isEmpty()) {
            return Overlay.NONE;
        }
        List<Long> authorIds = rows.stream().map(PostFeedRows::authorIdOf).filter(Objects::nonNull).distinct().toList();
        ViewerBlocks.Lens lens = viewerBlocks.lens(viewerId, authorIds);
        List<Long> dealIds = rows.stream().filter(r -> r.deal() != null).map(r -> Long.parseLong(r.id())).distinct().toList();
        Map<Long, String> mine = dealIds.isEmpty() ? Map.of() : deals.myEndRequests(dealIds, viewerId);
        return new Overlay(lens, mine);
    }

    /** 공용 줄 목록에 보는 사람 몫을 덧입힌다({@link #overlay}) */
    public List<PostFeedRowView> forViewer(List<PostFeedRowView> rows, Long viewerId) {
        return overlay(rows, viewerId).apply(rows);
    }

    /** 줄의 작성 회원 — 회원 글이면 {@code author.memberId}(= 작성자 ID, {@code AuthorViews#of}), 손님 글이면 null */
    static Long authorIdOf(PostFeedRowView r) {
        return r.author() == null || r.author().memberId() == null ? null : Long.parseLong(r.author().memberId());
    }

    /** 보는 사람 몫 — 비로그인이면 {@link #NONE}(그대로 돌려준다) */
    public record Overlay(ViewerBlocks.Lens lens, Map<Long, String> mine) {

        public static final Overlay NONE = new Overlay(null, Map.of());

        public PostFeedRowView apply(PostFeedRowView r) {
            if (lens == null || r == null) {
                return r;
            }
            Long authorId = authorIdOf(r);
            PostDealView deal = r.deal() == null ? null : r.deal().withMine(mine.get(Long.parseLong(r.id())));
            return r.forViewer(lens.memoOf(authorId), lens.blockedOf(authorId, r.title()), deal);
        }

        public List<PostFeedRowView> apply(List<PostFeedRowView> rows) {
            return lens == null ? rows : rows.stream().map(this::apply).toList();
        }
    }

    /**
     * 핫딜 게시판 글의 딜(hotdeal-contract — 섞인 목록·가젯도 핫딜 목록 줄과 같은 모양, 서버 2단계) — 게시판마다 한 번에 읽는다({@link DealReader#forRows}, 숨긴 줄은 뺀다).
     * 핫딜 게시판 글이 없으면 읽지 않는다.
     */
    private Map<Long, PostDealView> dealRows(List<PostRowData> rows, BoardCatalogSnapshot s, Instant now, Long viewerId) {
        Map<Long, List<PostRowData>> byBoard = new java.util.LinkedHashMap<>();
        for (PostRowData r : rows) {
            EffectiveBoard b = s.board(r.boardId()).orElse(null);
            if (b != null && DealWriter.dealBoard(b) && !r.hiddenAt(now)) {
                byBoard.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(r);
            }
        }
        Map<Long, PostDealView> out = new HashMap<>();
        byBoard.forEach((boardId, list) -> out.putAll(deals.forRows(list, s.board(boardId).orElseThrow(), viewerId, now)));
        return out;
    }

    /**
     * 줄들의 소모임 표시 — 소모임 ID → {@code { id, name, visibility, dissolved }}(질의 한 번, 소모임 글이 없으면 읽지 않는다). 섞인 목록에는 비멤버에게 보이는 글만 오므로
     * {@code visibility} 는 PUBLIC 이다(읽는 질의가 {@code PostRepositoryQDSL#feedVisible} 로 거른다). 해산한 소모임은 {@code dissolved = true}.
     */
    public Map<Long, PostGroupView> groupRefs(Collection<PostRowData> rows) {
        List<Long> ids = rows.stream().map(PostRowData::groupId).filter(Objects::nonNull).distinct().toList();
        Map<Long, PostGroupView> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (GroupEntity g : groups.findAllById(ids)) {
            out.put(g.getId(), PostGroupView.ref(Long.toString(g.getId()), g.getName(), true, g.getStatus() == GroupStatus.DELETED));
        }
        return out;
    }

    /**
     * 한 게시판의 줄(게시판 칸 없이 — 소모임 글 탭). 주어진 순서대로. 작성자·차단·투표 칩은 {@link #of} 와 같은 방식으로 한 번에 읽는다.
     *
     * @param viewerId 로그인한 회원(비로그인 null)
     */
    public List<PostRowView> boardRows(List<PostRowData> rows, EffectiveBoard b, BoardCatalogSnapshot s, Instant now, Long viewerId) {
        return boardRows(rows, b, s, now, viewerId, Set.of());
    }

    /**
     * {@link #boardRows(List, EffectiveBoard, BoardCatalogSnapshot, Instant, Long)} — {@code sealed} 는 보는 사람이 읽을 수 없는 비밀글(V168 소모임 게시판):
     * 줄·제목은 남기고 썸네일·이미지·투표 표시를 비운다(게시판 글 목록과 같다).
     */
    public List<PostRowView> boardRows(List<PostRowData> rows, EffectiveBoard b, BoardCatalogSnapshot s, Instant now, Long viewerId, Set<Long> sealed) {
        List<Long> authorIds = rows.stream().map(PostRowData::authorId).toList();
        AuthorMembers members = authors.members(authorIds);
        ViewerBlocks.Lens lens = rows.isEmpty() ? ViewerBlocks.Lens.none() : viewerBlocks.lens(viewerId, authorIds);
        Set<Long> withPoll = polls.postIdsWithPoll(rows.stream().map(PostRowData::id).toList());
        Map<Long, String> thumbs = thumbnails.of(rows.stream().filter(r -> !r.hiddenAt(now) && !sealed.contains(r.id())).map(PostRowData::id).toList());
        return rows.stream().map(r -> row(r, b, s, members, now, lens, sealed.contains(r.id()) ? Set.of() : withPoll, null, thumbs, sealed.contains(r.id()))).toList();
    }

    /**
     * {@code PostQueryService#row} 와 같은 규칙 — 신고로 숨긴 글은 자리만(이 목록들은 숨긴 글을 미리 거르므로 보통 오지 않는다). 섞인 목록 질의는 비밀글을 읽지 않는다
     * ({@code PostRepositoryQDSL#feedVisible}) — 비밀글 줄은 회원 작성글을 본인이 볼 때만 오므로 내용 칸을 그대로 싣는다.
     */
    private PostRowView row(PostRowData r, EffectiveBoard b, BoardCatalogSnapshot s, AuthorMembers members, Instant now, ViewerBlocks.Lens lens,
                            Set<Long> withPoll, PostDealView deal, Map<Long, String> thumbs, boolean sealed) {
        boolean hidden = r.hiddenAt(now);
        boolean bare = hidden || sealed;
        PostAuthorView author = authors.of(r.authorId(), r.guestName(), r.authorIp(), members, s).withMemo(lens.memoOf(r.authorId()));
        return new PostRowView(Long.toString(r.id()), hidden ? null : PostQueryService.prefixRef(b, r.prefixId()), hidden ? "" : r.title(),
                hidden ? 0 : r.commentCount(), !bare && Boolean.TRUE.equals(r.hasImage()), bare ? null : thumbs.get(r.id()), hidden ? AuthorViews.blank(author) : author, r.createdAt(),
                r.viewCount(), r.likeCount(), r.pinnedAt() != null, !hidden && Boolean.TRUE.equals(r.adult()), r.publicHiddenReason(now, b.staffHiddenNotice()),
                hidden ? null : lens.blockedOf(r.authorId(), r.title()), !bare && withPoll.contains(r.id()), bare ? null : deal, r.secretPost(),
                hidden ? null : PostProcessStatus.effective(b, r.processStatus()));
    }
}
