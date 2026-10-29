package com.community.menu.service;

import com.community.menu.dto.MenuNode;
import com.community.menu.entity.MenuNodeEntity;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.repository.master.MenuNodeRepository;
import com.community.menu.repository.master.MenuNodeRepositoryQDSL;
import com.community.menu.repository.master.MenuSetRepository;
import com.community.menu.target.BoardMenuTargetResolver;
import com.community.menu.target.GadgetPageMenuTargetResolver;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판과 메뉴의 연결 (docs/features/menu-contract.md 「게시판과의 연결」) — 게시판을 만들거나 지우는 <b>같은 트랜잭션</b>에서 부른다.
 *
 * <ul>
 *   <li>만들기: {@code main} 그룹의 자동 추가 폴더 끝에 그 게시판 항목을 넣는다(이름 비움 = 게시판 이름, 모두에게 보임). 폴더가 없거나 노드 한도(80)면 넣지 않는다.
 *       관리자 「만들기」와 설치 프리셋이 같은 길({@code BoardCreator})로 온다.</li>
 *   <li>지우기: 그 게시판을 가리키는 항목을 모든 그룹에서 지운다. 그 게시판에 연결된 <b>폴더는 지우지 않고</b>(하위가 CASCADE 로 사라진다) 연결만 비운다.</li>
 *   <li>가젯 페이지 지우기({@link #pageDeleted})도 같은 규칙이다 — 그 페이지를 가리키는 항목은 지우고 폴더는 연결만 비운다(menu-contract 「가젯 페이지 연결」).</li>
 *   <li>트리가 바뀐 그룹은 행을 잠그고 버전을 올린다 — 그사이 메뉴 화면을 열어 둔 운영자의 저장은 409 {@code MENU_VERSION_CONFLICT} 가 된다.</li>
 * </ul>
 */
@Component
public class MenuBoardLinker {

    private static final Logger log = LoggerFactory.getLogger(MenuBoardLinker.class);

    private final MenuSetRepository sets;
    private final MenuNodeRepository nodes;
    private final MenuNodeRepositoryQDSL nodeQueries;
    private final SnowflakeIdGenerator ids;

    public MenuBoardLinker(MenuSetRepository sets, MenuNodeRepository nodes, MenuNodeRepositoryQDSL nodeQueries, SnowflakeIdGenerator ids) {
        this.sets = sets;
        this.nodes = nodes;
        this.nodeQueries = nodeQueries;
        this.ids = ids;
    }

    /** 새 게시판을 {@code main} 그룹의 자동 추가 폴더 끝에 넣는다. 넣었으면 true */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean boardCreated(long boardId, Instant now) {
        MenuSetEntity set = sets.findWithLockBySetKey(MenuLimits.MAIN_SET).orElse(null);
        if (set == null) {
            return false;
        }
        MenuNodeEntity folder = nodes.findFirstBySetKeyAndKindAndAutoAddTrue(MenuLimits.MAIN_SET, MenuNode.FOLDER).orElse(null);
        if (folder == null) {
            return false;
        }
        if (nodes.countBySetKey(MenuLimits.MAIN_SET) >= MenuLimits.NODES) {
            log.info("메뉴 노드 한도라 새 게시판을 자동 추가 폴더에 넣지 않았다 boardId={}", boardId);
            return false;
        }
        int order = nodes.findTopByParentIdOrderBySortOrderDesc(folder.getId()).map(last -> last.getSortOrder() + 1).orElse(0);
        MenuNode item = new MenuNode(null, MenuNode.ITEM, null, null, true, "ALL", null, false, false, false, BoardMenuTargetResolver.TYPE,
                Long.toString(boardId), null, null, false, List.of());
        nodes.persist(MenuNodeEntity.of(ids.nextId(), MenuLimits.MAIN_SET, folder.getId(), order, item));
        set.treeChanged(null, now);
        return true;
    }

    /** 이 게시판을 가리키는 항목을 모든 그룹에서 지우고, 이 게시판에 연결된 폴더는 연결만 비운다. 바뀐 그룹 키 */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> boardDeleted(long boardId, Instant now) {
        List<String> keys = nodeQueries.setKeysLinkingBoard(boardId);
        for (String key : keys) {
            sets.findWithLockBySetKey(key).ifPresent(set -> set.treeChanged(null, now));
        }
        if (!keys.isEmpty()) {
            nodeQueries.deleteBoardItems(boardId);
            nodeQueries.clearBoardFolderLinks(boardId);
        }
        return keys;
    }

    /** 이 가젯 페이지를 가리키는 항목을 모든 그룹에서 지우고, 이 페이지에 연결된 폴더는 연결만 비운다. 바뀐 그룹 키 */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> pageDeleted(long pageId, Instant now) {
        String type = GadgetPageMenuTargetResolver.TYPE;
        List<String> keys = nodeQueries.setKeysLinking(type, pageId);
        for (String key : keys) {
            sets.findWithLockBySetKey(key).ifPresent(set -> set.treeChanged(null, now));
        }
        if (!keys.isEmpty()) {
            nodeQueries.deleteItems(type, pageId);
            nodeQueries.clearFolderLinks(type, pageId);
        }
        return keys;
    }
}
