package com.community.menu.repository.master;

import com.community.menu.entity.MenuNodeEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface MenuNodeRepository extends RoboyamRepository<MenuNodeEntity, Long> {

    /** 그룹의 모든 노드 — 부모 안의 차례대로(트리는 부르는 쪽이 짓는다) */
    List<MenuNodeEntity> findBySetKeyOrderBySortOrderAscIdAsc(String setKey);

    /** 그룹의 자동 추가 폴더(하나뿐이다) */
    Optional<MenuNodeEntity> findFirstBySetKeyAndKindAndAutoAddTrue(String setKey, String kind);

    long countBySetKey(String setKey);

    /** 이 폴더의 마지막 하위 노드 */
    Optional<MenuNodeEntity> findTopByParentIdOrderBySortOrderDesc(Long parentId);
}
