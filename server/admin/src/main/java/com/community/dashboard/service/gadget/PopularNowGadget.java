package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardPopularNowView;
import com.community.popular.entity.PopularSettingEntity;
import com.community.popular.repository.master.PopularSettingRepository;
import com.community.popular.service.PopularPreview;
import org.springframework.stereotype.Component;

/**
 * 지금 인기글 — 배치가 만든 실시간 순위 스냅샷({@code post_rank_snapshots} REALTIME, 50줄 이하)을 인기글 설정 미리보기({@link PopularPreview#realtimeTop})와 같은 거름으로
 * (지금 볼 수 없는 글·운영자가 뺀 글 제외) 상위 limit 건. 원본을 다시 세지 않는다.
 */
@Component
public class PopularNowGadget implements DashboardGadgetProvider {

    private final PopularPreview preview;
    private final PopularSettingRepository settings;

    public PopularNowGadget(PopularPreview preview, PopularSettingRepository settings) {
        this.preview = preview;
        this.settings = settings;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.POPULAR_NOW;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        PopularSettingEntity setting = settings.findById(PopularSettingEntity.ROW_ID).orElse(null);
        boolean excludeStaff = setting == null || setting.isExcludeStaffBoards();
        return new DashboardPopularNowView(preview.realtimeTop(excludeStaff).items().stream().limit(query.limit())
                .map(it -> new DashboardPopularNowView.Item(it.postId(), it.boardName(), it.boardNameEn(), it.title(), it.likeCount())).toList());
    }
}
