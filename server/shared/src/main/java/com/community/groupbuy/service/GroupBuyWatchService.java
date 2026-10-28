package com.community.groupbuy.service;

import com.community.groupbuy.dto.Availability;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.WatchKind;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심 신청(수량 풀림·마감 임박·다음 차수 — 계약 결정 17·31·84, 표 {@code group_buy_watches}). 신청할 수 있는 때는 서버가 정한다 — 회원 상세의 {@code actions}·{@code skuWatches} 와 같은 판정.
 *
 * <ul>
 *   <li>{@code RESTOCK}: 모집 중이고 그 단품(또는 공구)이 다 팔렸거나 잠시 구매 불가일 때. 단품을 주면 단품 단위, 없으면 공구 단위.</li>
 *   <li>{@code CLOSING_SOON}: 공개된 예정·모집 중일 때(단품 없음).</li>
 *   <li>{@code NEXT_ROUND}: 모집이 끝난 공구(중단 제외)일 때(단품 없음) — 다음 차수가 모집을 시작하면 한 번 알린다.</li>
 * </ul>
 * 같은 신청을 두 번 하면 그대로(204), 끄기는 있으면 지운다.
 */
@Service
public class GroupBuyWatchService {

    private static final Set<GroupBuyStatus> ENDED = Set.of(GroupBuyStatus.CLOSING, GroupBuyStatus.CLOSED_SUCCESS, GroupBuyStatus.ORDERED, GroupBuyStatus.SHIPPING,
            GroupBuyStatus.COMPLETED, GroupBuyStatus.CLOSED_FAILED);

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;
    private final GroupBuyQuoteService quotes;
    private final SnowflakeIdGenerator ids;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyWatchService(DataSource dataSource, GroupBuyStore groups, GroupBuyQuoteService quotes, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.groups = groups;
        this.quotes = quotes;
        this.ids = ids;
        this.clocks = clocks;
    }

    /** 공구 단위로 신청할 수 있나 */
    public boolean allowed(GroupBuyRow g, Availability a, WatchKind kind, Instant now) {
        return switch (kind) {
            case RESTOCK -> g.status() == GroupBuyStatus.RECRUITING && (a.soldOut() || a.temporarilyUnavailable());
            case CLOSING_SOON -> g.status() == GroupBuyStatus.RECRUITING || (g.status() == GroupBuyStatus.DRAFT && g.publishedAt() != null);
            case NEXT_ROUND -> ENDED.contains(g.status());
        };
    }

    /** 단품 단위 수량 풀림을 신청할 수 있는 단품(판매 중인 단품 중 다 팔림·잠시 구매 불가) — 공구가 모집 중일 때만 */
    public List<Long> watchableSkus(GroupBuyRow g, Availability a) {
        if (g.status() != GroupBuyStatus.RECRUITING) {
            return List.of();
        }
        return a.skus().stream().filter(s -> s.onSale() && (s.soldOut() || s.temporarilyUnavailable())).map(Availability.Sku::skuId).toList();
    }

    /** 회원이 신청한 것 — (종류, 단품 ID 또는 null) */
    public record Watch(WatchKind kind, Long skuId) {
    }

    @Transactional(readOnly = true)
    public List<Watch> mine(long userId, long groupBuyId) {
        return jdbc.query("SELECT kind, sku_id FROM group_buy_watches WHERE group_buy_id = ? AND user_id = ?",
                (rs, i) -> new Watch(WatchKind.valueOf(rs.getString(1)), rs.getObject(2) == null ? null : rs.getLong(2)), groupBuyId, userId);
    }

    @Transactional
    public void watch(long userId, long groupBuyId, WatchKind kind, Long skuId) {
        if (kind == null) {
            throw ValidationException.of("kind", new MessageRef("groupbuy.watch.kind.invalid"));
        }
        if (skuId != null && kind != WatchKind.RESTOCK) {
            throw ValidationException.of("skuId", new MessageRef("groupbuy.watch.sku.invalid"));
        }
        GroupBuyRow g = groups.find(groupBuyId).filter(r -> r.publishedAt() != null && r.status() != GroupBuyStatus.CANCELED).orElseThrow(GroupBuyException::notFound);
        Availability a = quotes.availability(groupBuyId);
        Instant now = clocks.getIfUnique(Clock::systemUTC).instant();
        boolean ok = skuId == null ? allowed(g, a, kind, now) : watchableSkus(g, a).contains(skuId);
        if (!ok) {
            throw GroupBuyException.invalidState();
        }
        jdbc.update("INSERT INTO group_buy_watches (id, group_buy_id, user_id, kind, sku_id, created_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (group_buy_id, user_id, kind, sku_id) DO NOTHING", ids.nextId(), groupBuyId, userId, kind.name(), skuId, Timestamp.from(now));
    }

    @Transactional
    public void unwatch(long userId, long groupBuyId, WatchKind kind, Long skuId) {
        if (kind == null) {
            throw ValidationException.of("kind", new MessageRef("groupbuy.watch.kind.invalid"));
        }
        jdbc.update("DELETE FROM group_buy_watches WHERE group_buy_id = ? AND user_id = ? AND kind = ? AND sku_id IS NOT DISTINCT FROM ?", groupBuyId, userId, kind.name(),
                skuId);
    }
}
