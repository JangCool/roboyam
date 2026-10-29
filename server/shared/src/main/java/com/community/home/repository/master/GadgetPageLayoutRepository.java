package com.community.home.repository.master;

import com.community.home.entity.GadgetPageLayoutEntity;
import com.community.home.entity.GadgetPageLayoutId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

/** 가젯 페이지의 테마별 배치({@code gadget_page_layouts}) — 키는 페이지 + 테마. 같은 페이지의 저장은 페이지 행 잠금으로 줄 세운다 */
public interface GadgetPageLayoutRepository extends RoboyamRepository<GadgetPageLayoutEntity, GadgetPageLayoutId> {

    Optional<GadgetPageLayoutEntity> findByPageIdAndTheme(Long pageId, String theme);
}
