package com.community.dashboard.service.gadget;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardBoardRankingView;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 오늘 활발한 게시판(post.read · 수집 항목 {@code boardActivity}) — 게시판 활동 일별 집계({@code board_activity_daily}) 오늘 줄을 글 + 댓글 많은 차례로 몇 줄 읽고, 게시판 사본에 있는
 * 게시판만 상위 {@value #TOP}. 소모임 게시판은 집계부터 빠진다(R__batch_38).
 */
@Component
public class BoardRankingGadget implements DashboardGadgetProvider {

    static final int TOP = 5;
    /** 사본에 없는(지워진) 게시판을 건너뛰어도 5개를 채우게 조금 더 읽는다 */
    private static final int READ = 20;

    private final DashboardAnalyticsStore store;
    private final BoardCatalog catalog;

    public BoardRankingGadget(DashboardAnalyticsStore store, BoardCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.BOARD_RANKING;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        BoardCatalogSnapshot boards = catalog.snapshot();
        List<DashboardBoardRankingView.Item> items = new ArrayList<>(TOP);
        for (DashboardAnalyticsStore.BoardDay row : store.boardDay(query.today(), READ)) {
            EffectiveBoard b = boards.board(row.boardId()).orElse(null);
            if (b == null || row.posts() + row.comments() == 0) {
                continue;
            }
            items.add(new DashboardBoardRankingView.Item(Long.toString(row.boardId()), b.name(), row.posts(), row.comments()));
            if (items.size() == TOP) {
                break;
            }
        }
        return new DashboardBoardRankingView(items);
    }
}
