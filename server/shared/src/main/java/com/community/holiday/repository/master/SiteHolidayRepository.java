package com.community.holiday.repository.master;

import com.community.holiday.entity.SiteHolidayEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 사이트 쉬는 날({@code site_holidays}) — 넣기는 {@code persist}(ID 는 DB 가 매긴다). 질의는 {@link SiteHolidayStore} */
public interface SiteHolidayRepository extends IrobitRepository<SiteHolidayEntity, Long> {
}
