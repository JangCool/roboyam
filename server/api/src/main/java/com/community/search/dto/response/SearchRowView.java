package com.community.search.dto.response;

import com.community.post.dto.response.PostAuthorView;
import com.community.post.dto.response.PostBoardRefView;
import com.community.post.dto.response.PostFeedRowView;
import com.community.post.dto.response.PostPrefixRefView;
import java.time.Instant;

/**
 * 검색 결과 한 줄(user-web {@code SearchRow}) — 게시판이 섞인 목록 한 줄({@link PostFeedRowView})의 칸 + 본문 일치 조각.
 *
 * @param blocked 보는 사람 기준 차단({@code USER}·{@code WORD}) — 결과에서 빼지 않고 표시만 한다(화면이 접는다)
 * @param snippet 본문이 걸린 자리(첫 단어 기준) 앞뒤 글자 약 100자(잘린 쪽에 「…」, 공백은 한 칸으로). 제목·작성자로만 걸렸거나 범위가 title·author 면 null
 */
public record SearchRowView(String id, PostPrefixRefView prefix, String title, int commentCount, boolean hasImage, String thumbnailUrl, PostAuthorView author,
                            Instant createdAt, int viewCount, int likeCount, boolean pinned, boolean adult, String hiddenReason, String blocked,
                            PostBoardRefView board, String snippet) {

    public static SearchRowView of(PostFeedRowView row, String snippet) {
        return new SearchRowView(row.id(), row.prefix(), row.title(), row.commentCount(), row.hasImage(), row.thumbnailUrl(), row.author(), row.createdAt(),
                row.viewCount(), row.likeCount(), row.pinned(), row.adult(), row.hiddenReason(), row.blocked(), row.board(), snippet);
    }
}
