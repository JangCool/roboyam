package com.community.board.service;

import com.community.board.dto.BoardPolicyValues;
import com.community.board.dto.NewBoard;
import com.community.board.entity.BoardEntity;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.repository.master.BoardRepository;
import com.community.menu.service.MenuBoardLinker;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 하나를 만든다 — 게시판 행 + 공통 말머리 4개(질문·후기·잡담·공지[운영진 전용]) + 고유 말머리. 관리자 「만들기」(admin)와 설치 프리셋 적용(api)이 같이 쓴다.
 * 말머리를 받지 않은 핫딜 게시판은 공통 말머리를 끈 채 고유 말머리 {@link BoardFields#DEAL_PREFIXES}(로봇·AI·메이킹·기타)로 시작한다.
 * 입력 검사·중복 검사·순서 정하기는 부르는 쪽이 끝낸다. 부르는 쪽의 트랜잭션 안에서만 돈다.
 * 가젯 노출 금지는 종류로 시작값을 정한다({@link BoardFields#defaultGadgetExclusions} — 핫딜·직접홍보는 인기글 제외).
 * 같은 트랜잭션에서 공개 메뉴 {@code main} 그룹의 자동 추가 폴더 끝에 이 게시판 항목을 넣는다({@link MenuBoardLinker} — 폴더가 없거나 노드 한도면 건너뛴다). 소모임 게시판은 넣지 않는다.
 */
@Component
public class BoardCreator {

    private final BoardRepository boards;
    private final BoardPrefixRepository prefixes;
    private final SnowflakeIdGenerator ids;
    private final MenuBoardLinker menu;

    public BoardCreator(BoardRepository boards, BoardPrefixRepository prefixes, SnowflakeIdGenerator ids, MenuBoardLinker menu) {
        this.boards = boards;
        this.prefixes = prefixes;
        this.ids = ids;
        this.menu = menu;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BoardEntity create(NewBoard input, int sortOrder, Long operatorId, Instant now) {
        return create(input, sortOrder, operatorId, now, true);
    }

    /**
     * @param linkMainMenu 공개 메뉴 {@code main} 의 자동 추가 폴더에 넣는가 — 운영 게시판 시드({@link OpsBoardSeeder})는 넣지 않는다(소모임 게시판은 늘 넣지 않는다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BoardEntity create(NewBoard input, int sortOrder, Long operatorId, Instant now, boolean linkMainMenu) {
        BoardEntity board = BoardEntity.create(ids.nextId(), input.slug(), input.name(), input.nameEn(), input.description(), input.kind(), input.layout(),
                input.viewToggle(), input.color(), input.investmentNotice(), sortOrder, operatorId, now);
        if (input.writeMin() != null) {
            board.changePolicy(new BoardPolicyValues(input.writeMin(), null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null));
        }
        board.changeGadgetExclusions(BoardFields.defaultGadgetExclusions(input.kind()));
        boards.persist(board);
        int order = 1;
        for (String key : BoardFields.COMMON_PREFIXES) {
            prefixes.persist(BoardPrefixEntity.common(ids.nextId(), board.getId(), key, BoardFields.COMMON_PREFIX_NAMES.get(key),
                    input.commonActive().contains(key), order++, now));
        }
        int own = 10;
        for (NewBoard.Prefix prefix : ownPrefixes(input)) {
            prefixes.persist(BoardPrefixEntity.own(ids.nextId(), board.getId(), prefix.name(), prefix.staffOnly(), prefix.color(), true, own++, now));
        }
        // 소모임 게시판은 공개 메뉴에 두지 않는 그릇이라 자동 추가 폴더에 넣지 않는다(docs/features/group-contract.md)
        if (linkMainMenu && !BoardFields.KIND_GROUP.equals(input.kind())) {
            menu.boardCreated(board.getId(), now);
        }
        return board;
    }

    /** 만들 고유 말머리 — 말머리를 하나도 받지 않은(공통·고유 모두 빈) 핫딜 게시판은 {@link BoardFields#DEAL_PREFIXES} 로 시작한다 */
    private static List<NewBoard.Prefix> ownPrefixes(NewBoard input) {
        if (BoardFields.KIND_DEAL.equals(input.kind()) && input.commonActive().isEmpty() && input.prefixes().isEmpty()) {
            return BoardFields.DEAL_PREFIXES.stream().map(name -> new NewBoard.Prefix(name, false, null)).toList();
        }
        return input.prefixes();
    }
}
