package com.community.site.service;

import com.community.site.dto.SiteOperatorValues;
import com.community.site.entity.SiteOperatorEntity;
import com.community.site.repository.master.SiteOperatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 주체의 지금 값(api·admin 공용) — 공개 사이트 정보(단계·연락 이메일), 약관 게시의 자리표시, 관리자 설정이 읽는다.
 * 커머스 사업자 정보는 같은 표를 SQL 로 이어 읽는다({@code CommerceSalesStore}).
 */
@Service
@Transactional(readOnly = true)
public class SiteOperatorReader {

    private final SiteOperatorRepository operators;

    public SiteOperatorReader(SiteOperatorRepository operators) {
        this.operators = operators;
    }

    /** 지금 값 — 행이 없으면(마이그레이션 전) 처음 값 */
    public SiteOperatorValues current() {
        return operators.findById(SiteOperatorEntity.ROW_ID).map(SiteOperatorEntity::values).orElse(SiteOperatorValues.INITIAL);
    }
}
