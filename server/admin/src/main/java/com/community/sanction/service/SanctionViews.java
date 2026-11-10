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
        return memberPreview(member, sanctions.allOf(userId));
    }

    /** 현황 — 신고 상세 작성자 카드(제재할 수 없는 회원이면 null). 회원 대상 현황이라 봉인 제재 줄은 뺀다({@link #memberPreview}) */
    public SanctionPreviewView previewOrNull(Long userId) {
        if (userId == null) {
            return null;
        }
        return sanctions.member(userId).filter(m -> !m.gone()).map(m -> memberPreview(m, sanctions.allOf(userId))).orElse(null);
    }

    /**
     * 「이 글/댓글 작성자」의 현황(익명 — V184, anonymous-board-contract 정책 9) — 모양은 {@link #preview(long)} 와 같고 대상과 이력·진행 중 줄의 대상이 모두 봉인이다
     * (이력 줄의 닉네임으로 신원이 이어지지 않게). 이력은 봉인 아닌 줄도 모두 싣는다(대상이 봉인이라), {@code sealedCount} 는 0.
     */
    public SanctionPreviewView previewSealed(long userId) {
        MemberRow member = sanctions.member(userId).filter(m -> !m.gone()).orElseThrow(SanctionTargetNotFoundException::new);
        List<SanctionRow> rows = sanctions.allOf(member.id());
        Instant now = sanctions.now();
        SanctionLadder.Standing standing = SanctionLadder.standing(rows, now);
        List<SanctionLineView> history = rows.stream().limit(MemberSanctionService.HISTORY_MAX).map(r -> sealed(line(r, now))).toList();
        return new SanctionPreviewView(SanctionUserRefView.SEALED, standing.warningCount(), standing.blockedReason(), next(standing), options(standing),
                standing.running() == null ? null : sealed(line(standing.running(), now)), history, 0);
    }

    /** 봉인된 현황 — 제재할 수 없는 회원(없음·탈퇴)이면 null(신고 상세 작성자 카드) */
    public SanctionPreviewView previewSealedOrNull(long userId) {
        return sanctions.member(userId).filter(m -> !m.gone()).isPresent() ? previewSealed(userId) : null;
    }

    /** 대상을 봉인한 줄 */
    public static SanctionLineView sealed(SanctionLineView l) {
        return l == null ? null : new SanctionLineView(l.id(), l.no(), l.kind(), l.status(), l.rank(), l.warningNo(), l.skipped(), SanctionUserRefView.SEALED, l.startsAt(),
                l.endsAt(), l.reason(), l.issuedBy(), l.createdAt(), l.reportCase(), l.liftedBy(), l.liftedAt(), l.liftReason(), l.voidedAt(), l.endedAt(), l.appeal(),
                l.appealDeadline(), l.sealed());
    }

    /**
     * 회원 대상 현황(회원 상세 · 신고 상세 작성자 카드 — anonymous-board-contract 2026-11-11). 셈(경고 수 · 다음 단계 · 선택지 · 막힘 · 진행 중 정지)은 봉인 제재도 그대로 센다.
     * 이력(최근 30개)에서는 봉인 제재 줄을 빼고, {@code sealedCount} 는 경고 수에 들어간 봉인 경고 수. 진행 중 정지가 봉인 제재면 {@code active.sealed true} 에
     * 계기 신고({@code reportCase})를 비운다(끝나는 날·해제 ID 는 그대로 — 「정지 풀기」는 그 제재를 해제한다).
     */
    private SanctionPreviewView memberPreview(MemberRow member, List<SanctionRow> rows) {
        Instant now = sanctions.now();
        SanctionLadder.Standing standing = SanctionLadder.standing(rows, now);
        SanctionUserRefView user = new SanctionUserRefView(Long.toString(member.id()), nz(member.nickname()), member.gradeKey(), member.status());
        List<SanctionLineView> history = rows.stream().limit(MemberSanctionService.HISTORY_MAX).filter(r -> !r.sealed()).map(r -> line(r, now)).toList();
        long sealedWarnings = rows.stream().filter(r -> r.valid() && r.kind().isWarning() && r.sealed()).count();
        SanctionLineView active = standing.running() == null ? null : line(standing.running(), now);
        if (active != null && active.sealed()) {
            active = withoutReportCase(active);
        }
        return new SanctionPreviewView(user, standing.warningCount(), standing.blockedReason(), next(standing), options(standing), active, history,
                (int) Math.min(sealedWarnings, standing.warningCount()));
    }

    private static SanctionNextView next(SanctionLadder.Standing standing) {
        return standing.next() == null ? null : new SanctionNextView(standing.next().name(), standing.nextRank());
    }

    private static List<SanctionOptionView> options(SanctionLadder.Standing standing) {
        return standing.options().stream().map(o -> new SanctionOptionView(o.kind().name(), o.rank(), o.state(), o.requiresBan())).toList();
    }

    /** 계기 신고를 비운 줄(회원 화면의 봉인 진행 중 정지 — 신고 사건으로 익명 글과 이어지지 않게) */
    private static SanctionLineView withoutReportCase(SanctionLineView l) {
        return new SanctionLineView(l.id(), l.no(), l.kind(), l.status(), l.rank(), l.warningNo(), l.skipped(), l.user(), l.startsAt(), l.endsAt(), l.reason(), l.issuedBy(),
                l.createdAt(), null, l.liftedBy(), l.liftedAt(), l.liftReason(), l.voidedAt(), l.endedAt(), l.appeal(), l.appealDeadline(), l.sealed());
    }

    /**
     * 그 회원의 이력(최근 것이 위, 최대 30개) — 회원 단위 이력(이의제기 상세의 회원 이력)이라 봉인 제재 줄은 뺀다(anonymous-board-contract 2026-11-11). 셈에는 쓰지 않는다.
     */
    public List<SanctionLineView> history(long userId) {
        Instant now = sanctions.now();
        return sanctions.allOf(userId).stream().filter(r -> !r.sealed()).limit(MemberSanctionService.HISTORY_MAX).map(r -> line(r, now)).toList();
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
                r.liftedAt(), r.liftReason(), r.voidedAt(), r.effectiveEndedAt(now), appeal, MemberSanctionService.appealDeadline(r), r.sealed());
    }

    public static SanctionUserRefView user(SanctionRow r) {
        if (r.sealed()) {
            return SanctionUserRefView.SEALED; // 익명 글·댓글 작성자로 건 제재(V184)
        }
        return new SanctionUserRefView(Long.toString(r.userId()), nz(r.nickname()), r.gradeKey(), r.userStatus());
    }

    public static SanctionActorRefView actor(Long id, String name) {
        return id == null ? null : new SanctionActorRefView(Long.toString(id), name);
    }

    private static String nz(String text) {
        return text == null ? "" : text;
    }
}
