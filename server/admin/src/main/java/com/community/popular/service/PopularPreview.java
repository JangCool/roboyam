package com.community.popular.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.popular.dto.response.PopularPreviewItemView;
import com.community.popular.dto.response.PopularRankItemView;
import com.community.popular.entity.PopularExclusionEntity;
import com.community.popular.repository.master.PopularExclusionRepository;
import com.community.popular.repository.master.PopularRankQueries;
import com.community.post.dto.AdminPostRow;
import com.community.post.dto.response.AdminPostRefView;
import com.community.post.service.AdminPostSummaries;
import com.community.user.entity.ProfileEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 순위 스냅샷 → 관리자 화면의 줄 — 공개 인기글({@code PopularService})과 <b>같은 거름</b>: 지금 볼 수 없는 글(지움·숨김·19금)·인기글에 오를 수 없는 게시판
 * ({@link PopularRules#rankableBoard})의 글은 빠지고, 운영자가 뺀 글은 공개에서는 빠지지만 여기서는 {@code excluded} 로 자리에 남는다. 트랜잭션은 부르는 서비스가 연다.
 */
@Component
public class PopularPreview {

    /** 설정 화면 미리보기 수 */
    static final int PREVIEW_MAX = 10;

    private final PopularRankQueries ranks;
    private final PopularExclusionRepository exclusions;
    private final AdminPostSummaries summaries;
    private final BoardCatalog catalog;

    public PopularPreview(PopularRankQueries ranks, PopularExclusionRepository exclusions, AdminPostSummaries summaries, BoardCatalog catalog) {
        this.ranks = ranks;
        this.exclusions = exclusions;
        this.summaries = summaries;
        this.catalog = catalog;
    }

    /** 미리보기 결과 */
    public record Result(List<PopularPreviewItemView> items, Instant computedAt) {
    }

    /** 한 기간의 순위 */
    public record Ranking(List<PopularRankItemView> items, Instant computedAt) {
    }

    /** 실시간 순위 상위 10개(뺀 글은 건너뛰고 번호는 스냅샷 그대로) */
    public Result realtimeTop(boolean excludeStaffBoards) {
        Ranking ranking = ranking(PopularRules.REALTIME, excludeStaffBoards);
        List<PopularPreviewItemView> out = new ArrayList<>();
        for (PopularRankItemView item : ranking.items()) {
            if (item.excluded()) {
                continue;
            }
            AdminPostRefView p = item.post();
            out.add(new PopularPreviewItemView(item.snapshotRank(), p.id(), p.title(), p.boardName(), p.boardNameEn(), item.score(), p.likeCount(),
                    p.commentCount(), p.viewCount(), p.createdAt(), item.fill()));
            if (out.size() >= PREVIEW_MAX) {
                break;
            }
        }
        return new Result(out, ranking.computedAt());
    }

    /** 그 기간의 순위 — 공개와 같은 차례·같은 글, 계산 뒤에 뺀 글은 {@code excluded} 로 남는다 */
    public Ranking ranking(String period, boolean excludeStaffBoards) {
        List<PopularRankQueries.Entry> entries = ranks.snapshot(period);
        if (entries.isEmpty()) {
            return new Ranking(List.of(), null);
        }
        Instant now = Instant.now();
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Long> ids = entries.stream().map(PopularRankQueries.Entry::postId).toList();
        Map<Long, AdminPostRow> rows = summaries.rows(ids);
        Map<Long, ProfileEntity> members = summaries.authors(rows.values());
        Set<Long> excluded = exclusions.findAllById(ids).stream().map(PopularExclusionEntity::getPostId).collect(Collectors.toSet());
        List<PopularRankItemView> out = new ArrayList<>();
        int rank = 0;
        for (PopularRankQueries.Entry e : entries) {
            AdminPostRow row = rows.get(e.postId());
            if (row == null || !AdminPostRow.PUBLIC.equals(row.stateAt(now, true))
                    || !PopularRules.rankableBoard(row.boardId() == null ? null : s.board(row.boardId()).orElse(null), excludeStaffBoards)) {
                continue;
            }
            boolean isExcluded = excluded.contains(e.postId());
            out.add(new PopularRankItemView(isExcluded ? null : ++rank, e.rank(), e.score(), e.fill(), isExcluded, summaries.view(row, s, members, now)));
        }
        return new Ranking(out, entries.get(0).computedAt());
    }
}
