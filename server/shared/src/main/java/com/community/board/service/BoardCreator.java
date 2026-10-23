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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 하나를 만든다 — 게시판 행 + 공통 말머리 4개(질문·후기·잡담·공지[운영진 전용]) + 고유 말머리. 관리자 「만들기」(admin)와 설치 프리셋 적용(api)이 같이 쓴다.
 * 입력 검사·중복 검사·순서 정하기는 부르는 쪽이 끝낸다. 부르는 쪽의 트랜잭션 안에서만 돈다.
 * 같은 트랜잭션에서 공개 메뉴 {@code main} 그룹의 자동 추가 폴더 끝에 이 게시판 항목을 넣는다({@link MenuBoardLinker} — 폴더가 없거나 노드 한도면 건너뛴다).
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
        BoardEntity board = BoardEntity.create(ids.nextId(), input.slug(), input.name(), input.nameEn(), input.description(), input.kind(), input.layout(),
                input.viewToggle(), input.color(), input.investmentNotice(), sortOrder, operatorId, now);
        if (input.writeMin() != null) {
            board.changePolicy(new BoardPolicyValues(input.writeMin(), null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null));
        }
        boards.persist(board);
        int order = 1;
        for (String key : BoardFields.COMMON_PREFIXES) {
            prefixes.persist(BoardPrefixEntity.common(ids.nextId(), board.getId(), key, BoardFields.COMMON_PREFIX_NAMES.get(key),
                    input.commonActive().contains(key), order++, now));
        }
        int own = 10;
        for (NewBoard.Prefix prefix : input.prefixes()) {
            prefixes.persist(BoardPrefixEntity.own(ids.nextId(), board.getId(), prefix.name(), prefix.staffOnly(), prefix.color(), true, own++, now));
        }
        menu.boardCreated(board.getId(), now);
        return board;
    }
}
