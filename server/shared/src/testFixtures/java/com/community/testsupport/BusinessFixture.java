package com.community.testsupport;

import com.community.business.entity.BusinessServiceEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.repository.master.BusinessServiceRepository;
import org.springframework.context.ApplicationContext;

/**
 * 시험 사업자 서비스 줄({@code business_services}) — {@link BusinessServiceEntity#create} 로 만들고 {@code persist()} 로 넣는다(호출마다 커밋).
 * 사업자 신원 줄({@code business_profiles})은 SQL 로 남는다 — 팩터리는 신청(PENDING)만 만들고, 승인 행은 심사 운영자·시각을 함께 남기기 때문이다.
 */
public final class BusinessFixture {

    private final FixtureTx tx;
    private final BusinessServiceRepository services;

    public BusinessFixture(ApplicationContext context) {
        this.tx = new FixtureTx(context);
        this.services = context.getBean(BusinessServiceRepository.class);
    }

    /** 서비스 줄 하나 — {@code service}·{@code status} 는 enum 이름(AD·HOTDEAL… / PENDING·APPROVED…), 바꾼 운영자 없음 */
    public void service(long id, long profileId, String service, String status) {
        tx.run(() -> services.persist(BusinessServiceEntity.create(id, profileId, BusinessServiceKind.valueOf(service), BusinessServiceStatus.valueOf(status),
                FixtureTx.now())));
    }
}
