package com.community.sanction.service;

import com.community.sanction.dto.request.AppealRequest;
import com.community.sanction.dto.response.AppealCreatedView;
import com.community.sanction.dto.response.MyAppealView;
import com.community.sanction.dto.response.MySanctionView;
import com.community.sanction.dto.response.MySanctionsView;
import com.community.sanction.exception.SanctionNotFoundException;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.MemberRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 제재·이의제기(공개 사이트 — docs/features/member-sanction-contract.md, 모양은 user-web {@code useSanctionApi.types.ts}). 로그인만 하면 쓴다(정지·영구 정지 중에도, 이메일 인증 전에도).
 * 규칙은 shared {@link MemberSanctionService}. 내 제재를 읽기 전에 지난 정지를 먼저 끝낸다(정책 7).
 */
@Service
public class MySanctionService {

    /** 이력 최대 */
    static final int ITEMS_MAX = 50;

    private final MemberSanctionService sanctions;

    public MySanctionService(MemberSanctionService sanctions) {
        this.sanctions = sanctions;
    }

    @Transactional
    public MySanctionsView mine(long userId) {
        sanctions.settle(userId);
        MemberRow member = sanctions.member(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Instant now = sanctions.now();
        List<SanctionRow> rows = sanctions.allOf(userId);
        SanctionLadder.Standing standing = SanctionLadder.standing(rows, now);
        Map<Long, AppealRow> appeals = sanctions.appealsOf(userId).stream().collect(Collectors.toMap(AppealRow::sanctionId, Function.identity(), (a, b) -> a));
        List<MySanctionView> items = rows.stream().limit(ITEMS_MAX).map(row -> view(row, appeals.get(row.id()), now)).toList();
        MySanctionView current = standing.running() == null ? null : view(standing.running(), appeals.get(standing.running().id()), now);
        return new MySanctionsView(member.status(), standing.warningCount(), current, items);
    }

    @Transactional
    public void acknowledge(long userId, String sanctionIdText) {
        sanctions.acknowledge(userId, parse(sanctionIdText));
    }

    @Transactional
    public AppealCreatedView appeal(long userId, AppealRequest body) {
        long sanctionId = parse(body == null ? null : body.sanctionId());
        AppealRow appeal = sanctions.fileAppeal(userId, sanctionId, body.reason());
        return new AppealCreatedView(Long.toString(appeal.id()), appeal.no(), appeal.status().name());
    }

    private static MySanctionView view(SanctionRow row, AppealRow appeal, Instant now) {
        MyAppealView appealView = appeal == null ? null : new MyAppealView(Long.toString(appeal.id()), appeal.no(), appeal.status().name(), appeal.reason(),
                appeal.answer(), appeal.createdAt(), appeal.answeredAt());
        boolean acknowledged = !row.kind().isWarning() || row.acknowledgedAt() != null;
        return new MySanctionView(Long.toString(row.id()), row.no(), row.kind().name(), row.effectiveStatus(now).name(), row.reason(), row.warningNo(), row.startsAt(),
                row.endsAt(), row.createdAt(), acknowledged, appealView, MemberSanctionService.appealDeadline(row));
    }

    /** 제재 ID — 숫자가 아니면 없는 제재(404) */
    private static long parse(String text) {
        if (text == null || text.isBlank() || text.strip().length() > 19) {
            throw new SanctionNotFoundException();
        }
        try {
            return Long.parseLong(text.strip());
        } catch (NumberFormatException e) {
            throw new SanctionNotFoundException();
        }
    }
}
