package com.community.emoticon.service;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore.AcquisitionRow;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsRow;
import com.community.operator.service.OperatorMemberAccountService;
import com.community.point.service.PointService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 무료 팩 받기 · 목록에서 빼기 · 내 로보티콘(계약 가정 6·11, S1).
 *
 * <p><b>받기</b>({@link #acquire}) — 한 트랜잭션: 팩 행을 잠근다(같은 팩의 받기·심사 처리가 줄을 선다) → 없거나 상점에 나오지 않은 팩 {@code 404} → 판매 중단 {@code 409 EMOTICON_PACK_SUSPENDED}
 * → <b>내가 만든 팩</b>이면 같은 결과(받기 줄·받은 수·보상 없음 — 사지 않아도 가진다, 후속 결정 1)
 * → <b>운영자 회원 계정</b>(힘이 살아 있을 때)이면 아무것도 바꾸지 않고 200 — 판매 중 팩은 이미 쓸 수 있다, 받기 줄을 만들거나 되살리지 않는다({@code acquired} = 정말 가졌는가,
 * 운영자 회원 계정 연결 2026-11-05) → 이미 가졌으면 같은 결과(멱등) → 뺐던 팩이면 다시 가진다(받은 수·보상 없음 — 무료일 때 받은 팩이 그 뒤 유료가 됐어도, S2) → 유료 {@code 409 EMOTICON_PACK_PAID}(구매 — S3)
 * → 처음이면 받기 줄 + 받은 수 + 1 +
 * <b>받기 보상</b>(작가 팩만 — 운영 팩·본인·인정되지 않는 받기는 없음, {@link PointService#emoticonCreatorReward} 한 길, 작가 잔액 행 잠금이 마지막 잠금, 실패해도 받기는 성공).
 * 받기 줄은 지우지 않으므로 같은 회원의 같은 팩 보상은 처음 한 번만이다(빼고 다시 받아도).
 *
 * <p><b>빼기</b>({@link #remove}) — 무료로 받은 팩만(S1 은 모두 무료). 가지지 않았으면 할 일이 없다(멱등). 산 팩 {@code 409 EMOTICON_PACK_PAID} ·
 * 내가 만든 상점 팩 {@code 409 EMOTICON_PACK_OWNED}(최상위 {@code reason: CREATOR} — 빼도 가진 것으로 보이므로 빼지 않는다).
 */
@Service
public class EmoticonAcquireService {

    private static final Logger log = LoggerFactory.getLogger(EmoticonAcquireService.class);

    private final EmoticonPackStore packs;
    private final EmoticonAcquisitionStore acquisitions;
    private final EmoticonReadStore reads;
    private final EmoticonSettingsService settings;
    private final EmoticonContent content;
    private final PointService points;
    private final SnowflakeIdGenerator ids;
    private final OperatorMemberAccountService operatorAccounts;
    private final Clock clock;

    public EmoticonAcquireService(EmoticonPackStore packs, EmoticonAcquisitionStore acquisitions, EmoticonReadStore reads, EmoticonSettingsService settings,
                                  EmoticonContent content, PointService points, SnowflakeIdGenerator ids, OperatorMemberAccountService operatorAccounts,
                                  ObjectProvider<Clock> clocks) {
        this.operatorAccounts = operatorAccounts;
        this.packs = packs;
        this.acquisitions = acquisitions;
        this.reads = reads;
        this.settings = settings;
        this.content = content;
        this.points = points;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 받기 결과 + 이번에 작가에게 준 보상(시험·로그용 — 응답에는 싣지 않는다) */
    public record Acquired(EmoticonPublicView.AcquireResult result, long rewarded) {
    }

    @Transactional
    public Acquired acquire(long userId, String packIdText) {
        if (reads.member(userId).filter(m -> !m.gone()).isEmpty()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        long packId = EmoticonShopService.parse(packIdText);
        PackRow p = packId < 0 ? null : packs.lock(packId).orElse(null);
        if (p == null || !p.status().published()) {
            throw new EmoticonPackNotFoundException();
        }
        if (p.status() == EmoticonPackStatus.SUSPENDED) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_SUSPENDED);
        }
        if (Long.valueOf(userId).equals(p.creatorId())) {
            // 내가 만든 팩 — 사지 않아도 가진다(후속 결정 1). 받기 줄·받은 수·보상 없이 같은 결과
            return new Acquired(new EmoticonPublicView.AcquireResult(Long.toString(packId), true, p.acquireCount()), 0);
        }
        Optional<AcquisitionRow> existing = acquisitions.find(userId, packId);
        if (operatorAccounts.isActiveOperatorAccount(userId)) {
            // 운영자 회원 계정 — 판매 중 팩은 이미 쓸 수 있다. 받기 줄을 만들거나 되살리지 않는다(받은 수·보상이 부풀지 않게, 운영자 회원 계정 연결 2026-11-05).
            // acquired = 정말 가졌는가(팩 상세의 acquired 와 같은 뜻 — 연결 전에 받거나 산 줄이 살아 있으면 true)
            return new Acquired(new EmoticonPublicView.AcquireResult(Long.toString(packId), existing.map(AcquisitionRow::owned).orElse(false), p.acquireCount()), 0);
        }
        Instant now = clock.instant();
        if (existing.isPresent()) {
            AcquisitionRow row = existing.get();
            // 이미 가졌다(무료로 받았거나 샀다 — 산 팩이 그 뒤 무료가 됐어도 새 받기 줄·받은 수·보상은 없다, S3) → 같은 결과
            if (!row.owned()) {
                if (!row.purchased()) {
                    // 무료일 때 받았다가 뺀 팩 — 그 뒤 유료가 됐어도 다시 넣는다(계약 「유료 ↔ 무료 전환」, S2). 받은 수·보상은 처음 한 번만
                    acquisitions.restore(row.id(), now);
                } else if (p.price() > 0) {
                    throw new EmoticonConflictException(EmoticonConflictException.PACK_PAID); // 샀다가 환불한 유료 팩 — 다시 사야 한다
                } else {
                    acquisitions.restoreAsFree(row.id(), now); // 샀다가 환불한 팩이 무료가 됐다 — 받기로 다시(줄은 이미 한 번 셌다 — 받은 수·보상 없음)
                }
            }
            return new Acquired(new EmoticonPublicView.AcquireResult(Long.toString(packId), true, p.acquireCount()), 0);
        }
        if (p.price() > 0) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_PAID);
        }
        long acquisitionId = ids.nextId();
        acquisitions.insert(acquisitionId, userId, packId, now);
        int count = packs.incrementAcquire(packId);
        long rewarded = 0;
        if (p.source() == EmoticonPackSource.CREATOR && p.creatorId() != null && p.price() == 0) {
            SettingsRow s = settings.current();
            rewarded = points.emoticonCreatorReward(p.creatorId(), userId, acquisitionId, s.rewardPerAcquire(), s.rewardDailyCap());
        }
        log.info("로보티콘 팩 받기 userId={} packId={} rewarded={}", userId, packId, rewarded);
        return new Acquired(new EmoticonPublicView.AcquireResult(Long.toString(packId), true, count), rewarded);
    }

    /** 목록에서 빼기 — 형식이 틀린 ID·가지지 않은 팩은 할 일이 없다. 산 팩은 빼지 않는다({@code 409 EMOTICON_PACK_PAID} — 쓰지 않았으면 구매 내역에서 환불, 가정 11) */
    @Transactional
    public void remove(long userId, String packIdText) {
        long packId = EmoticonShopService.parse(packIdText);
        if (packId < 0) {
            return;
        }
        if (acquisitions.creatorOwns(userId, packId)) {
            throw EmoticonConflictException.creatorOwned("error.EMOTICON_PACK_OWNED.creatorRemove");
        }
        Optional<AcquisitionRow> row = acquisitions.find(userId, packId);
        if (row.isPresent() && row.get().owned() && row.get().purchased()) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_PAID, "error.EMOTICON_PACK_PAID.remove");
        }
        acquisitions.remove(userId, packId, clock.instant());
    }

    /** 내 로보티콘 — 지금 가진 팩 중 상점에 나와 있는 것(판매 중단 포함 — 가졌으면 계속 쓴다), 최근 가진 것 먼저 */
    @Transactional(readOnly = true)
    public EmoticonPublicView.MyPacks mine(long userId) {
        boolean allPacks = operatorAccounts.isActiveOperatorAccount(userId); // 운영자 회원 계정 — 판매 중 팩 전부를 쓴다(목록은 가진 팩만 그대로)
        Map<Long, EmoticonAcquisitionStore.OwnedPack> owned = acquisitions.owned(userId);
        if (owned.isEmpty()) {
            return new EmoticonPublicView.MyPacks(List.of(), allPacks);
        }
        Map<Long, PackRow> byId = packs.byIds(owned.keySet()).stream().filter(p -> p.status().published()).collect(Collectors.toMap(PackRow::id, p -> p));
        Map<Long, EmoticonContent.Loaded> lives = content.load(byId.values().stream().map(PackRow::liveRevisionId).toList());
        Set<Long> creators = new HashSet<>();
        byId.values().forEach(p -> {
            if (p.creatorId() != null) {
                creators.add(p.creatorId());
            }
        });
        Map<Long, MemberRef> members = reads.members(creators);
        List<EmoticonPublicView.MyPack> out = new ArrayList<>();
        for (EmoticonAcquisitionStore.OwnedPack o : owned.values()) {
            PackRow p = byId.get(o.packId());
            EmoticonContent.Loaded live = p == null ? null : lives.get(p.liveRevisionId());
            if (live == null) {
                continue;
            }
            MemberRef creator = p.creatorId() == null ? null : members.get(p.creatorId());
            // 산 팩은 결제 금액·구매 번호(목록에서 빼지 않는다 — 환불은 구매 내역), 무료로 받은 팩은 0·null
            out.add(new EmoticonPublicView.MyPack(Long.toString(p.id()), live.revision().name(), p.source().name(), creator == null ? null : creator.nickname(),
                    content.coverUrl(live), o.acquiredAt(), o.purchaseId() == null ? 0 : o.purchaseAmount(), o.purchaseId() == null ? null : Long.toString(o.purchaseId()),
                    p.status() == EmoticonPackStatus.SUSPENDED, content.publicItems(live), o.creator()));
        }
        return new EmoticonPublicView.MyPacks(out, allPacks);
    }
}
