package com.community.post.service;

import com.community.block.service.ViewerBlocks;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.AuthorViews;
import com.community.post.dto.PostRowData;
import com.community.post.dto.response.PostAuthorView;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostRowView;
import com.community.user.entity.ProfileEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 게시판이 섞인 목록의 줄({@link PostFeedRowView}) 만들기 — 줄 모양은 게시판 글 목록({@code PostQueryService#row})과 같고 그 글의 게시판을 더한다.
 * 작성자 프로필은 목록마다 한 번에 읽는다. 게시판이 사본에 없거나 꺼진 글은 뺀다(사본은 15초 — 게시판을 막 끈 경우).
 * 보는 사람의 차단·메모({@code blocked}·작성자 {@code memo})도 게시판 목록과 같게 싣는다 — 쪽마다 {@link ViewerBlocks} 질의 세 번(줄 수와 무관). 차단된 줄도 빼지 않는다(화면이 접는다).
 */
@Component
public class PostFeedRows {

    private final AuthorViews authors;
    private final ViewerBlocks viewerBlocks;

    public PostFeedRows(AuthorViews authors, ViewerBlocks viewerBlocks) {
        this.authors = authors;
        this.viewerBlocks = viewerBlocks;
    }

    /**
     * 주어진 순서대로 줄을 만든다. 게시판이 없거나 꺼진 글은 빠진다.
     *
     * @param viewerId 로그인한 회원(비로그인 null — {@code blocked}·{@code memo} 모두 null)
     */
    public List<PostFeedRowView> of(List<PostRowData> rows, BoardCatalogSnapshot s, Instant now, Long viewerId) {
        List<Long> authorIds = rows.stream().map(PostRowData::authorId).toList();
        Map<Long, ProfileEntity> members = authors.members(authorIds);
        ViewerBlocks.Lens lens = rows.isEmpty() ? ViewerBlocks.Lens.none() : viewerBlocks.lens(viewerId, authorIds);
        List<PostFeedRowView> out = new ArrayList<>(rows.size());
        for (PostRowData r : rows) {
            EffectiveBoard b = s.board(r.boardId()).filter(EffectiveBoard::active).orElse(null);
            if (b == null) {
                continue;
            }
            out.add(PostFeedRowView.of(row(r, b, s, members, now, lens), new PostBoardRefView(Long.toString(b.id()), b.slug(), b.name(), b.nameEn(), b.color())));
        }
        return out;
    }

    /** {@code PostQueryService#row} 와 같은 규칙 — 신고로 숨긴 글은 자리만(이 목록들은 숨긴 글을 미리 거르므로 보통 오지 않는다) */
    private PostRowView row(PostRowData r, EffectiveBoard b, BoardCatalogSnapshot s, Map<Long, ProfileEntity> members, Instant now, ViewerBlocks.Lens lens) {
        boolean hidden = r.hiddenAt(now);
        PostAuthorView author = authors.of(r.authorId(), r.guestName(), r.authorIp(), members, s).withMemo(lens.memoOf(r.authorId()));
        return new PostRowView(Long.toString(r.id()), hidden ? null : PostQueryService.prefixRef(b, r.prefixId()), hidden ? "" : r.title(),
                hidden ? 0 : r.commentCount(), !hidden && Boolean.TRUE.equals(r.hasImage()), null, hidden ? AuthorViews.blank(author) : author, r.createdAt(),
                r.viewCount(), r.likeCount(), r.pinnedAt() != null, !hidden && Boolean.TRUE.equals(r.adult()), r.publicHiddenReason(now, b.staffHiddenNotice()),
                hidden ? null : lens.blockedOf(r.authorId(), r.title()));
    }
}
