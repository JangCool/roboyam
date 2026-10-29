package com.community.address.entity;

import com.community.address.dto.AddressProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 주소 검색 설정 — 사이트에 한 줄(id = 1, V109 가 넣는다). 관리자 설정 › 주소 검색이 {@link #change} 로 바꾸고, 공개 서버(api)는 회원 화면에 제공처·스크립트 주소를 내려 준다.
 * {@code scriptUrl} 이 null 이면 공식 주소({@code AddressSearchPolicy.DEFAULT_SCRIPT_URL})를 쓴다.
 */
@Entity
@Table(name = "address_search_settings")
public class AddressSearchSettingsEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Enumerated(EnumType.STRING)
    private AddressProvider provider;

    private String scriptUrl;
    private int staleWarnDays;
    private Instant updatedAt;
    private Long updatedBy;

    protected AddressSearchSettingsEntity() {
    }

    public void change(AddressProvider provider, String scriptUrl, int staleWarnDays, long operatorId, Instant now) {
        this.provider = provider;
        this.scriptUrl = scriptUrl;
        this.staleWarnDays = staleWarnDays;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public AddressProvider getProvider() {
        return provider;
    }

    public String getScriptUrl() {
        return scriptUrl;
    }

    public int getStaleWarnDays() {
        return staleWarnDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
