package com.community.address.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 주소 DB 적재 한 번 — 시작하면 {@code RUNNING} 줄을 넣고(사이트에 하나), 끝나면 {@code DONE}(건수) 또는 {@code FAILED}(사유 문구 키, {@code phase} 는 멈춘 단계)로 닫는다.
 * 적재 이력·「지금 올라 있는 주소 DB」는 가장 최근 {@code DONE} 줄이다. 결과·실패 문구는 언어마다 풀도록 키({@code messageKey})와 인자만 둔다.
 * {@code nodeId} 는 적재를 돌린 관리자 서버 — 서버가 다시 뜨면 자기 서버의 {@code RUNNING} 줄만 「중단」으로 닫는다.
 */
@Entity
@Table(name = "address_db_loads")
public class AddressDbLoadEntity {

    /** 파일 이름·문구 인자를 한 칸에 담을 때의 구분자(파일 이름에 줄바꿈은 없다) */
    private static final String SEPARATOR = "\n";

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private AddressLoadStatus status;

    @Enumerated(EnumType.STRING)
    private AddressImportPhase phase;

    private String fileNames;
    private String charset;
    private int nodeId;
    private Instant startedAt;
    private Long startedBy;
    private Instant finishedAt;
    private Long entryCount;
    private Long jibunCount;
    private Long englishCount;
    private String messageKey;
    private String messageArgs;

    protected AddressDbLoadEntity() {
    }

    public static AddressDbLoadEntity start(long id, List<String> fileNames, String charset, int nodeId, long operatorId, Instant now) {
        AddressDbLoadEntity load = new AddressDbLoadEntity();
        load.id = id;
        load.status = AddressLoadStatus.RUNNING;
        load.phase = AddressImportPhase.COUNTING;
        load.fileNames = String.join(SEPARATOR, fileNames);
        load.charset = charset;
        load.nodeId = nodeId;
        load.startedBy = operatorId;
        load.startedAt = now;
        return load;
    }

    public void succeed(long entries, long jibun, long english, String messageKey, List<String> args, Instant now) {
        this.status = AddressLoadStatus.DONE;
        this.phase = AddressImportPhase.DONE;
        this.entryCount = entries;
        this.jibunCount = jibun;
        this.englishCount = english;
        this.messageKey = messageKey;
        this.messageArgs = String.join(SEPARATOR, args);
        this.finishedAt = now;
    }

    /** 실패로 닫는다 — {@code phase} 는 멈춘 단계로 남긴다(화면이 단계 표시에서 어디서 멈췄는지 보인다) */
    public void fail(String messageKey, List<String> args, Instant now) {
        this.status = AddressLoadStatus.FAILED;
        this.messageKey = messageKey;
        this.messageArgs = String.join(SEPARATOR, args);
        this.finishedAt = now;
    }

    public Long getId() {
        return id;
    }

    public AddressLoadStatus getStatus() {
        return status;
    }

    public AddressImportPhase getPhase() {
        return phase;
    }

    public List<String> getFileNames() {
        return fileNames == null || fileNames.isEmpty() ? List.of() : Arrays.asList(fileNames.split(SEPARATOR));
    }

    public String getCharset() {
        return charset;
    }

    public int getNodeId() {
        return nodeId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Long getStartedBy() {
        return startedBy;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getEntryCount() {
        return entryCount;
    }

    public Long getJibunCount() {
        return jibunCount;
    }

    public Long getEnglishCount() {
        return englishCount;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArgs() {
        return messageArgs == null || messageArgs.isEmpty() ? new Object[0] : messageArgs.split(SEPARATOR, -1);
    }
}
