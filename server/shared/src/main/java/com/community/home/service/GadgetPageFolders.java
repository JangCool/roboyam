package com.community.home.service;

import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardFields;
import com.community.menu.dto.MenuNode;
import com.community.menu.entity.MenuNodeEntity;
import com.community.menu.repository.master.MenuNodeRepository;
import com.community.menu.service.MenuLimits;
import com.community.menu.target.BoardMenuTargetResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 가젯 페이지의 기준 폴더 — 공개 메뉴 main 의 폴더 노드와 그 아래(하위 폴더까지) {@code BOARD} 항목의 게시판(메뉴 차례, 겹치면 한 번, 소모임 게시판 제외).
 * 꺼진 게시판도 담고({@link Board#active}) 쓰는 쪽이 거른다. 부르는 쪽의 트랜잭션 안에서 쓴다(gadget-page-contract 「필드·검증」).
 */
@Component
public class GadgetPageFolders {

    private final MenuNodeRepository nodes;
    private final BoardRepository boards;

    public GadgetPageFolders(MenuNodeRepository nodes, BoardRepository boards) {
        this.nodes = nodes;
        this.boards = boards;
    }

    /** 폴더의 게시판 한 개 */
    public record Board(long id, String slug, String name, String nameEn, String layout, boolean active) {
    }

    /** 기준 폴더로 고를 수 있는 폴더 한 개 */
    public record Folder(long id, String name, String nameEn, List<Board> boards) {

        /** 켜진 게시판의 slug */
        public List<String> activeSlugs() {
            return boards.stream().filter(Board::active).map(Board::slug).toList();
        }
    }

    /** main 의 모든 폴더(트리 차례) */
    public Map<Long, Folder> all() {
        List<MenuNodeEntity> rows = nodes.findBySetKeyOrderBySortOrderAscIdAsc(MenuLimits.MAIN_SET);
        Map<Long, List<MenuNodeEntity>> children = new HashMap<>();
        List<MenuNodeEntity> roots = new ArrayList<>();
        for (MenuNodeEntity row : rows) {
            if (row.getParentId() == null) {
                roots.add(row);
            } else {
                children.computeIfAbsent(row.getParentId(), k -> new ArrayList<>()).add(row);
            }
        }
        Map<Long, Board> byId = new HashMap<>();
        for (BoardEntity b : boards.findAllByOrderBySortOrderAscIdAsc()) {
            if (!BoardFields.KIND_GROUP.equals(b.getKind())) {
                byId.put(b.getId(), new Board(b.getId(), b.getSlug(), b.getName(), b.getNameEn(), b.getLayout(), b.isActive()));
            }
        }
        Map<Long, Folder> out = new LinkedHashMap<>();
        walk(roots, children, byId, out);
        return out;
    }

    public Optional<Folder> of(Long folderId) {
        return folderId == null ? Optional.empty() : Optional.ofNullable(all().get(folderId));
    }

    private static void walk(List<MenuNodeEntity> list, Map<Long, List<MenuNodeEntity>> children, Map<Long, Board> byId, Map<Long, Folder> out) {
        for (MenuNodeEntity row : list) {
            if (!MenuNode.FOLDER.equals(row.getKind())) {
                continue;
            }
            List<Board> found = new ArrayList<>();
            collect(children.getOrDefault(row.getId(), List.of()), children, byId, found);
            MenuNode node = row.toNode();
            out.put(row.getId(), new Folder(row.getId(), node.label(), node.nameEn(), List.copyOf(found)));
            walk(children.getOrDefault(row.getId(), List.of()), children, byId, out);
        }
    }

    private static void collect(List<MenuNodeEntity> list, Map<Long, List<MenuNodeEntity>> children, Map<Long, Board> byId, List<Board> found) {
        for (MenuNodeEntity row : list) {
            if (MenuNode.FOLDER.equals(row.getKind())) {
                collect(children.getOrDefault(row.getId(), List.of()), children, byId, found);
                continue;
            }
            MenuNode node = row.toNode();
            if (!BoardMenuTargetResolver.TYPE.equals(node.targetType()) || node.targetId() == null) {
                continue;
            }
            try {
                Board board = byId.get(Long.parseLong(node.targetId()));
                if (board != null && found.stream().noneMatch(b -> b.id() == board.id())) {
                    found.add(board);
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 ID 는 건너뛴다
            }
        }
    }
}
