package com.community.title.service;

import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleSource;
import com.community.title.entity.TitleTier;
import com.community.title.exception.TitleException;
import com.community.title.repository.master.MemberTitleRepository;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칭호 주기·올리기·회수(member-title-contract 「규칙」·「구현 결정」 1·8) — 운영자 수여, 신청 승인, 자동 판정 배치, 명예 작가 즉시 지급이 모두 여기로 들어온다. 부르는 쪽 트랜잭션에 참여한다.
 *
 * <ul>
 *   <li>AI 회원·운영팀 계정은 늘 제외(409 {@code TITLE_MEMBER_EXCLUDED}), 탈퇴(진행 중 포함)·없는 회원은 404 {@code TITLE_MEMBER_NOT_FOUND}.</li>
 *   <li>같은 칭호·같은 기간의 살아 있는 줄은 하나(409 {@code TITLE_ALREADY_GRANTED} — 유일 색인이 마지막으로 막는다).</li>
 *   <li>이달의 작가: 기간 {@code YYYY-MM} 필수, 칭호 행을 잠근 뒤 그 달 인원이 설정 최대면 409 {@code TITLE_MONTHLY_FULL}. 수여한 같은 트랜잭션에서 이달의 작가 횟수가 설정 이상이면
 *       명예 작가를 바로 준다(배치를 기다리지 않는다). 올해의 작가: 기간 {@code YYYY} 필수, 그해 이달의 작가를 받은 적이 없으면 409 {@code TITLE_YEARLY_NOT_ELIGIBLE}.</li>
 *   <li>받으면(단계가 오르면) 회원 알림 {@code TITLE_GRANTED} — 세이브포인트 안이라 실패해도 지급을 깨지 않는다. 축하 창은 받은 줄의 {@code seen_at} 이 비어 있는 것.</li>
 *   <li>자동 판정은 운영자가 회수한 칭호를 다시 주지 않는다(운영자 결정이 이긴다).</li>
 * </ul>
 */
@Service
public class TitleGrantService {

    private final TitleRepository titles;
    private final MemberTitleRepository memberTitles;
    private final TitleQueries queries;
    private final TitleSettingsReader settings;
    private final TitleEligibility eligibility;
    private final UserRepository users;
    private final MemberNotificationPublisher notices;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public TitleGrantService(TitleRepository titles, MemberTitleRepository memberTitles, TitleQueries queries, TitleSettingsReader settings, TitleEligibility eligibility,
                             UserRepository users, MemberNotificationPublisher notices, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.titles = titles;
        this.memberTitles = memberTitles;
        this.queries = queries;
        this.settings = settings;
        this.eligibility = eligibility;
        this.users = users;
        this.notices = notices;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 수여 결과 — 이달의 작가 수여로 명예 작가가 따라왔으면 {@code honorary}(그 밖 null) */
    public record Granted(TitleEntity title, MemberTitleEntity row, MemberTitleEntity honorary) {
    }

    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 운영자 수여 — 칭호 행을 잠그고 기간·단계·회원·인원을 검사한 뒤 넣는다.
     *
     * @param period 이달의 작가 {@code YYYY-MM}·올해의 작가 {@code YYYY}, 그 밖 칭호는 비워야 한다(400 {@code fields.period})
     * @param tier   단계 칭호의 단계(없으면 동), 단계 없는 칭호는 비워야 한다(400 {@code fields.tier})
     */
    @Transactional
    public Granted award(String key, long userId, String period, String tier, long operatorId, String reason) {
        TitleEntity title = titles.findWithLockByTitleKey(key == null ? "" : key).orElseThrow(TitleException::notFound);
        String p = period(title, period);
        TitleTier t = tier(title, tier);
        requireReceivable(userId);
        Instant now = now();
        if (BuiltinTitles.MONTHLY_WRITER.equals(title.getTitleKey())
                && memberTitles.countByTitleIdAndPeriodAndRevokedAtIsNull(title.getId(), p) >= settings.current().monthlyMax()) {
            throw TitleException.monthlyFull();
        }
        if (BuiltinTitles.YEARLY_WRITER.equals(title.getTitleKey()) && !wasMonthlyWriterIn(userId, p)) {
            throw TitleException.yearlyNotEligible();
        }
        MemberTitleEntity row = insert(title, userId, t, p, TitleSource.AWARD, null, operatorId, null, reason, now);
        MemberTitleEntity honorary = BuiltinTitles.MONTHLY_WRITER.equals(title.getTitleKey()) ? grantHonoraryIfDue(userId, settings.current().honoraryCount(), now) : null;
        return new Granted(title, row, honorary);
    }

    /** 회원이 칭호를 받을 수 있나 — 없는·탈퇴(진행 중 포함) 404, AI 회원·운영팀 계정 409 */
    public void requireReceivable(long userId) {
        UserEntity user = users.findById(userId).orElseThrow(TitleException::memberNotFound);
        if (user.isGone() || user.getStatus() == UserStatus.WITHDRAWING) {
            throw TitleException.memberNotFound();
        }
        if (queries.excluded(userId)) {
            throw TitleException.memberExcluded();
        }
    }

    private boolean wasMonthlyWriterIn(long userId, String year) {
        TitleEntity monthly = titles.findByTitleKey(BuiltinTitles.MONTHLY_WRITER).orElse(null);
        return monthly != null && memberTitles.findByUserIdAndTitleIdAndRevokedAtIsNull(userId, monthly.getId()).stream()
                .anyMatch(r -> r.getPeriod() != null && r.getPeriod().startsWith(year + "-"));
    }

    /** 기간 검사·정리 */
    static String period(TitleEntity title, String raw) {
        String p = raw == null ? "" : raw.strip();
        if (BuiltinTitles.MONTHLY_WRITER.equals(title.getTitleKey())) {
            if (!p.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
                throw new ValidationException(Map.of("period", new MessageRef("title.period.month")));
            }
            return p;
        }
        if (BuiltinTitles.YEARLY_WRITER.equals(title.getTitleKey())) {
            if (!p.matches("[0-9]{4}")) {
                throw new ValidationException(Map.of("period", new MessageRef("title.period.year")));
            }
            return p;
        }
        if (!p.isEmpty()) {
            throw new ValidationException(Map.of("period", new MessageRef("title.period.none")));
        }
        return "";
    }

    /** 단계 검사 — 단계 칭호는 없으면 동, 단계 없는 칭호는 비워야 한다 */
    static TitleTier tier(TitleEntity title, String raw) {
        if (raw == null || raw.isBlank()) {
            return title.isTiered() ? TitleTier.BRONZE : null;
        }
        TitleTier t = TitleTier.parse(raw).orElse(null);
        if (t == null || !title.isTiered()) {
            throw new ValidationException(Map.of("tier", new MessageRef(title.isTiered() ? "title.tier.invalid" : "title.tier.none")));
        }
        return t;
    }

    /**
     * 받은 줄 하나를 넣고 알린다(검사는 부르는 쪽). 같은 칭호·기간의 살아 있는 줄이 있으면 409 {@code TITLE_ALREADY_GRANTED}.
     */
    public MemberTitleEntity insert(TitleEntity title, long userId, TitleTier tier, String period, TitleSource source, Long applicationId, Long operatorId, Long memberId,
                                    String reason, Instant now) {
        String p = period == null ? "" : period;
        if (memberTitles.findByUserIdAndTitleIdAndPeriodAndRevokedAtIsNull(userId, title.getId(), p).isPresent()) {
            throw TitleException.alreadyGranted();
        }
        MemberTitleEntity row = MemberTitleEntity.grant(ids.nextId(), userId, title.getId(), tier, p, source, applicationId, operatorId, memberId, reason, now);
        try {
            memberTitles.persist(row);
            memberTitles.flush(); // 살아 있는 줄은 하나 — 동시 지급의 유일 색인 위반은 여기서
        } catch (DuplicateKeyException e) {
            throw TitleException.alreadyGranted();
        }
        notices.titleGranted(userId, title.getTitleKey(), TitleLabels.label(title, p), tier == null ? null : tier.name());
        return row;
    }

    /**
     * 명예 작가 — 이달의 작가 살아 있는 줄이 {@code honoraryCount} 이상이고 명예 작가가 켜져 있고 아직 없으면 바로 준다(자동, 기간 없음). 준 줄(아니면 null).
     */
    @Transactional
    public MemberTitleEntity grantHonoraryIfDue(long userId, int honoraryCount, Instant now) {
        TitleEntity honorary = titles.findByTitleKey(BuiltinTitles.HONORARY_WRITER).filter(TitleEntity::isEnabled).orElse(null);
        TitleEntity monthly = titles.findByTitleKey(BuiltinTitles.MONTHLY_WRITER).orElse(null);
        if (honorary == null || monthly == null || queries.excluded(userId)) {
            return null;
        }
        if (memberTitles.findByUserIdAndTitleIdAndPeriodAndRevokedAtIsNull(userId, honorary.getId(), "").isPresent()) {
            return null;
        }
        if (memberTitles.countByUserIdAndTitleIdAndRevokedAtIsNull(userId, monthly.getId()) < honoraryCount) {
            return null;
        }
        return insert(honorary, userId, null, "", TitleSource.AUTO, null, null, null, null, now);
    }

    /**
     * 자동 판정 한 건(배치) — 조건을 모두 채웠으면 주고, 단계 칭호는 센 수에 맞는 단계로 올린다(낮추지 않는다). 운영자가 회수한 칭호는 다시 주지 않는다. 바뀌었으면 true.
     *
     * @param count 단계·목표를 정하는 센 수(배치 후보 질의가 센 값)
     */
    @Transactional
    public boolean autoGrant(TitleEntity title, long userId, long count) {
        if (!title.isEnabled() || queries.excluded(userId)) {
            return false;
        }
        if (memberTitles.existsByUserIdAndTitleIdAndRevokedAtIsNotNullAndRevokedByIsNotNull(userId, title.getId())) {
            return false;
        }
        Instant now = now();
        if (BuiltinTitles.HONORARY_WRITER.equals(title.getTitleKey())) {
            return grantHonoraryIfDue(userId, settings.current().honoraryCount(), now) != null;
        }
        if (!eligibility.allMet(title, userId)) {
            return false;
        }
        TitleTier tier = title.tierFor(count);
        if (title.isTiered() && tier == null) {
            return false;
        }
        MemberTitleEntity existing = memberTitles.findByUserIdAndTitleIdAndPeriodAndRevokedAtIsNull(userId, title.getId(), "").orElse(null);
        if (existing != null) {
            if (tier == null || (existing.getTier() != null && existing.getTier().ordinal() >= tier.ordinal())) {
                return false;
            }
            existing.raiseTier(tier, now);
            notices.titleGranted(userId, title.getTitleKey(), TitleLabels.label(title, ""), tier.name());
            return true;
        }
        try {
            insert(title, userId, tier, "", TitleSource.AUTO, null, null, null, null, now);
        } catch (TitleException alreadyThere) {
            return false; // 다른 서버가 먼저 줬다
        }
        return true;
    }

    /** 회수 — 그 칭호의 살아 있는 줄만(아니면 409 {@code TITLE_GRANT_NOT_ACTIVE}), 줄은 남긴다 */
    @Transactional
    public MemberTitleEntity revoke(String key, long grantId, long operatorId, String reason) {
        TitleEntity title = titles.findByTitleKey(key == null ? "" : key).orElseThrow(TitleException::notFound);
        MemberTitleEntity row = memberTitles.findWithLockById(grantId).filter(r -> r.getTitleId().equals(title.getId())).orElseThrow(TitleException::grantNotFound);
        if (!row.isActive()) {
            throw TitleException.grantNotActive();
        }
        row.revoke(operatorId, reason, now());
        return row;
    }
}
