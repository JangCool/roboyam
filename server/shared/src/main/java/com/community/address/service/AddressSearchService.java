package com.community.address.service;

import com.community.address.dto.AddressProvider;
import com.community.address.dto.response.AddressSearchConfigResponse;
import com.community.address.dto.response.AddressSearchPageResponse;
import com.community.address.entity.AddressSearchSettingsEntity;
import com.community.address.exception.AddressDbNotLoadedException;
import com.community.address.repository.master.AddressEntryQueries;
import com.community.address.repository.master.AddressSearchSettingsRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.sql.SQLException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 화면 우편번호 찾기(docs/features/address-search-contract.md) — 설정(제공처·다음 스크립트 주소)과 자체 주소 DB 검색. 공개 서버(api)가 로그인 없이 부른다.
 * 검색 규칙: 검색어를 낱말로 나눠 모든 낱말이 들어 있는 줄({@link AddressSearchPolicy#terms}), 파일 순서, 건수는 {@link AddressSearchPolicy#TOTAL_CAP} 까지.
 */
@Service
@Transactional(readOnly = true)
public class AddressSearchService {

    private final AddressSearchSettingsRepository settings;
    private final AddressEntryQueries entries;

    public AddressSearchService(AddressSearchSettingsRepository settings, AddressEntryQueries entries) {
        this.settings = settings;
        this.entries = entries;
    }

    public AddressSearchConfigResponse config() {
        return settings.findById(AddressSearchSettingsEntity.SINGLETON_ID)
                .map(row -> new AddressSearchConfigResponse(row.getProvider(), AddressSearchPolicy.effectiveScriptUrl(row.getScriptUrl())))
                .orElseGet(() -> new AddressSearchConfigResponse(AddressProvider.DAUM, AddressSearchPolicy.DEFAULT_SCRIPT_URL));
    }

    /**
     * 검사 순서: 검색어(400 {@code VALIDATION} {@code fields.keyword}) → 주소 DB 가 비었으면 503 {@code ADDRESS_DB_NOT_LOADED} → 검색. 쪽 번호·크기는 범위 안으로 당긴다.
     * 너무 넓어 DB 가 시간 안에 못 끝내면 400 {@code fields.keyword}(「더 자세히」).
     */
    public AddressSearchPageResponse search(String keyword, int page, int size) {
        MessageRef problem = AddressSearchPolicy.keywordProblem(keyword);
        if (problem != null) {
            throw ValidationException.of("keyword", problem);
        }
        if (!entries.loaded()) {
            throw new AddressDbNotLoadedException();
        }
        List<String> terms = AddressSearchPolicy.terms(keyword);
        int safeSize = Math.clamp(size, 1, AddressSearchPolicy.PAGE_SIZE_MAX);
        int safePage = Math.clamp(page, 0, AddressSearchPolicy.TOTAL_CAP / safeSize);
        try {
            long total = entries.count(terms, AddressSearchPolicy.TOTAL_CAP);
            long offset = (long) safePage * safeSize;
            return new AddressSearchPageResponse(offset >= total ? List.of() : entries.search(terms, safeSize, offset), total, safePage, safeSize);
        } catch (DataAccessException e) {
            if (timedOut(e)) {
                throw ValidationException.of("keyword", new MessageRef("address.search.tooBroad"));
            }
            throw e;
        }
    }

    /** 질의 시간 한도로 DB 가 끊었는가(PostgreSQL {@code 57014 query_canceled}) */
    static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof QueryTimeoutException || cause instanceof SQLException sql && "57014".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
