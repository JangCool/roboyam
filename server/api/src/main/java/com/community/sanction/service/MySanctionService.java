package com.community.sanction.service;

import com.community.sanction.dto.request.AppealRequest;
import com.community.sanction.dto.response.AppealCreatedView;
import com.community.sanction.dto.response.MyAppealView;
import com.community.sanction.dto.response.MyBoardRefView;
import com.community.sanction.dto.response.MyBoardSanctionView;
import com.community.sanction.dto.response.MySanctionView;
import com.community.sanction.dto.response.MySanctionsView;
import com.community.sanction.exception.BoardSanctionException;
import com.community.sanction.exception.SanctionNotFoundException;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.MemberRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 제재·이의제기(공개 사이트 — docs/features/member-sanction-contract.md · board-sanction-contract.md, 모양은 user-web {@code useSanctionApi.types.ts}). 로그인만 하면 쓴다
 * (정지·영구 정지 중에도, 이메일 인증 전에도). 규칙은 shared {@link MemberSanctionService}·{@link BoardSanctionService}. 내 제재를 읽기 전에 지난 정지를 먼저 끝낸다(정책 7).
 * 게시판 제재에는 <b>처리자 신원이 없다</b>(결정 7).
 */
@Service
public class MySanctionService {

    /** 이력 최대 */
    static final int ITEMS_MAX = 50;

    private final MemberSanctionService sanctions;
    private final BoardSanctionService boardSanctions;

    public MySanctionService(MemberSanctionService sanctions, BoardSanctionService boardSanctions) {
        this.sanctions = sanctions;
        this.boardSanctions = boardSanctions;
    }

    @Transactional
    public MySanctionsView mine(long userId) {
        sanctions.settle(userId);
        MemberRow member = sanctions.member(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = sanctions.now();
        List<SanctionRow> rows = sanctions.allOf(userId);
        SanctionLadder.Standing standing = SanctionLadder.standing(rows, now);
        Map<Long, AppealRow> appeals = sanctions.appealsOf(userId).stream().filter(a -> a.sanctionId() != null)
                .collect(Collectors.toMap(AppealRow::sanctionId, Function.identity(), (a, b) -> a));
        List<MySanctionView> items = rows.stream().limit(ITEMS_MAX).map(row -> view(row, appeals.get(row.id()), now)).toList();
        MySanctionView current = standing.running() == null ? null : view(standing.running(), appeals.get(standing.running().id()), now);
        Map<Long, AppealRow> boardAppeals = boardSanctions.appealsOf(userId);
        List<MyBoardSanctionView> board = boardSanctions.ofUser(userId, ITEMS_MAX).stream().map(row -> boardView(row, boardAppeals.get(row.id()), now)).toList();
        return new MySanctionsView(member.status(), standing.warningCount(), current, items, board);
    }

    @Transactional
    public void acknowledge(long userId, String sanctionIdText) {
        sanctions.acknowledge(userId, parse(sanctionIdText, SanctionNotFoundException::new));
    }

    /** 게시판 경고 확인(멱등) — 내 것이 아니면 404 {@code BOARD_SANCTION_NOT_FOUND} */
    @Transactional
    public void acknowledgeBoard(long userId, String sanctionIdText) {
        boardSanctions.acknowledge(userId, parse(sanctionIdText, BoardSanctionException::notFound));
    }

    /** 이의제기 — {@code boardSanctionId} 가 있으면 게시판 제재, 아니면 사이트 제재. 둘 다 보내면 400 {@code fields.boardSanctionId} */
    @Transactional
    public AppealCreatedView appeal(long userId, AppealRequest body) {
        boolean board = body != null && body.boardSanctionId() != null && !body.boardSanctionId().isBlank();
        boolean site = body != null && body.sanctionId() != null && !body.sanctionId().isBlank();
        if (board && site) {
            throw ValidationException.of("boardSanctionId", new MessageRef("appeal.target.one"));
        }
        AppealRow appeal = board ? boardSanctions.fileAppeal(userId, parse(body.boardSanctionId(), BoardSanctionException::notFound), body.reason())
                : sanctions.fileAppeal(userId, parse(body == null ? null : body.sanctionId(), SanctionNotFoundException::new), body == null ? null : body.reason());
        return new AppealCreatedView(Long.toString(appeal.id()), appeal.no(), appeal.status().name());
    }

    private static MySanctionView view(SanctionRow row, AppealRow appeal, Instant now) {
        boolean acknowledged = !row.kind().isWarning() || row.acknowledgedAt() != null;
        return new MySanctionView(Long.toString(row.id()), row.no(), row.kind().name(), row.effectiveStatus(now).name(), row.reason(), row.warningNo(), row.startsAt(),
                row.endsAt(), row.createdAt(), acknowledged, appealView(appeal), MemberSanctionService.appealDeadline(row));
    }

    private static MyBoardSanctionView boardView(BoardSanctionRow row, AppealRow appeal, Instant now) {
        boolean acknowledged = !row.kind().isWarning() || row.acknowledgedAt() != null;
        return new MyBoardSanctionView(Long.toString(row.id()), row.no(), new MyBoardRefView(Long.toString(row.boardId()), row.boardSlug(), row.boardName(), row.boardNameEn()),
                row.kind().name(), row.effectiveStatus(now).name(), row.reasonCode(), row.reasonText(), row.startsAt(), row.endsAt(), row.createdAt(), acknowledged,
                appealView(appeal), BoardSanctionService.appealDeadline(row));
    }

    private static MyAppealView appealView(AppealRow appeal) {
        return appeal == null ? null : new MyAppealView(Long.toString(appeal.id()), appeal.no(), appeal.status().name(), appeal.reason(), appeal.answer(),
                appeal.createdAt(), appeal.answeredAt());
    }

    /** 제재 ID — 숫자가 아니면 없는 제재(404) */
    private static long parse(String text, Supplier<? extends RuntimeException> notFound) {
        if (text == null || text.isBlank() || text.strip().length() > 19) {
            throw notFound.get();
        }
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            throw notFound.get();
        }
    }
}
