package com.community.installation.service;

import com.community.board.dto.NewBoard;
import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardCreator;
import com.community.board.service.BoardFields;
import com.community.group.entity.GroupCategoryBoardEntity;
import com.community.group.entity.GroupCategoryEntity;
import com.community.group.repository.master.GroupCategoryBoardRepository;
import com.community.group.repository.master.GroupCategoryRepository;
import com.community.group.service.GroupCategoryNames;
import com.community.installation.dto.BoardPreset;
import com.community.installation.entity.InstallationEntity;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치 때 고른 기본 게시판 구성(프리셋)을 적용한다 — 정본은 docs/features/install-contract.md 「프리셋 구조 (2026-10-27)」.
 *
 * <ul>
 *   <li>설치({@link InstallationInstaller} → {@link #applyAtInstall}): 설치 트랜잭션 안에서 ① 일반 게시판 → ② 소모임 게시판({@code kind = GROUP}) + 소모임 분류 만들기·잇기
 *       → ③ 공개 메뉴 {@code main} 트리를 프리셋 {@code menu} 로 바꾸기({@link PresetMenuApplier}). 설치가 롤백되면 모두 없다.</li>
 *   <li>이미 설치된 곳({@link #applyOnceIfMissing}, 서버가 뜰 때): 설치 행을 잠그고, 적용 표시가 없고 {@code board_preset} 이 있을 때만 — 게시판이 하나도 없으면 <b>일반 게시판만</b>
 *       만들고, 있으면 만들지 않고 — 적용 표시를 채운다. 메뉴·소모임 분류·소모임 게시판은 새로 적용하지 않는다(운영자가 이미 구성했을 수 있다).
 *       그래서 몇 번을 띄워도 한 번이고, 운영자가 게시판을 모두 지운 뒤에도 다시 만들지 않는다.</li>
 *   <li>게시판 순서: 일반 게시판을 프리셋의 순서({@code sortOrder})대로 먼저, 이어서 소모임 게시판을 매긴다(지금 있는 마지막 순서 다음부터 1씩).
 *       이미 있는 주소·이름의 게시판은 건너뛴다.</li>
 *   <li>소모임 분류: 소모임 게시판의 {@code groupCategoryName} 마다 프리셋 차례대로 분류를 만들고(이름 키 = {@link GroupCategoryNames#key}, 같은 키의 분류가 있으면 그것을 쓴다,
 *       순서는 지금 마지막 다음부터 10 단위) 게시판을 분류 끝에 잇는다. 설치 전이라 관리자 분류 화면과 겨룰 일이 없어 분류 잠금(admin {@code GroupCategoryLock})은 잡지 않는다
 *       — 설치는 {@code installation} 행 잠금으로 한 번만 돈다.</li>
 *   <li>말머리: {@code common} 말머리는 같은 이름의 공통 말머리(질문·후기·잡담·공지)를 켜고, 프리셋에 없는 공통 말머리는 꺼 둔다. 고유 말머리의 이름이 공통 말머리 이름과 같으면
 *       (예: 자유게시판의 「잡담」, 공지 게시판의 「공지」) 고유 말머리를 따로 만들지 않고 그 공통 말머리를 켠다 — 관리자 화면의 「이름 중복 없음」 규칙과 맞춘다.
 *       고유 말머리는 활성 한도({@link BoardFields#PREFIX_ACTIVE_MAX})까지만.</li>
 *   <li>{@code staffOnlyWrite} 는 글쓰기 최소 자격 STAFF 로 만든다. 만든 이(created_by)는 루트 운영자.</li>
 * </ul>
 */
@Service
public class BoardPresetApplier {

    private static final Logger log = LoggerFactory.getLogger(BoardPresetApplier.class);
    private static final int CATEGORY_ORDER_STEP = 10;

    private final BoardPresetCatalog catalog;
    private final BoardCreator creator;
    private final BoardRepository boards;
    private final InstallationRepositoryQDSL installation;
    private final GroupCategoryRepository categories;
    private final GroupCategoryBoardRepository assignments;
    private final SnowflakeIdGenerator ids;
    private final PresetMenuApplier menu;

    public BoardPresetApplier(BoardPresetCatalog catalog, BoardCreator creator, BoardRepository boards, InstallationRepositoryQDSL installation,
                              GroupCategoryRepository categories, GroupCategoryBoardRepository assignments, SnowflakeIdGenerator ids, PresetMenuApplier menu) {
        this.catalog = catalog;
        this.creator = creator;
        this.boards = boards;
        this.installation = installation;
        this.categories = categories;
        this.assignments = assignments;
        this.ids = ids;
        this.menu = menu;
    }

    /**
     * 설치 때 — 일반 게시판, 소모임 게시판·분류, 메뉴 {@code main} 트리를 만든다. 부르는 쪽(설치)의 트랜잭션 안에서.
     *
     * @return 만든 게시판 수(일반 + 소모임). 모르는 프리셋이면 0
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int applyAtInstall(String presetId, Long operatorId, Instant now) {
        Optional<BoardPreset> found = catalog.find(presetId);
        if (found.isEmpty()) {
            log.warn("모르는 게시판 구성이라 기본 게시판·메뉴를 만들지 않았다 preset={}", presetId);
            return 0;
        }
        BoardPreset preset = found.get();
        Existing existing = existing();
        int general = createBoards(preset, false, existing, operatorId, now).size();
        List<CreatedBoard> groupBoards = createBoards(preset, true, existing, operatorId, now);
        boards.flush(); // 분류 할당 행(외래 키 boards)보다 게시판 행이 먼저 들어가게
        int linked = linkGroupCategories(groupBoards, operatorId, now);
        boards.flush();
        boolean menuReplaced = preset.menu() != null && menu.replaceMain(presetId, preset.menu(), operatorId, now);
        log.info("게시판 구성 {} 적용 — 일반 게시판 {}개 · 소모임 게시판 {}개(분류 연결 {}개) · 메뉴 {}", presetId, general, groupBoards.size(), linked,
                menuReplaced ? "교체" : "그대로");
        return general + groupBoards.size();
    }

    /** 이미 설치된 곳에서 한 번 — 적용 표시가 없고 게시판이 하나도 없을 때만 일반 게시판을 만든다(멱등). 메뉴·소모임은 적용하지 않는다 */
    @Transactional
    public void applyOnceIfMissing() {
        InstallationEntity row = installation.lockRow().orElse(null);
        if (row == null || !row.isCompleted() || row.getBoardPresetAppliedAt() != null || row.getBoardPreset() == null) {
            return;
        }
        Instant now = Instant.now();
        if (boards.count() == 0) {
            Optional<BoardPreset> found = catalog.find(row.getBoardPreset());
            if (found.isEmpty()) {
                log.warn("모르는 게시판 구성이라 기본 게시판을 만들지 않았다 preset={}", row.getBoardPreset());
            } else {
                int created = createBoards(found.get(), false, existing(), row.getRootOperatorId(), now).size();
                boards.flush();
                log.info("게시판 구성 {} 으로 기본 게시판 {}개를 만들었다(메뉴·소모임은 적용하지 않음)", row.getBoardPreset(), created);
            }
        } else {
            log.info("게시판이 이미 있어 설치 때 고른 게시판 구성({})을 적용하지 않고 적용됨으로 표시한다", row.getBoardPreset());
        }
        installation.markBoardPresetApplied(now);
    }

    // ── 게시판 ──

    /** 지금 있는 게시판의 주소·이름·마지막 순서 — 만들 때마다 더해 간다 */
    private static final class Existing {
        final Set<String> slugs = new HashSet<>();
        final Set<String> names = new HashSet<>();
        int order;
    }

    private Existing existing() {
        Existing existing = new Existing();
        for (BoardEntity board : boards.findAllByOrderBySortOrderAscIdAsc()) {
            existing.slugs.add(board.getSlug());
            existing.names.add(board.getName().toLowerCase(Locale.ROOT));
            existing.order = Math.max(existing.order, board.getSortOrder());
        }
        return existing;
    }

    private record CreatedBoard(long boardId, String categoryName) {
    }

    /** {@code group} 이면 소모임 게시판만, 아니면 일반 게시판만 프리셋 순서대로 만든다. 만든 게시판(소모임은 분류 이름과 함께) */
    private List<CreatedBoard> createBoards(BoardPreset preset, boolean group, Existing existing, Long operatorId, Instant now) {
        List<BoardPreset.Board> list = new ArrayList<>(preset.boards());
        list.sort(Comparator.comparingInt(BoardPreset.Board::sortOrder));
        List<CreatedBoard> created = new ArrayList<>();
        for (BoardPreset.Board board : list) {
            String name = board.name().toLowerCase(Locale.ROOT);
            if (board.isGroupBoard() != group || existing.slugs.contains(board.slug()) || existing.names.contains(name)) {
                continue;
            }
            BoardEntity entity = creator.create(toNewBoard(board), ++existing.order, operatorId, now);
            existing.slugs.add(board.slug());
            existing.names.add(name);
            created.add(new CreatedBoard(entity.getId(), board.groupCategoryName()));
        }
        return created;
    }

    // ── 소모임 분류 ──

    /** 만든 소모임 게시판을 분류에 잇는다(분류가 없으면 프리셋 차례대로 만든다). 이은 게시판 수 */
    private int linkGroupCategories(List<CreatedBoard> groupBoards, Long operatorId, Instant now) {
        if (groupBoards.isEmpty()) {
            return 0;
        }
        Instant at = now.truncatedTo(ChronoUnit.MICROS); // DB 가 마이크로초까지 저장한다 — 관리자 「먼저 고쳤어요」 비교와 같은 값이 되게
        Map<String, GroupCategoryEntity> byKey = new HashMap<>();
        int order = 0;
        for (GroupCategoryEntity cat : categories.findAllByOrderBySortOrderAscIdAsc()) {
            byKey.put(cat.getNameKey(), cat);
            order = Math.max(order, cat.getSortOrder());
        }
        Map<Long, Integer> nextAssigned = new HashMap<>();
        int linked = 0;
        for (CreatedBoard board : groupBoards) {
            if (board.categoryName() == null) {
                continue; // 카탈로그가 막지만, 분류 없는 소모임 게시판도 그대로 둘 수 있다(「분류 없음」)
            }
            String name = GroupCategoryNames.normalize(board.categoryName());
            String key = GroupCategoryNames.key(name);
            GroupCategoryEntity cat = byKey.get(key);
            if (cat == null) {
                order += CATEGORY_ORDER_STEP;
                cat = GroupCategoryEntity.create(ids.nextId(), name, key, order, operatorId, at);
                categories.persist(cat);
                byKey.put(key, cat);
                nextAssigned.put(cat.getId(), 1);
            } else {
                cat.touch(operatorId, at);
            }
            long categoryId = cat.getId();
            int assigned = nextAssigned.computeIfAbsent(categoryId,
                    id -> assignments.findTopByCategoryIdOrderByAssignedOrderDesc(id).map(last -> last.getAssignedOrder() + 1).orElse(1));
            assignments.persist(GroupCategoryBoardEntity.assign(board.boardId(), categoryId, assigned));
            nextAssigned.put(categoryId, assigned + 1);
            linked++;
        }
        return linked;
    }

    // ── 말머리 ──

    private static NewBoard toNewBoard(BoardPreset.Board board) {
        Map<String, String> commonByName = new HashMap<>();
        BoardFields.COMMON_PREFIX_NAMES.forEach((key, name) -> commonByName.put(name.toLowerCase(Locale.ROOT), key));
        BoardFields.COMMON_PREFIX_NAMES_EN.forEach((key, name) -> commonByName.put(name.toLowerCase(Locale.ROOT), key));
        Set<String> commonActive = new HashSet<>();
        List<NewBoard.Prefix> own = new ArrayList<>();
        List<BoardPreset.Prefix> prefixes = new ArrayList<>(board.prefixes());
        prefixes.sort(Comparator.comparingInt(BoardPreset.Prefix::sortOrder));
        for (BoardPreset.Prefix prefix : prefixes) {
            String common = commonByName.get(prefix.name().toLowerCase(Locale.ROOT));
            if (common != null) {
                commonActive.add(common);
            } else if (own.size() < BoardFields.PREFIX_ACTIVE_MAX) {
                own.add(new NewBoard.Prefix(prefix.name(), prefix.staffOnly(), null));
            }
        }
        String kind = board.kind() == null ? BoardFields.KIND_GENERAL : board.kind();
        String layout = board.layout() == null ? "LIST" : board.layout();
        return new NewBoard(board.slug(), board.name(), board.nameEn(), board.description() == null ? "" : board.description().strip(), kind, layout,
                board.viewToggle(), board.color(), board.investmentNotice(), board.staffOnlyWrite() ? BoardFields.STAFF : null, commonActive, own);
    }
}
