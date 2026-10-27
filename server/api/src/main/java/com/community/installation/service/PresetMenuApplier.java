package com.community.installation.service;

import com.community.installation.dto.BoardPreset;
import com.community.menu.dto.MenuBoardRef;
import com.community.menu.dto.MenuNode;
import com.community.menu.entity.MenuSetEntity;
import com.community.menu.repository.master.MenuSetRepository;
import com.community.menu.service.MenuLimits;
import com.community.menu.service.MenuSnapshots;
import com.community.menu.service.MenuTreeStore;
import com.community.menu.service.MenuTreeValidator;
import com.community.menu.target.BoardMenuTargetResolver;
import com.community.menu.target.BuiltinMenuTargetResolver;
import com.community.menu.target.MenuValidationContext;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치 때 공개 메뉴 {@code main} 그룹의 트리를 프리셋 {@code menu} 정의로 바꾼다 (docs/features/install-contract.md 「프리셋 구조」 1).
 *
 * <ul>
 *   <li>설치 트랜잭션 안에서, 게시판을 모두 만든 <b>뒤</b> 부른다 — 그때까지 게시판 만들기가 자동 추가 폴더에 넣은 항목까지 지우고(V43 시드 포함) 정의대로 새로 넣는다.
 *       그룹 행을 잠그고 버전을 1 올린다(만든 사람 = 루트 운영자).</li>
 *   <li>게시판 항목은 프리셋의 {@code slug} 로 가리킨다 — 그 주소의 일반 게시판이 없으면(만들지 못했으면) 그 항목은 뺀다.</li>
 *   <li>관리자 메뉴 저장과 같은 검사기({@link MenuTreeValidator})를 지난다. 프리셋은 서버가 뜰 때({@link BoardPresetCatalog}) 같은 규칙으로 이미 검사됐으므로 여기서 걸리는 일은
 *       없어야 하지만, 걸리면 설치를 깨지 않고 메뉴만 그대로 둔다(경고 로그).</li>
 * </ul>
 */
@Component
public class PresetMenuApplier {

    private static final Logger log = LoggerFactory.getLogger(PresetMenuApplier.class);

    private final MenuSetRepository sets;
    private final MenuTreeStore store;
    private final MenuTreeValidator validator;
    private final MenuSnapshots snapshots;

    public PresetMenuApplier(MenuSetRepository sets, MenuTreeStore store, MenuTreeValidator validator, MenuSnapshots snapshots) {
        this.sets = sets;
        this.store = store;
        this.validator = validator;
        this.snapshots = snapshots;
    }

    /**
     * {@code main} 트리를 바꾼다. 바꿨으면 true.
     *
     * @param presetId 로그용
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceMain(String presetId, List<BoardPreset.MenuEntry> menu, Long operatorId, Instant now) {
        MenuSetEntity set = sets.findWithLockBySetKey(MenuLimits.MAIN_SET).orElse(null);
        if (set == null) {
            log.warn("메뉴 그룹 {} 이 없어 게시판 구성 {} 의 메뉴를 넣지 않았다", MenuLimits.MAIN_SET, presetId);
            return false;
        }
        Map<Long, MenuBoardRef> boards = snapshots.boards(); // 소모임 게시판은 빠져 있다
        Map<String, Long> boardIdBySlug = new HashMap<>();
        boards.values().forEach(board -> boardIdBySlug.put(board.slug(), board.id()));
        List<String> missing = new ArrayList<>();
        List<MenuNode> wanted = toNodes(menu, boardIdBySlug, missing);
        if (!missing.isEmpty()) {
            log.info("게시판 구성 {} 의 메뉴에서 없는 게시판 항목을 뺐다 slugs={}", presetId, missing);
        }

        Set<String> gradeKeys = new HashSet<>(MenuSnapshots.levels(snapshots.grades()).keySet());
        List<MenuNode> tree;
        try {
            tree = validator.validate(wanted, new MenuValidationContext(boards, gradeKeys, set.getPlacement()), Map.of());
        } catch (ValidationException e) {
            log.warn("게시판 구성 {} 의 메뉴가 메뉴 규칙에 맞지 않아 넣지 않았다 fields={}", presetId, e.getFields().keySet());
            return false;
        }
        // 지금 노드를 읽어 영속성 컨텍스트에서 떼어 둔다(게시판 만들기가 방금 넣은 자동 추가 항목 포함) — 관리자 저장과 같은 순서
        store.load(MenuLimits.MAIN_SET);
        store.replace(MenuLimits.MAIN_SET, tree, Set.of());
        set.treeChanged(operatorId, now);
        log.info("게시판 구성 {} 으로 메뉴 {} 를 바꿨다 노드={}", presetId, MenuLimits.MAIN_SET, MenuNode.count(tree));
        return true;
    }

    /** 프리셋 정의 → 메뉴 노드(ID 없음). 없는 게시판 항목은 빼고 {@code missing} 에 주소를 모은다 */
    static List<MenuNode> toNodes(List<BoardPreset.MenuEntry> entries, Map<String, Long> boardIdBySlug, List<String> missing) {
        List<MenuNode> out = new ArrayList<>();
        for (BoardPreset.MenuEntry entry : entries) {
            String audience = entry.audienceOrAll();
            if (entry.isFolder()) {
                out.add(new MenuNode(null, MenuNode.FOLDER, entry.label(), entry.nameEn(), entry.visibleOrDefault(), audience, null, entry.collapsibleOn(),
                        entry.collapsedOn(), entry.autoAddOn(), null, null, null, null, false, toNodes(entry.children(), boardIdBySlug, missing)));
            } else if (BoardPreset.MenuEntry.BOARD.equals(entry.type())) {
                Long boardId = boardIdBySlug.get(entry.slug());
                if (boardId == null) {
                    missing.add(entry.slug());
                    continue;
                }
                out.add(new MenuNode(null, MenuNode.ITEM, entry.label(), entry.nameEn(), entry.visibleOrDefault(), audience, null, false, false, false,
                        BoardMenuTargetResolver.TYPE, Long.toString(boardId), null, null, entry.tabOn(), List.of()));
            } else {
                out.add(new MenuNode(null, MenuNode.ITEM, entry.label(), entry.nameEn(), entry.visibleOrDefault(), audience, null, false, false, false,
                        BuiltinMenuTargetResolver.TYPE, null, entry.key(), null, entry.tabOn(), List.of()));
            }
        }
        return out;
    }
}
