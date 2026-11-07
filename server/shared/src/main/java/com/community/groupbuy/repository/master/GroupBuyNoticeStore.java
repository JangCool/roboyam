package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyNoticeEntity;
import com.community.groupbuy.entity.GroupBuyNoticeSkuEntity;
import com.community.groupbuy.entity.GroupBuyNoticeValueEntity;
import com.community.groupbuy.entity.QGroupBuyNoticeEntity;
import com.community.groupbuy.entity.QGroupBuyNoticeSkuEntity;
import com.community.groupbuy.entity.QGroupBuyNoticeValueEntity;
import com.community.groupbuy.entity.QGroupBuyProductSkuEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/**
 * 상품정보제공고시 양식·적용 단품·항목 값·판매자 추가 항목(계약 결정 87). 상품마다(결정 89) — 저장은 상품 단위로 통째로 바꾼다(양식 줄을 지우면 적용 단품·값은 CASCADE).
 * 읽기는 양식 한 번 · 적용 단품 한 번 · 값 한 번(양식마다 따로 읽지 않는다). 모두 QueryDSL({@link GroupBuyStoreSupport}).
 */
@Repository
public class GroupBuyNoticeStore extends GroupBuyStoreSupport {

    private static final QGroupBuyNoticeEntity N = QGroupBuyNoticeEntity.groupBuyNoticeEntity;
    private static final QGroupBuyNoticeSkuEntity NS = QGroupBuyNoticeSkuEntity.groupBuyNoticeSkuEntity;
    private static final QGroupBuyNoticeValueEntity NV = QGroupBuyNoticeValueEntity.groupBuyNoticeValueEntity;
    private static final QGroupBuyProductSkuEntity PS = QGroupBuyProductSkuEntity.groupBuyProductSkuEntity;

    /** 양식 한 장(적용 단품 이름·고시 항목 값·판매자 추가 항목 포함) */
    public record NoticeRow(long id, int sortOrder, String categoryCode, String title, String catalogVersion, List<Long> skuIds, List<String> skuNames,
                            Map<String, ValueRow> values, List<CustomRow> customItems) {
    }

    /** 고시 항목 값 */
    public record ValueRow(String itemKey, String value, boolean unavailable, String unavailableReason) {
    }

    /** 판매자 추가 항목(차례대로) */
    public record CustomRow(String name, String value, int sortOrder) {
    }

    public GroupBuyNoticeStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    public List<NoticeRow> notices(long productId) {
        Map<Long, NoticeRow> byId = new LinkedHashMap<>();
        query.select(N.id, N.sortOrder, N.categoryCode, N.title, N.catalogVersion).from(N).where(N.productId.eq(productId)).orderBy(N.sortOrder.asc()).fetch()
                .forEach(t -> byId.put(t.get(N.id), new NoticeRow(t.get(N.id), n(t.get(N.sortOrder)), t.get(N.categoryCode), t.get(N.title), t.get(N.catalogVersion),
                        new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>(), new ArrayList<>())));
        if (byId.isEmpty()) {
            return List.of();
        }
        // 적용 단품 — 양식 안에서 단품 차례(sort_order, id)
        query.select(NS.noticeId, PS.id, PS.name).from(NS).join(PS).on(PS.id.eq(NS.productSkuId)).where(NS.noticeId.in(byId.keySet()))
                .orderBy(PS.sortOrder.asc(), PS.id.asc()).fetch().forEach(t -> {
                    NoticeRow notice = byId.get(t.get(NS.noticeId));
                    notice.skuIds().add(t.get(PS.id));
                    notice.skuNames().add(t.get(PS.name));
                });
        // 값 — 양식 안에서 (custom, sort_order, item_key) 차례
        query.select(NV.noticeId, NV.custom, NV.itemKey, NV.customName, NV.sortOrder, NV.value, NV.unavailable, NV.unavailableReason).from(NV)
                .where(NV.noticeId.in(byId.keySet())).orderBy(NV.custom.asc(), NV.sortOrder.asc(), NV.itemKey.asc()).fetch().forEach(t -> {
                    NoticeRow notice = byId.get(t.get(NV.noticeId));
                    if (Boolean.TRUE.equals(t.get(NV.custom))) {
                        notice.customItems().add(new CustomRow(t.get(NV.customName), t.get(NV.value), n(t.get(NV.sortOrder))));
                    } else {
                        notice.values().put(t.get(NV.itemKey), new ValueRow(t.get(NV.itemKey), t.get(NV.value), Boolean.TRUE.equals(t.get(NV.unavailable)),
                                t.get(NV.unavailableReason)));
                    }
                });
        return new ArrayList<>(byId.values());
    }

    /** 상품의 양식을 모두 지운다(적용 단품·값은 DB 의 ON DELETE CASCADE) */
    public void deleteAll(long productId) {
        query.delete(N).where(N.productId.eq(productId)).execute();
    }

    public void insertNotice(long id, long productId, int sortOrder, String categoryCode, String title, String catalogVersion, Instant now) {
        insert(GroupBuyNoticeEntity.create(id, productId, sortOrder, categoryCode, title, catalogVersion, now));
    }

    public void insertSku(long noticeId, long productId, long productSkuId) {
        insert(GroupBuyNoticeSkuEntity.create(noticeId, productId, productSkuId));
    }

    public void insertValue(long id, long noticeId, String itemKey, String value, boolean unavailable, String reason) {
        insert(GroupBuyNoticeValueEntity.item(id, noticeId, itemKey, value, unavailable, reason));
    }

    public void insertCustom(long id, long noticeId, String name, String value, int sortOrder) {
        insert(GroupBuyNoticeValueEntity.custom(id, noticeId, name, value, sortOrder));
    }
}
