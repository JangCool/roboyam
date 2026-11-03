package com.community.dashboard.service.gadget;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardRecentPostsView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.post.dto.AdminPostRow;
import com.community.post.dto.response.AdminPostRefView;
import com.community.post.service.AdminPostSummaries;
import com.community.user.entity.ProfileEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 최근 글 — 글 ID(PK) 거꾸로 limit 건, 줄 모양은 관리자 화면의 글 한 줄({@link AdminPostSummaries} — 게시판 이름은 게시판 사본, 작성자는 한 번에)과 같다.
 * {@code hidden} = 지금 숨김 중, {@code reported} = 열린 신고 사건이 있다.
 */
@Component
public class RecentPostsGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;
    private final AdminPostSummaries summaries;
    private final BoardCatalog catalog;

    public RecentPostsGadget(DashboardGadgetStore store, AdminPostSummaries summaries, BoardCatalog catalog) {
        this.store = store;
        this.summaries = summaries;
        this.catalog = catalog;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.RECENT_POSTS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        List<Long> ids = store.recentPostIds(query.limit());
        Map<Long, AdminPostRow> rows = summaries.rows(ids);
        Map<Long, ProfileEntity> members = summaries.authors(rows.values());
        Set<Long> reported = store.openlyReportedPosts(ids);
        BoardCatalogSnapshot s = catalog.snapshot();
        List<DashboardRecentPostsView.Item> items = new ArrayList<>();
        for (Long id : ids) {
            AdminPostRow row = rows.get(id);
            if (row == null) {
                continue;
            }
            AdminPostRefView v = summaries.view(row, s, members, query.now());
            items.add(new DashboardRecentPostsView.Item(v.id(), v.boardName(), v.boardNameEn(), v.title(), v.authorName(), v.createdAt(),
                    AdminPostRow.HIDDEN.equals(v.state()), reported.contains(id)));
        }
        return new DashboardRecentPostsView(items);
    }
}
