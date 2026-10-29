package com.community.menu.repository.master;

import com.community.menu.entity.QMenuNodeEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class MenuNodeRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final String BOARD = "BOARD";
    private static final String ITEM = "ITEM";
    private static final String FOLDER = "FOLDER";

    public MenuNodeRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 그룹별 노드 수(노드가 없는 그룹은 빠진다) */
    public Map<String, Long> countsBySet() {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        Map<String, Long> out = new HashMap<>();
        for (Tuple row : query.select(node.setKey, node.id.count()).from(node).groupBy(node.setKey).fetch()) {
            out.put(row.get(node.setKey), row.get(node.id.count()));
        }
        return out;
    }

    /** 그룹에 지금 있는 노드 ID → 자동 추가 켜짐 — 트리 저장 때 ID 재사용·새로 켠 자동 추가 폴더 판정에 쓴다(엔티티를 올리지 않는다) */
    public Map<Long, Boolean> autoAddById(String setKey) {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        Map<Long, Boolean> out = new HashMap<>();
        for (Tuple row : query.select(node.id, node.autoAdd).from(node).where(node.setKey.eq(setKey)).fetch()) {
            out.put(row.get(node.id), Boolean.TRUE.equals(row.get(node.autoAdd)));
        }
        return out;
    }

    /** 그룹의 노드를 모두 지운다(트리 전체 교체의 앞단계) */
    public long deleteBySet(String setKey) {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        return query.delete(node).where(node.setKey.eq(setKey)).execute();
    }

    /** 이 게시판을 가리키는 항목이 있는 그룹(키 차례) */
    public List<String> setKeysLinkingBoard(long boardId) {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        return query.selectDistinct(node.setKey).from(node)
                .where(node.targetType.eq(BOARD), node.targetId.eq(Long.toString(boardId)))
                .orderBy(node.setKey.asc()).fetch();
    }

    /** 이 게시판을 가리키는 <b>항목</b>을 모든 그룹에서 지운다(폴더는 지우지 않는다 — 하위가 CASCADE 로 사라진다) */
    public long deleteBoardItems(long boardId) {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        return query.delete(node).where(node.kind.eq(ITEM), node.targetType.eq(BOARD), node.targetId.eq(Long.toString(boardId))).execute();
    }

    /** 이 게시판에 연결된 폴더의 연결을 모든 그룹에서 비운다(유형·ID·키·설정·탭 동작). 바뀐 행 수 */
    public long clearBoardFolderLinks(long boardId) {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        return query.update(node).setNull(node.targetType).setNull(node.targetId).setNull(node.targetKey).setNull(node.settings).setNull(node.linkClick)
                .where(node.kind.eq(FOLDER), node.targetType.eq(BOARD), node.targetId.eq(Long.toString(boardId))).execute();
    }

    /** {@code SIDE} 전용 칸(모바일 탭·자동 추가)을 끈다 — 그룹의 위치가 {@code SIDE} 에서 바뀔 때. 바뀐 행 수 */
    public long clearSideOnly(String setKey) {
        QMenuNodeEntity node = QMenuNodeEntity.menuNodeEntity;
        return query.update(node).set(node.mobileTab, false).set(node.autoAdd, false)
                .where(node.setKey.eq(setKey), node.mobileTab.isTrue().or(node.autoAdd.isTrue())).execute();
    }
}
