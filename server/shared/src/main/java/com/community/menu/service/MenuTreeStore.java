package com.community.menu.service;

import com.community.menu.dto.MenuNode;
import com.community.menu.entity.MenuNodeEntity;
import com.community.menu.repository.master.MenuNodeRepository;
import com.community.menu.repository.master.MenuNodeRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 트리 읽기·전체 교체. 부르는 쪽의 트랜잭션 안에서 돈다.
 *
 * <p>읽은 노드 엔티티는 영속성 컨텍스트에서 떼어 둔다 — 같은 트랜잭션에서 그룹 노드를 지우고(일괄 DELETE) 같은 ID 로 다시 넣을 때 낡은 엔티티와 부딪치지 않게.
 */
@Component
public class MenuTreeStore {

    private final MenuNodeRepository nodes;
    private final MenuNodeRepositoryQDSL nodeQueries;
    private final SnowflakeIdGenerator ids;
    private final EntityManager entityManager;

    public MenuTreeStore(MenuNodeRepository nodes, MenuNodeRepositoryQDSL nodeQueries, SnowflakeIdGenerator ids, EntityManager entityManager) {
        this.nodes = nodes;
        this.nodeQueries = nodeQueries;
        this.ids = ids;
        this.entityManager = entityManager;
    }

    /** 그룹의 트리(차례대로). 부모가 없어진 노드는 빠진다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MenuNode> load(String setKey) {
        List<MenuNodeEntity> rows = nodes.findBySetKeyOrderBySortOrderAscIdAsc(setKey);
        rows.forEach(entityManager::detach);
        Map<Long, List<MenuNodeEntity>> byParent = new LinkedHashMap<>();
        for (MenuNodeEntity row : rows) {
            byParent.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row);
        }
        return build(byParent, null);
    }

    /**
     * 그룹의 트리를 통째로 바꾼다 — 지금 노드를 모두 지우고 {@code tree} 를 차례대로 넣는다. 지금 그룹에 있던 ID 는 그대로 쓰고(한 번만),
     * 없던 ID·null 은 새로 발급한다(다른 그룹의 노드 ID 를 가져올 수 없다).
     *
     * @param existingIds 지금 그룹에 있는 노드 ID
     * @return ID 를 매긴 트리
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MenuNode> replace(String setKey, List<MenuNode> tree, Set<Long> existingIds) {
        nodeQueries.deleteBySet(setKey);
        return insert(setKey, null, tree, existingIds, new HashSet<>());
    }

    private List<MenuNode> insert(String setKey, Long parentId, List<MenuNode> list, Set<Long> existingIds, Set<Long> used) {
        List<MenuNode> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            MenuNode node = list.get(i);
            long id = node.id() != null && existingIds.contains(node.id()) && used.add(node.id()) ? node.id() : ids.nextId();
            nodes.persist(MenuNodeEntity.of(id, setKey, parentId, i, node));
            List<MenuNode> children = insert(setKey, id, node.children(), existingIds, used);
            out.add(node.withId(id).withChildren(children));
        }
        return out;
    }

    private static List<MenuNode> build(Map<Long, List<MenuNodeEntity>> byParent, Long parentId) {
        List<MenuNode> out = new ArrayList<>();
        for (MenuNodeEntity row : byParent.getOrDefault(parentId, List.of())) {
            MenuNode node = row.toNode();
            out.add(MenuNode.FOLDER.equals(row.getKind()) ? node.withChildren(build(byParent, row.getId())) : node);
        }
        return out;
    }
}
