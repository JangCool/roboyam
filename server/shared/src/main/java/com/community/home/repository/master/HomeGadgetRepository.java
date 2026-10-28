package com.community.home.repository.master;

import com.community.home.entity.HomeGadgetEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

/** 홈 가젯({@code home_gadgets}) — 테마에 딸린다 */
public interface HomeGadgetRepository extends RoboyamRepository<HomeGadgetEntity, Long> {

    /** 그 테마의 가젯(만든 차례) */
    List<HomeGadgetEntity> findByThemeOrderByIdAsc(String theme);
}
