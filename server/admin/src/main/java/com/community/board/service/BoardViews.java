package com.community.board.service;

import com.community.board.dto.BoardDefaultsValues;
import com.community.board.dto.BoardPolicyValues;
import com.community.board.dto.response.BoardDetailView;
import com.community.board.dto.response.BoardGadgetView;
import com.community.board.dto.response.BoardListItemView;
import com.community.board.dto.response.BoardPrefixView;
import com.community.board.dto.response.BoardRefView;
import com.community.board.entity.BoardDefaultsEntity;
import com.community.board.entity.BoardEntity;
import com.community.board.entity.BoardGadget;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.repository.master.BoardContentQueries;
import com.community.board.repository.master.BoardDefaultsRepository;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.repository.master.BoardRepository;
import com.community.board.repository.master.BoardStaffQueries;
import com.community.operator.repository.master.OperatorRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 게시판 응답을 조립한다(목록 한 줄·상세·짧은 모양). 여러 표(게시판·기본 설정·말머리·담당 수·글 집계)를 합치므로 매퍼가 아니라 여기서 만든다.
 * 부르는 서비스의 트랜잭션 안에서 돈다.
 */
@Component
public class BoardViews {

    private final BoardRepository boards;
    private final BoardDefaultsRepository defaults;
    private final BoardPrefixRepository prefixes;
    private final BoardStaffQueries staff;
    private final BoardContentQueries content;
    private final OperatorRepository operators;
    private final Messages messages;

    public BoardViews(BoardRepository boards, BoardDefaultsRepository defaults, BoardPrefixRepository prefixes, BoardStaffQueries staff,
                      BoardContentQueries content, OperatorRepository operators, Messages messages) {
        this.boards = boards;
        this.defaults = defaults;
        this.prefixes = prefixes;
        this.staff = staff;
        this.content = content;
        this.operators = operators;
        this.messages = messages;
    }

    /** 지금 기본 설정 행(마이그레이션이 만든다 — 없으면 설치가 깨진 것) */
    public BoardDefaultsEntity defaultsRow() {
        return defaults.findById(BoardDefaultsEntity.ROW_ID).orElseThrow(() -> new CommonException(ErrorCode.INTERNAL));
    }

    /** 모든 게시판(순서대로) */
    public List<BoardListItemView> list() {
        BoardDefaultsValues base = defaultsRow().values();
        Map<Long, Long> posts = content.postCounts();
        Map<Long, int[]> counts = staff.countsByBoard();
        List<BoardListItemView> out = new ArrayList<>();
        int order = 1;
        for (BoardEntity board : boards.findAllByOrderBySortOrderAscIdAsc()) {
            out.add(item(board, order++, posts.getOrDefault(board.getId(), 0L), counts.getOrDefault(board.getId(), new int[2]), base));
        }
        return out;
    }

    /** 게시판 하나의 목록 줄 */
    public BoardListItemView itemOf(long boardId) {
        return list().stream().filter(item -> item.id().equals(Long.toString(boardId))).findFirst()
                .orElseThrow(com.community.board.exception.BoardNotFoundException::new);
    }

    public BoardDetailView detail(BoardEntity board) {
        BoardDefaultsValues base = defaultsRow().values();
        Map<Long, Long> usage = content.prefixUsage(board.getId());
        List<BoardPrefixView> prefixViews = sortedPrefixes(board.getId()).stream()
                .map(p -> new BoardPrefixView(Long.toString(p.getId()), p.getName(), p.getCommonKey(), p.isStaffOnly(), p.getColor(), p.isActive(),
                        usage.getOrDefault(p.getId(), 0L)))
                .toList();
        BoardPolicyValues policy = board.policy();
        return new BoardDetailView(Long.toString(board.getId()), board.getSlug(), board.getName(), board.getNameEn(), board.getDescription(), board.getKind(),
                board.getLayout(), board.isViewToggle(), board.getColor(), board.isAllowAdult(), board.isInvestmentNotice(), board.isActive(),
                content.postCount(board.getId()), board.getWriteLock(), board.getIntroText(), board.getWriteGuideText(),
                board.getGadgetExclusions().stream().map(Enum::name).toList(), policy, prefixViews, base, diffCount(board), gadgets());
    }

    /** 「가젯 노출 금지」에 고를 수 있는 가젯(enum 순서, 이름은 요청 언어) */
    public List<BoardGadgetView> gadgets() {
        return Arrays.stream(BoardGadget.values()).map(g -> new BoardGadgetView(g.name(), messages.get("board.gadget." + g.name()))).toList();
    }

    /** 말머리 — 공통 4개(고정 순서) 다음 고유(표시 순서) */
    public List<BoardPrefixEntity> sortedPrefixes(long boardId) {
        List<BoardPrefixEntity> all = new ArrayList<>(prefixes.findByBoardIdOrderBySortOrderAscIdAsc(boardId));
        all.sort(Comparator.comparingInt((BoardPrefixEntity p) -> p.isCommon() ? BoardFields.COMMON_PREFIXES.indexOf(p.getCommonKey()) : 100)
                .thenComparingInt(BoardPrefixEntity::getSortOrder).thenComparing(BoardPrefixEntity::getId));
        return all;
    }

    public static BoardRefView ref(BoardEntity board) {
        return new BoardRefView(Long.toString(board.getId()), board.getSlug(), board.getName(), board.getNameEn());
    }

    /** 기본값과 다르게 정한 정책 칸 수 + 잠금이면 1 */
    public static int diffCount(BoardEntity board) {
        return board.policy().overriddenCount() + (board.getWriteLock() != null ? 1 : 0);
    }

    /** 운영자 이름 — 없거나 지워졌으면 null */
    public String operatorName(Long operatorId) {
        return operatorId == null ? null : operators.findById(operatorId).map(o -> o.getOperatorName()).orElse(null);
    }

    private static BoardListItemView item(BoardEntity board, int order, long postCount, int[] counts, BoardDefaultsValues base) {
        return new BoardListItemView(Long.toString(board.getId()), board.getSlug(), board.getName(), board.getNameEn(), board.getDescription(),
                board.getKind(), board.getLayout(), board.getColor(), board.isActive(), order, postCount, counts[0], counts[1], diffCount(board),
                board.getWriteMin() != null ? board.getWriteMin() : base.writeMin(), board.getCommentMin() != null ? board.getCommentMin() : base.commentMin());
    }
}
