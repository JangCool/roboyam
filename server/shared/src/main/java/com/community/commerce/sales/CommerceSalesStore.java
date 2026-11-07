package com.community.commerce.sales;

import com.community.commerce.sales.entity.CommerceBusinessInfoEntity;
import com.community.commerce.sales.entity.CommerceSalesSettingsEntity;
import com.community.commerce.sales.entity.QCommerceBusinessInfoEntity;
import com.community.commerce.sales.entity.QCommerceSalesChecklistEntity;
import com.community.commerce.sales.entity.QCommerceSalesSettingsEntity;
import com.community.emoticon.entity.EmoticonSettingsEntity;
import com.community.emoticon.entity.QEmoticonSettingsEntity;
import com.community.groupbuy.entity.QCommerceCarrierEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.site.entity.QSiteOperatorEntity;
import com.community.site.entity.SiteOperatorEntity;
import com.community.site.entity.SiteOperatorStage;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 판매 스위치·운영 체크리스트·사업자 정보(V104 — {@code commerce_sales_settings}·{@code commerce_sales_checklist}·{@code commerce_business_info}, 모두 1행 또는 고정 줄).
 * 운영자 이름은 {@code operators.operator_name} 을 이어 읽는다(지운 운영자는 null).
 *
 * <p>QueryDSL 이다 — {@code commerce_*} 표(엔티티 {@link CommerceSalesSettingsEntity} 등)와 운영 주체({@code site_operator}, 엔티티 {@link SiteOperatorEntity}).
 * 스위치 행 잠금은 그 행만 고르는 {@code PESSIMISTIC_WRITE}, 로보티콘 유료 판매 스위치({@code emoticon_settings} — 엔티티 {@link EmoticonSettingsEntity})는 그 행만 고르는
 * {@code PESSIMISTIC_READ}({@code FOR SHARE}). Native SQL 로 남긴 것은 없다.
 */
@Repository
public class CommerceSalesStore extends RoboyamRepositoryQDSL {

    private static final QSiteOperatorEntity SO = QSiteOperatorEntity.siteOperatorEntity;
    private static final QCommerceSalesSettingsEntity SW = QCommerceSalesSettingsEntity.commerceSalesSettingsEntity;
    private static final QCommerceSalesChecklistEntity CL = QCommerceSalesChecklistEntity.commerceSalesChecklistEntity;
    private static final QCommerceBusinessInfoEntity BI = QCommerceBusinessInfoEntity.commerceBusinessInfoEntity;
    private static final QCommerceCarrierEntity CA = QCommerceCarrierEntity.commerceCarrierEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QEmoticonSettingsEntity ES = QEmoticonSettingsEntity.emoticonSettingsEntity;

    /** 스위치 한 줄 */
    public record SwitchRow(boolean groupBuyOpen, Instant changedAt, String changedBy, String reason) {
    }

    /** 체크 항목 한 줄 */
    public record ChecklistRow(String key, boolean checked, String checkedBy, Instant checkedAt) {
    }

    public CommerceSalesStore(JPAQueryFactory query) {
        super(query);
    }

    public boolean groupBuyOpen() {
        return Boolean.TRUE.equals(query.select(SW.groupbuyOpen).from(SW).where(SW.id.eq(CommerceSalesSettingsEntity.ROW_ID)).fetchFirst());
    }

    public SwitchRow switchRow() {
        SwitchRow row = query.select(Projections.constructor(SwitchRow.class, SW.groupbuyOpen, SW.changedAt, O.operatorName, SW.changeReason)).from(SW).leftJoin(O)
                .on(O.id.eq(SW.changedBy)).where(SW.id.eq(CommerceSalesSettingsEntity.ROW_ID)).fetchFirst();
        if (row == null) {
            throw new EmptyResultDataAccessException(1); // 스위치 행은 마이그레이션이 넣는다
        }
        return row;
    }

    /** 스위치 행을 잠근다(켜기·끄기가 줄 선다) — 그 행만 고르는 쓰기 잠금 */
    public boolean lockSwitch() {
        Boolean open = query.select(SW.groupbuyOpen).from(SW).where(SW.id.eq(CommerceSalesSettingsEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst();
        if (open == null) {
            throw new EmptyResultDataAccessException(1);
        }
        return open;
    }

    public void setSwitch(boolean open, String reason, Long operatorId, Instant now) {
        query.update(SW).set(SW.groupbuyOpen, open).set(SW.changeReason, reason).set(SW.changedBy, operatorId).set(SW.changedAt, now)
                .where(SW.id.eq(CommerceSalesSettingsEntity.ROW_ID)).execute();
    }

    public List<ChecklistRow> checklist() {
        return query.select(Projections.constructor(ChecklistRow.class, CL.itemKey, CL.checked, O.operatorName, CL.checkedAt)).from(CL).leftJoin(O).on(O.id.eq(CL.checkedBy))
                .orderBy(CL.sortOrder.asc()).fetch();
    }

    /** @return 바뀌었으면 true(같은 값이면 false — 감사를 남기지 않는다) */
    public boolean check(String key, boolean checked, Long operatorId, Instant now) {
        return query.update(CL).set(CL.checked, checked).set(CL.checkedBy, checked ? operatorId : null).set(CL.checkedAt, checked ? now : null)
                .where(CL.itemKey.eq(key), CL.checked.ne(checked)).execute() == 1;
    }

    /**
     * 사업자 정보 열두 칸 — <b>겹치는 칸은 운영 주체({@code site_operator}, 정본)</b>에서, 커머스 고유 칸(운영시간·배송·환불 안내)과 마지막 저장은 {@code commerce_business_info} 에서 읽는다
     * (terms-operator-contract 세션 정리 결정). 상호 = 상호(법인은 회사명), 보호책임자 = 운영 주체의 보호책임자(「운영자와 같음」이면 운영자 이름). 개인 단계는 사업자 칸이 비어 있다(빈 글자).
     */
    public BusinessInfo businessInfo() {
        // 예전과 같이 한 문장 — 커머스 고유 칸(commerce_business_info) × 겹치는 칸(운영 주체 site_operator, 정본) + 마지막 저장한 운영자 이름(operators).
        // 두 행을 같은 시점에 읽는다(문장을 나누면 그 사이 저장이 섞인 사업자 정보를 낼 수 있다)
        Tuple s = query.select(BI.businessHours, BI.shippingRefundPolicy, BI.updatedAt, O.operatorName, SO.businessName, SO.representative, SO.registrationNumber,
                        SO.mailOrderNumber, SO.mailOrderAuthority, SO.address, SO.phone, SO.contactEmail, SO.privacyOfficerSame, SO.operatorName, SO.privacyOfficerName,
                        SO.hostingProvider)
                .from(BI).join(SO).on(SO.id.eq(SiteOperatorEntity.ROW_ID)).leftJoin(O).on(O.id.eq(BI.updatedBy))
                .where(BI.id.eq(CommerceBusinessInfoEntity.ROW_ID)).fetchFirst();
        if (s == null) {
            throw new EmptyResultDataAccessException(1); // 예전 queryForObject 와 같이 — 사업자 정보 행·운영 주체 행은 마이그레이션이 넣는다(없으면 읽지 못한다)
        }
        CommerceOwn own = new CommerceOwn(s.get(BI.businessHours), s.get(BI.shippingRefundPolicy), s.get(BI.updatedAt), s.get(O.operatorName));
        String privacyOfficer = Boolean.TRUE.equals(s.get(SO.privacyOfficerSame)) ? s.get(SO.operatorName) : s.get(SO.privacyOfficerName);
        return new BusinessInfo(nz(s.get(SO.businessName)), nz(s.get(SO.representative)), nz(s.get(SO.registrationNumber)), nz(s.get(SO.mailOrderNumber)),
                nz(s.get(SO.mailOrderAuthority)), nz(s.get(SO.address)), nz(s.get(SO.phone)), nz(s.get(SO.contactEmail)), own.businessHours(), nz(privacyOfficer),
                nz(s.get(SO.hostingProvider)), own.shippingRefundPolicy(), own.updatedAt(), own.updatedBy());
    }

    /** 사업자 정보 중 커머스 고유 칸 */
    private record CommerceOwn(String businessHours, String shippingRefundPolicy, Instant updatedAt, String updatedBy) {
    }

    /** {@code coalesce(x, '')} */
    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** 커머스 고유 칸만 저장한다(운영시간·배송·환불 안내) — 겹치는 칸은 운영 주체 설정이 저장한다 */
    public void saveBusinessInfo(BusinessInfo v, Long operatorId, Instant now) {
        query.update(BI).set(BI.businessHours, v.businessHours()).set(BI.shippingRefundPolicy, v.shippingRefundPolicy()).set(BI.updatedBy, operatorId).set(BI.updatedAt, now)
                .where(BI.id.eq(CommerceBusinessInfoEntity.ROW_ID)).execute();
    }

    /**
     * 운영 주체가 판매를 열 수 있는 단계인가 — 개인사업자 이상이고 통신판매업 신고번호가 있다(판매 켜기 자동 조건 {@code OPERATOR_STAGE}). <b>돈을 받는 기능은 모두 이 한 곳으로 판정한다</b>
     * (사용자 결정 2026-11-07 — 공구 판매 켜기 · 로보티콘 유료 판매 켜기·구매 · 광고/핫딜 유료 신청·결제 시작·승인·입금 확정, terms-operator-contract).
     */
    public boolean operatorCanSell() {
        // 신고번호는 DB 에서 앞뒤 공백을 깎는다(HQL trim = PostgreSQL btrim 과 같은 공백만)
        Tuple row = query.select(SO.stage, SO.mailOrderNumber.trim()).from(SO).where(SO.id.eq(SiteOperatorEntity.ROW_ID)).fetchFirst();
        if (row == null) {
            throw new EmptyResultDataAccessException(1); // 예전 queryForObject 와 같이 — 운영 주체 행은 설치 때 생긴다
        }
        SiteOperatorStage stage = row.get(0, SiteOperatorStage.class);
        String mailOrderNumber = row.get(1, String.class);
        return stage != null && stage != SiteOperatorStage.INDIVIDUAL && mailOrderNumber != null && !mailOrderNumber.isEmpty();
    }

    /**
     * 로보티콘 유료 판매 스위치({@code emoticon_settings.paid_sales_enabled})를 나눔 잠금으로 잡고 읽는다 — 운영 주체를 개인 단계로 바꾸거나 신고번호를 비우기 전에(켜져 있으면 막는다).
     * 설정 저장(행 쓰기 잠금 뒤 운영 주체 읽기)과 줄을 서서, 켜기가 읽은 운영 주체 단계가 그 커밋 전에 바뀌지 않는다. 잠금은 설정 표만 고르는 {@code PESSIMISTIC_READ}
     * ({@code FOR SHARE}) — 설정 행이 없으면 예전처럼 {@link EmptyResultDataAccessException}.
     */
    public boolean lockEmoticonPaidSales() {
        Boolean on = query.select(ES.paidSalesEnabled).from(ES).where(ES.id.eq(EmoticonSettingsEntity.ROW_ID)).setLockMode(LockModeType.PESSIMISTIC_READ).fetchFirst();
        if (on == null) {
            throw new EmptyResultDataAccessException(1);
        }
        return on;
    }

    /** 켜진 택배사가 하나라도 있나(판매 켜기 자동 조건 — 공구) */
    public boolean anyActiveCarrier() {
        return query.selectOne().from(CA).where(CA.enabled.isTrue()).fetchFirst() != null;
    }

}
