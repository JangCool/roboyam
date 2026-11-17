package com.community.trial.service;

import com.community.addressbook.dto.SavedAddress;
import com.community.addressbook.service.AddressBookService;
import com.community.identity.service.IdentityVerificationService;
import com.community.restriction.entity.RestrictedFeature;
import com.community.restriction.exception.MemberRestrictedException;
import com.community.restriction.service.MemberRestrictionService;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.repository.master.TitleQueries;
import com.community.title.service.BuiltinTitles;
import com.community.title.service.TitleGrantService;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialConsent;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialPhotoEntity;
import com.community.trial.entity.TrialQuota;
import com.community.trial.entity.TrialStatus;
import com.community.trial.exception.TrialException;
import com.community.trial.repository.master.TrialApplicationRepository;
import com.community.trial.repository.master.TrialDamageReportRepository;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialPhotoRepository;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.repository.master.TrialRepository;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체험단 공개·회원 쪽(trial-group-contract 「모집 · 신청 · 뽑기」·「구현 결정」 9) — 목록·상세(끝난 모집은 사용기 모아 보기)·신청·신청 취소·내 체험단·받음·파손 이의.
 *
 * <p><b>신청 검사 차례</b>: 본인인증(403 {@code IDENTITY_REQUIRED}) → AI 회원·운영팀 계정(409 {@code TRIAL_APPLICANT_EXCLUDED}) → 신청 금지 기능 제한(403 {@code MEMBER_RESTRICTED})
 * → 모집 행 잠금 → 모집 중·마감 전(409 {@code TRIAL_NOT_RECRUITING}) → 리뷰어 먼저 신청 창(금 48·은 24시간 전부터, 동·칭호 없음은 일반 시작 — 409 {@code TRIAL_APPLY_NOT_OPEN})
 * → 이미 신청(409 {@code TRIAL_ALREADY_APPLIED}) → 받을 곳 = 내 배송지(400 {@code fields.addressId}) → 동의 항목(보내는 쪽·형태로 정해진 것과 같아야 — 400 {@code fields.consents}).
 * 리뷰어 칭호(살아 있는 줄)가 있으면 리뷰어 몫으로 신청한다. 배송지는 암호문 사본으로 둔다.
 */
@Service
public class TrialMemberService {

    private final TrialRepository trials;
    private final TrialApplicationRepository applications;
    private final TrialParticipantRepository participants;
    private final TrialDamageReportRepository damages;
    private final TrialPhotoRepository photos;
    private final TrialQueries queries;
    private final TrialViewer viewer;
    private final IdentityVerificationService identity;
    private final MemberRestrictionService restrictions;
    private final TitleGrantService titles;
    private final TitleQueries titleQueries;
    private final AddressBookService addresses;
    private final DataCipher cipher;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public TrialMemberService(TrialRepository trials, TrialApplicationRepository applications, TrialParticipantRepository participants,
                              TrialDamageReportRepository damages, TrialPhotoRepository photos, TrialQueries queries, TrialViewer viewer,
                              IdentityVerificationService identity, MemberRestrictionService restrictions, TitleGrantService titles, TitleQueries titleQueries,
                              AddressBookService addresses, DataCipher cipher, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.trials = trials;
        this.applications = applications;
        this.participants = participants;
        this.damages = damages;
        this.photos = photos;
        this.queries = queries;
        this.viewer = viewer;
        this.identity = identity;
        this.restrictions = restrictions;
        this.titles = titles;
        this.titleQueries = titleQueries;
        this.addresses = addresses;
        this.cipher = cipher;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // ── 공개 ─────────────────────────────────────────────────────────────

    /** 목록 — {@code status} RECRUITING(기본 — 뽑는 중 포함) · RUNNING · CLOSED */
    @Transactional(readOnly = true)
    public List<TrialViews.TrialCard> list(String statusRaw) {
        String s = statusRaw == null || statusRaw.isBlank() ? "RECRUITING" : statusRaw.strip().toUpperCase(java.util.Locale.ROOT);
        List<TrialStatus> statuses = switch (s) {
            case "RECRUITING" -> List.of(TrialStatus.RECRUITING, TrialStatus.SELECTING);
            case "RUNNING" -> List.of(TrialStatus.RUNNING);
            case "CLOSED" -> List.of(TrialStatus.CLOSED);
            default -> throw ValidationException.of("status", new MessageRef("trial.value.invalid"));
        };
        Instant now = now();
        List<TrialEntity> rows = queries.byStatuses(statuses);
        Map<Long, Long> counts = queries.applicantCounts(rows.stream().map(TrialEntity::getId).toList());
        return rows.stream().map(t -> viewer.card(t, counts.getOrDefault(t.getId(), 0L), now)).toList();
    }

    /** 상세 — 준비·취소한 모집은 없는 모집(404) */
    @Transactional(readOnly = true)
    public TrialViews.TrialPublicDetail detail(String idText, Long viewerId) {
        TrialEntity t = publicTrial(idText);
        Instant now = now();
        long count = queries.applicantCounts(List.of(t.getId())).getOrDefault(t.getId(), 0L);
        TrialViews.Mine mine = viewerId == null ? null : mine(t, viewerId, now);
        List<TrialViews.Review> reviews = t.getStatus() == TrialStatus.CLOSED ? viewer.reviews(t, now) : List.of();
        return new TrialViews.TrialPublicDetail(viewer.card(t, count, now), t.getDescription(), t.getReviewDays(), t.getReturnDays(), t.getShipper().name(),
                t.getReviewerShare(), t.getReviewerGoldHours(), t.getReviewerSilverHours(), TrialViewer.requiredConsents(t).stream().map(Enum::name).toList(),
                t.lend() ? t.getProductPrice() : null, mine, reviews);
    }

    private TrialEntity publicTrial(String idText) {
        TrialEntity t = trials.findById(TrialService.id(idText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        if (t.getStatus() == TrialStatus.DRAFT || t.getStatus() == TrialStatus.CANCELLED) {
            throw TrialException.notFound();
        }
        return t;
    }

    /** 보는 회원의 신청 상태 — 신청할 수 없으면 까닭 코드({@code blockedReason}: IDENTITY_REQUIRED · EXCLUDED · RESTRICTED · NOT_RECRUITING · NOT_OPEN · APPLIED) */
    private TrialViews.Mine mine(TrialEntity t, long userId, Instant now) {
        TrialApplicationEntity a = applications.findFirstByTrialIdAndUserIdAndStatusNot(t.getId(), userId, TrialApplicationStatus.CANCELLED).orElse(null);
        Optional<MemberTitleEntity> reviewer = titles.held(BuiltinTitles.REVIEWER, userId);
        String tier = reviewer.map(r -> r.getTier() == null ? null : r.getTier().name()).orElse(null);
        Instant openAt = t.applyOpenFor(tier);
        String blocked = null;
        if (a != null) {
            blocked = "APPLIED";
        } else if (!identityVerified(userId)) {
            blocked = "IDENTITY_REQUIRED";
        } else if (titleQueries.excluded(userId)) {
            blocked = "EXCLUDED";
        } else if (restrictions.blocking(userId, RestrictedFeature.TRIAL_APPLY).isPresent()) {
            blocked = "RESTRICTED";
        } else if (t.getStatus() != TrialStatus.RECRUITING || !now.isBefore(t.getApplyEndAt())) {
            blocked = "NOT_RECRUITING";
        } else if (now.isBefore(openAt)) {
            blocked = "NOT_OPEN";
        }
        return new TrialViews.Mine(a == null ? null : Long.toString(a.getId()), a == null ? null : a.getStatus().name(), blocked == null,
                a != null && a.getStatus() == TrialApplicationStatus.APPLIED && t.getStatus() == TrialStatus.RECRUITING, openAt,
                reviewer.isPresent() ? TrialQuota.REVIEWER.name() : TrialQuota.GENERAL.name(), tier, blocked);
    }

    private boolean identityVerified(long userId) {
        try {
            identity.requireVerified(userId);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ── 신청 ─────────────────────────────────────────────────────────────

    @Transactional
    public TrialViews.Mine apply(long userId, String trialIdText, TrialViews.ApplyRequest body) {
        identity.requireVerified(userId); // 본인인증 회원만
        if (titleQueries.excluded(userId)) {
            throw TrialException.applicantExcluded(); // AI 회원·운영팀 계정 불가
        }
        restrictions.assertAllowed(userId, RestrictedFeature.TRIAL_APPLY); // 신청 금지(미반납·기한 넘김·파손)
        TrialEntity t = trials.findWithLockById(TrialService.id(trialIdText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        Instant now = now();
        if (t.getStatus() == TrialStatus.DRAFT || t.getStatus() == TrialStatus.CANCELLED) {
            throw TrialException.notFound();
        }
        if (t.getStatus() != TrialStatus.RECRUITING || !now.isBefore(t.getApplyEndAt())) {
            throw TrialException.notRecruiting();
        }
        Optional<MemberTitleEntity> reviewer = titles.held(BuiltinTitles.REVIEWER, userId);
        String tier = reviewer.map(r -> r.getTier() == null ? null : r.getTier().name()).orElse(null);
        if (now.isBefore(t.applyOpenFor(tier))) {
            throw TrialException.applyNotOpen(); // 리뷰어 금 48·은 24시간 먼저 — 그 밖은 일반 시작부터
        }
        if (applications.findFirstByTrialIdAndUserIdAndStatusNot(t.getId(), userId, TrialApplicationStatus.CANCELLED).isPresent()) {
            throw TrialException.alreadyApplied();
        }
        Long addressId = TrialService.parseId(body == null ? null : body.addressId());
        SavedAddress address = addressId == null ? null : addresses.forOrder(userId, addressId).orElse(null);
        if (address == null) {
            throw ValidationException.of("addressId", new MessageRef("trial.address.required"));
        }
        Set<String> given = new LinkedHashSet<>();
        for (String c : body.consents() == null ? List.<String>of() : body.consents()) {
            given.add(c == null ? "" : c.strip().toUpperCase(java.util.Locale.ROOT));
        }
        List<String> required = TrialViewer.requiredConsents(t).stream().map(TrialConsent::name).toList();
        if (!given.equals(new LinkedHashSet<>(required))) {
            throw ValidationException.of("consents", new MessageRef("trial.consents.required"));
        }
        TrialApplicationEntity.AddressCopy copy = new TrialApplicationEntity.AddressCopy(cipher.encrypt(TrialPii.RECIPIENT, address.recipient()),
                cipher.encrypt(TrialPii.PHONE, address.phone()), address.postcode(), cipher.encrypt(TrialPii.ADDRESS1, address.address1()),
                enc(TrialPii.ADDRESS2, address.address2()), enc(TrialPii.MEMO, address.memo()));
        TrialApplicationEntity a = TrialApplicationEntity.apply(ids.nextId(), t.getId(), userId, reviewer.isPresent() ? TrialQuota.REVIEWER : TrialQuota.GENERAL, tier,
                copy, TrialViewer.consentsJson(required), now);
        try {
            applications.persist(a);
            applications.flush(); // 살아 있는 신청은 하나 — 동시 신청의 유일 색인 위반은 여기서
        } catch (DuplicateKeyException e) {
            throw TrialException.alreadyApplied();
        }
        return mine(t, userId, now);
    }

    private String enc(com.community.zconfiguration.crypto.DataPurpose purpose, String value) {
        return value == null || value.isBlank() ? null : cipher.encrypt(purpose, value.strip());
    }

    /** 신청 취소 — 신청 대기이고 모집 중일 때만(뽑기가 시작되면 409 {@code TRIAL_APPLICATION_CLOSED}) */
    @Transactional
    public void cancel(long userId, String trialIdText) {
        TrialEntity t = trials.findWithLockById(TrialService.id(trialIdText, TrialException::notFound)).orElseThrow(TrialException::notFound);
        TrialApplicationEntity a = applications.findFirstByTrialIdAndUserIdAndStatusNot(t.getId(), userId, TrialApplicationStatus.CANCELLED)
                .orElseThrow(TrialException::applicationNotFound);
        if (a.getStatus() != TrialApplicationStatus.APPLIED || t.getStatus() != TrialStatus.RECRUITING) {
            throw TrialException.applicationClosed();
        }
        a.cancel(now());
    }

    // ── 내 체험단 ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrialViews.MyTrial> mine(long userId) {
        Instant now = now();
        List<TrialApplicationEntity> apps = applications.findTop100ByUserIdOrderByCreatedAtDescIdDesc(userId).stream().filter(TrialApplicationEntity::live).toList();
        Map<Long, TrialEntity> trialById = new HashMap<>();
        trials.findAllById(apps.stream().map(TrialApplicationEntity::getTrialId).toList()).forEach(t -> trialById.put(t.getId(), t));
        Map<Long, Long> counts = queries.applicantCounts(trialById.keySet());
        Map<Long, TrialParticipantEntity> byApp = new HashMap<>();
        participants.findByUserIdOrderByCreatedAtDesc(userId).forEach(p -> byApp.put(p.getApplicationId(), p));
        List<TrialViews.MyTrial> out = new ArrayList<>();
        for (TrialApplicationEntity a : apps) {
            TrialEntity t = trialById.get(a.getTrialId());
            if (t == null || t.getStatus() == TrialStatus.DRAFT) {
                continue;
            }
            TrialParticipantEntity p = byApp.get(a.getId());
            out.add(new TrialViews.MyTrial(Long.toString(a.getId()), a.getStatus().name(), a.getQuota().name(), a.getCreatedAt(),
                    a.getStatus() == TrialApplicationStatus.APPLIED && t.getStatus() == TrialStatus.RECRUITING, viewer.card(t, counts.getOrDefault(t.getId(), 0L), now),
                    p == null ? null : participation(t, p, now)));
        }
        return out;
    }

    private TrialViews.MyParticipation participation(TrialEntity t, TrialParticipantEntity p, Instant now) {
        List<TrialQueries.PhotoMeta> attached = queries.attachedPhotos(t.getId());
        List<TrialViews.MyDamage> ds = damages.findByParticipantIdOrderByCreatedAtDesc(p.getId()).stream().map(d -> new TrialViews.MyDamage(Long.toString(d.getId()),
                d.getDescription(), d.getStatus().name(), d.getObjectionDueAt(), d.getStatus() == TrialDamageStatus.REPORTED && now.isBefore(d.getObjectionDueAt()),
                d.getObjection(), d.getVerdict() == null ? null : d.getVerdict().name(), d.getDecisionNote(),
                viewer.photos(attached.stream().filter(x -> d.getId().equals(x.damageReportId())).toList(), TrialViewer.Audience.MEMBER), d.getCreatedAt())).toList();
        boolean canWrite = !p.cancelled() && p.getReviewPostId() == null && p.getUnreturnedAt() == null
                && (t.getStatus() == TrialStatus.RUNNING || t.getStatus() == TrialStatus.CLOSED);
        return new TrialViews.MyParticipation(Long.toString(p.getId()), p.getCarrier(), p.getTrackingNo(), p.getShippedAt(), p.getReceivedAt(), p.getReceivedBy(),
                p.shipped() && !p.received(), p.getReviewDueAt(), p.getReviewPostId() == null ? null : Long.toString(p.getReviewPostId()), canWrite,
                viewer.board(t.getReviewBoardId()), p.getReturnDueAt(), p.getReturnedAt(), p.getKept(), p.getUnreturnedAt(), ds, p.getCancelledAt());
    }

    /** 글쓰기 「뽑힌 체험단 사용기로 쓰기」 후보 — 연결 전 줄(게시판 slug 를 주면 그 게시판 것만) */
    @Transactional(readOnly = true)
    public List<TrialViews.Writable> writable(long userId, String boardSlug) {
        List<TrialViews.Writable> out = new ArrayList<>();
        for (TrialParticipantEntity p : participants.findByUserIdOrderByCreatedAtDesc(userId)) {
            if (p.cancelled() || p.getReviewPostId() != null || p.getUnreturnedAt() != null) {
                continue;
            }
            TrialEntity t = trials.findById(p.getTrialId()).orElse(null);
            if (t == null || (t.getStatus() != TrialStatus.RUNNING && t.getStatus() != TrialStatus.CLOSED)) {
                continue;
            }
            TrialViews.BoardRef board = viewer.board(t.getReviewBoardId());
            if (board == null || (boardSlug != null && !boardSlug.isBlank() && !boardSlug.equals(board.slug()))) {
                continue;
            }
            out.add(new TrialViews.Writable(Long.toString(p.getId()), Long.toString(t.getId()), t.getTitle(), board));
        }
        return out;
    }

    /** 받음 — 내 줄, 송장이 있고 아직 받지 않았을 때 */
    @Transactional
    public void received(long userId, String participantIdText) {
        TrialParticipantEntity p = participants.findWithLockById(TrialService.id(participantIdText, TrialException::participantNotFound))
                .filter(x -> x.getUserId() == userId).orElseThrow(TrialException::participantNotFound);
        if (!p.shipped()) {
            throw TrialException.notShipped();
        }
        if (p.received()) {
            throw TrialException.alreadyReceived();
        }
        TrialEntity t = trials.findById(p.getTrialId()).orElseThrow(TrialException::notFound);
        p.receive(TrialParticipantEntity.RECEIVED_BY_MEMBER, now(), t.getReviewDays(), t.getReturnDays());
    }

    /** 파손 이의 — 내 줄의 신고, 이의 기한 안 · 한 번 */
    @Transactional
    public void objection(long userId, String reportIdText, TrialViews.ObjectionRequest body) {
        TrialDamageReportEntity d = damages.findWithLockById(TrialService.id(reportIdText, TrialException::damageNotFound)).orElseThrow(TrialException::damageNotFound);
        TrialParticipantEntity p = participants.findById(d.getParticipantId()).filter(x -> x.getUserId() == userId).orElseThrow(TrialException::damageNotFound);
        String text = body == null || body.text() == null ? "" : body.text().strip();
        if (text.isEmpty() || text.length() > 1000) {
            throw ValidationException.of("text", new MessageRef(text.isEmpty() ? "trial.field.required" : "trial.text.length", "1000"));
        }
        Instant now = now();
        if (d.getStatus() != TrialDamageStatus.REPORTED || !now.isBefore(d.getObjectionDueAt())) {
            throw TrialException.objectionClosed();
        }
        d.object(text, now);
        p.getId(); // 내 줄인지 위에서 확인했다
    }

    /** 내 파손 신고 사진 보기(점검 D3 — {@code GET /api/me/trials/photos/{id}}) — 내 뽑힌 줄의 파손 신고에 붙은 사진만(그 밖 404), 지운 사진 410 */
    @Transactional(readOnly = true)
    public TrialViews.PhotoBytes photo(long userId, String photoIdText) {
        TrialPhotoEntity p = photos.findById(TrialService.id(photoIdText, TrialException::photoNotFound)).orElseThrow(TrialException::photoNotFound);
        if (p.getDamageReportId() == null || p.getParticipantId() == null
                || participants.findById(p.getParticipantId()).filter(x -> x.getUserId() == userId).isEmpty()) {
            throw TrialException.photoNotFound();
        }
        if (p.purged()) {
            throw TrialException.photoPurged();
        }
        return new TrialViews.PhotoBytes(cipher.decryptBytes(TrialPii.PHOTO, p.getContent()), p.getContentType());
    }

    /** 이 회원이 체험단 신청을 막혀 있나(화면 안내) — 막혔으면 예외 */
    public Optional<MemberRestrictedException> blocked(long userId) {
        return restrictions.blocking(userId, RestrictedFeature.TRIAL_APPLY);
    }
}
