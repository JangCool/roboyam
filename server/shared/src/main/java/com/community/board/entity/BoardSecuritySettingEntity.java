package com.community.board.entity;

import com.community.board.dto.BoardSecurityValues;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 게시판 기본 설정 「보안」(행 하나) — V42__boards.sql 의 {@code board_security_settings}. 금지어는 JSON 배열 칸이다. 이번에는 저장만 하고 적용 지점은 후속이다 */
@Entity
@Table(name = "board_security_settings")
public class BoardSecuritySettingEntity {

    public static final int ROW_ID = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Id
    private Integer id;

    private int viewCountDedupHours;
    private boolean blockedWordsEnabled;
    private String blockedWordsAction;

    /** JSON 배열 글자 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String blockedWords;

    private int newMemberLinkLimit;
    private int newMemberImageLimit;
    private int duplicatePostWindowMinutes;
    private boolean externalLinkNofollow;
    private boolean externalLinkWarn;
    private boolean guestCaptchaRequired;
    private int guestNameMin;
    private int guestNameMax;
    private int guestPasswordMin;
    private int guestLinkLimit;
    private boolean guestImageUploadAllowed;
    private Instant updatedAt;
    private Long updatedBy;

    protected BoardSecuritySettingEntity() {
    }

    public BoardSecurityValues values() {
        List<String> words = blockedWords == null || blockedWords.isBlank() ? List.of() : MAPPER.readValue(blockedWords, new TypeReference<List<String>>() {
        });
        return new BoardSecurityValues(viewCountDedupHours, blockedWordsEnabled, blockedWordsAction, words, newMemberLinkLimit, newMemberImageLimit,
                duplicatePostWindowMinutes, externalLinkNofollow, externalLinkWarn, guestCaptchaRequired, guestNameMin, guestNameMax, guestPasswordMin,
                guestLinkLimit, guestImageUploadAllowed);
    }

    public void change(BoardSecurityValues v, long operatorId, Instant now) {
        this.viewCountDedupHours = v.viewCountDedupHours();
        this.blockedWordsEnabled = v.blockedWordsEnabled();
        this.blockedWordsAction = v.blockedWordsAction();
        this.blockedWords = MAPPER.writeValueAsString(v.blockedWords());
        this.newMemberLinkLimit = v.newMemberLinkLimit();
        this.newMemberImageLimit = v.newMemberImageLimit();
        this.duplicatePostWindowMinutes = v.duplicatePostWindowMinutes();
        this.externalLinkNofollow = v.externalLinkNofollow();
        this.externalLinkWarn = v.externalLinkWarn();
        this.guestCaptchaRequired = v.guestCaptchaRequired();
        this.guestNameMin = v.guestNameMin();
        this.guestNameMax = v.guestNameMax();
        this.guestPasswordMin = v.guestPasswordMin();
        this.guestLinkLimit = v.guestLinkLimit();
        this.guestImageUploadAllowed = v.guestImageUploadAllowed();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
