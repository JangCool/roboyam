package com.community.sanction.service;

import com.community.sanction.dto.response.SanctionActorRefView;
import com.community.sanction.dto.response.SanctionAppealRefView;
import com.community.sanction.dto.response.SanctionLineView;
import com.community.sanction.dto.response.SanctionNextView;
import com.community.sanction.dto.response.SanctionOptionView;
import com.community.sanction.dto.response.SanctionPreviewView;
import com.community.sanction.dto.response.SanctionReportRefView;
import com.community.sanction.dto.response.SanctionUserRefView;
import com.community.sanction.exception.SanctionTargetNotFoundException;
import com.community.sanction.repository.master.MemberSanctionStore.MemberRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제재 줄·현황의 응답 모양(admin-web {@code useSanctionApi.types.ts}) — 제재 화면·이의제기 화면·신고 상세 작성자 카드·회원 상세가 같이 쓴다. 읽기만(지연 만료는 쓰지 않고
 * {@link SanctionRow#effectiveStatus} 로 지금 상태를 보인다). 현황(preview)은 회원 하나 범위 쿼리 한 번.
 */
@Component
@Transactional(readOnly = true)
public class SanctionViews {

    private final MemberSanctionService sanctions;

    public SanctionViews(MemberSanctionService sanctions) {
        this.sanctions = sanctions;
    }

    /**
     * 회원의 제재 현황 — 없는 회원·탈퇴(진행 중 포함)는 404 {@code SANCTION_TARGET_NOT_FOUND}.
     */
    public SanctionPreviewView preview(long userId) {
        MemberRow member = sanctions.member(userId).filter(m -> !m.gone()).orElseThrow(SanctionTargetNotFoundException::new);
        return preview(member, sanctions.allOf(userId));
    }

    /** 현황 — 신고 상세 작성자 카드(제재할 수 없는 회원이면 null) */
    public SanctionPreviewView previewOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        return sanctions.member(userId).filter(m -> !m.gone()).map(m -> preview(m, sanctions.allOf(userId))).orElse(null);
    }

    private SanctionPreviewView preview(MemberRow member, List<SanctionRow> rows) {
        Instant now = sanctions.now();
        SanctionLadder.Standing standing = SanctionLadder.standing(rows, now);
        SanctionUserRefView user = new SanctionUserRefView(Long.toString(member.id()), nz(member.nickname()), member.gradeKey(), member.status());
        List<SanctionOptionView> options = standing.options().stream().map(o -> new SanctionOptionView(o.kind().name(), o.rank(), o.state(), o.requiresBan())).toList();
        SanctionNextView next = standing.next() == null ? null : new SanctionNextView(standing.next().name(), standing.nextRank());
        List<SanctionLineView> history = rows.stream().limit(MemberSanctionService.HISTORY_MAX).map(r -> line(r, now)).toList();
        return new SanctionPreviewView(user, standing.warningCount(), standing.blockedReason(), next, options,
                standing.running() == null ? null : line(standing.running(), now), history);
    }

    /** 그 회원의 이력(최근 것이 위, 최대 30개) */
    public List<SanctionLineView> history(long userId) {
        Instant now = sanctions.now();
        return sanctions.allOf(userId).stream().limit(MemberSanctionService.HISTORY_MAX).map(r -> line(r, now)).toList();
    }

    public SanctionLineView line(SanctionRow row) {
        return line(row, sanctions.now());
    }

    public static SanctionLineView line(SanctionRow r, Instant now) {
        SanctionReportRefView reportCase = r.reportCaseId() == null ? null : new SanctionReportRefView(Long.toString(r.reportCaseId()),
                r.reportCaseNo() == null ? 0 : r.reportCaseNo());
        SanctionAppealRefView appeal = r.appealId() == null ? null : new SanctionAppealRefView(Long.toString(r.appealId()), r.appealNo(), r.appealStatus().name());
        return new SanctionLineView(Long.toString(r.id()), r.no(), r.kind().name(), r.effectiveStatus(now).name(), r.kind().rank(r.warningNo()), r.warningNo(), r.skipped(),
                user(r), r.startsAt(), r.endsAt(), r.reason(), actor(r.issuedBy(), r.issuedByName()), r.createdAt(), reportCase, actor(r.liftedBy(), r.liftedByName()),
                r.liftedAt(), r.liftReason(), r.voidedAt(), r.effectiveEndedAt(now), appeal, MemberSanctionService.appealDeadline(r));
    }

    public static SanctionUserRefView user(SanctionRow r) {
        return new SanctionUserRefView(Long.toString(r.userId()), nz(r.nickname()), r.gradeKey(), r.userStatus());
    }

    public static SanctionActorRefView actor(Long id, String name) {
        return id == null ? null : new SanctionActorRefView(Long.toString(id), name);
    }

    private static String nz(String text) {
        return text == null ? "" : text;
    }
}
