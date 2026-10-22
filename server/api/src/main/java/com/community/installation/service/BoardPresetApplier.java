package com.community.installation.service;

import com.community.board.dto.NewBoard;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardCreator;
import com.community.board.service.BoardFields;
import com.community.installation.dto.BoardPreset;
import com.community.installation.entity.InstallationEntity;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 설치 때 고른 기본 게시판 구성(프리셋)으로 게시판(+말머리)을 만든다 (docs/features/board-contract.md 「데이터 구조」 마지막 줄).
 *
 * <ul>
 *   <li>설치({@link InstallationInstaller}): 설치 트랜잭션 안에서 {@link #apply} 하고 {@code installation.board_preset_applied_at} 을 채운다 — 설치가 롤백되면 게시판도 없다.</li>
 *   <li>이미 설치된 곳({@link #applyOnceIfMissing}, 서버가 뜰 때): 설치 행을 잠그고, 적용 표시가 없고 {@code board_preset} 이 있을 때만 — 게시판이 하나도 없으면 만들고,
 *       있으면 만들지 않고 — 적용 표시를 채운다. 그래서 몇 번을 띄워도 한 번이고, 운영자가 게시판을 모두 지운 뒤에도 다시 만들지 않는다.</li>
 *   <li>프리셋의 순서({@code sortOrder})대로 1부터 매긴다. 소모임 게시판({@code kind = GROUP})은 소모임 기능 전까지 만들지 않는다.</li>
 *   <li>말머리: {@code common} 말머리는 같은 이름의 공통 말머리(질문·후기·잡담·공지)를 켜고, 프리셋에 없는 공통 말머리는 꺼 둔다. 고유 말머리의 이름이 공통 말머리 이름과 같으면
 *       (예: 자유게시판의 「잡담」, 공지 게시판의 「공지」) 고유 말머리를 따로 만들지 않고 그 공통 말머리를 켠다 — 관리자 화면의 「이름 중복 없음」 규칙과 맞춘다.</li>
 *   <li>{@code staffOnlyWrite} 는 글쓰기 최소 자격 STAFF 로 만든다. 만든 이(created_by)는 루트 운영자.</li>
 * </ul>
 */
@Service
public class BoardPresetApplier {

    private static final Logger log = LoggerFactory.getLogger(BoardPresetApplier.class);

    private final BoardPresetCatalog catalog;
    private final BoardCreator creator;
    private final BoardRepository boards;
    private final InstallationRepositoryQDSL installation;

    public BoardPresetApplier(BoardPresetCatalog catalog, BoardCreator creator, BoardRepository boards, InstallationRepositoryQDSL installation) {
        this.catalog = catalog;
        this.creator = creator;
        this.boards = boards;
        this.installation = installation;
    }

    /**
     * 프리셋으로 게시판을 만든다 — 부르는 쪽의 트랜잭션 안에서. 이미 있는 주소·이름의 게시판은 건너뛴다.
     *
     * @return 만든 게시판 수. 모르는 프리셋이면 0
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int apply(String presetId, Long operatorId, Instant now) {
        Optional<BoardPreset> found = catalog.find(presetId);
        if (found.isEmpty()) {
            log.warn("모르는 게시판 구성이라 기본 게시판을 만들지 않았다 preset={}", presetId);
            return 0;
        }
        Set<String> slugs = new HashSet<>();
        Set<String> names = new HashSet<>();
        int order = 0;
        for (var board : boards.findAllByOrderBySortOrderAscIdAsc()) {
            slugs.add(board.getSlug());
            names.add(board.getName().toLowerCase(Locale.ROOT));
            order = Math.max(order, board.getSortOrder());
        }
        List<BoardPreset.Board> list = new ArrayList<>(found.get().boards());
        list.sort(Comparator.comparingInt(BoardPreset.Board::sortOrder));
        int created = 0;
        for (BoardPreset.Board board : list) {
            if (board.isGroupBoard() || slugs.contains(board.slug()) || names.contains(board.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            creator.create(toNewBoard(board), ++order, operatorId, now);
            created++;
        }
        boards.flush();
        log.info("게시판 구성 {} 으로 기본 게시판 {}개를 만들었다", presetId, created);
        return created;
    }

    /** 이미 설치된 곳에서 한 번 — 적용 표시가 없고 게시판이 하나도 없을 때만 만든다(멱등) */
    @Transactional
    public void applyOnceIfMissing() {
        InstallationEntity row = installation.lockRow().orElse(null);
        if (row == null || !row.isCompleted() || row.getBoardPresetAppliedAt() != null || row.getBoardPreset() == null) {
            return;
        }
        Instant now = Instant.now();
        if (boards.count() == 0) {
            apply(row.getBoardPreset(), row.getRootOperatorId(), now);
        } else {
            log.info("게시판이 이미 있어 설치 때 고른 게시판 구성({})을 적용하지 않고 적용됨으로 표시한다", row.getBoardPreset());
        }
        installation.markBoardPresetApplied(now);
    }

    private static NewBoard toNewBoard(BoardPreset.Board board) {
        Map<String, String> commonByName = new java.util.HashMap<>();
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
