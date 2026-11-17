package com.community.title.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.title.dto.TitleCondition;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleMethod;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 칭호의 사람이 읽는 글 — 조건 한 줄(「정식 등급 이상」·「사용기 게시판 글 3편」…)과 받는 방법 한 줄(도감 {@code howTo}). 요청 언어로 만든다(번들 {@code title.condition.*}·
 * {@code title.howTo.*}·{@code title.metric.*}). 게시판·등급 이름은 운영자가 넣은 값이라 영어 요청이면 영어 이름(없으면 한국어)을 쓴다.
 */
@Component
public class TitleTexts {

    private final Messages messages;
    private final BoardCatalog catalog;
    private final TitleSettingsReader settings;

    public TitleTexts(Messages messages, BoardCatalog catalog, TitleSettingsReader settings) {
        this.messages = messages;
        this.catalog = catalog;
        this.settings = settings;
    }


    /** 시스템 회수 사유 코드(체험단 — V241). 운영자가 적은 글이 아니라 코드로 남는다 */
    private static final java.util.Set<String> SYSTEM_REVOKE_CODES = java.util.Set.of(com.community.restriction.entity.RestrictionAutoRule.TRIAL_UNRETURNED,
            com.community.restriction.entity.RestrictionAutoRule.TRIAL_REVIEW_LATE, com.community.restriction.entity.RestrictionAutoRule.TRIAL_DAMAGE_INTENTIONAL);

    /**
     * 회수 사유를 보일 글로(점검 L2) — 시스템 회수(운영자 없음)는 저장된 코드 대신 요청 언어의 번들 문구({@code title.revoke.system.<코드>}), 운영자 회수는 적은 글 그대로.
     */
    public String revokeReason(Long revokedBy, String stored) {
        if (revokedBy == null && stored != null && SYSTEM_REVOKE_CODES.contains(stored)) {
            return messages.get(new MessageRef("title.revoke.system." + stored), messages.locale());
        }
        return stored;
    }
    /** 조건 한 줄 */
    public String condition(TitleCondition c) {
        BoardCatalogSnapshot s = catalog.snapshot();
        Locale locale = messages.locale();
        return switch (c.type()) {
            case GRADE_AT_LEAST -> {
                String grade = s.gradeLabel(c.gradeKey());
                yield messages.get(new MessageRef("title.condition.GRADE_AT_LEAST", grade == null ? String.valueOf(c.gradeKey()) : grade), locale);
            }
            case POSTS -> {
                Optional<Long> board = TitleEligibility.board(c, s);
                String name = board == null || board.isEmpty() ? null : s.board(board.get()).map(b -> boardName(b, locale)).orElse(null);
                if (name == null && c.boardSlug() != null && board == null) {
                    name = c.boardSlug();
                }
                yield name == null ? messages.get(new MessageRef("title.condition.POSTS.any", String.valueOf(value(c))), locale)
                        : messages.get(new MessageRef("title.condition.POSTS", name, String.valueOf(value(c))), locale);
            }
            case POPULAR_DAYS -> messages.get(new MessageRef("title.condition.POPULAR_DAYS", String.valueOf(value(c))), locale);
            case ATTENDANCE_DAYS -> messages.get(new MessageRef("title.condition.ATTENDANCE_DAYS", String.valueOf(value(c))), locale);
            case NO_REPORT_HIDDEN -> messages.get(new MessageRef("title.condition.NO_REPORT_HIDDEN"), locale);
            case PURCHASE_PROOFS -> messages.get(new MessageRef("title.condition.PURCHASE_PROOFS", String.valueOf(value(c))), locale);
        };
    }

    /** 받는 방법 한 줄(도감) — 「자동 · 출석 100일」·「신청·심사 · 정식 등급 이상 · 뉴스 게시판 글 10편」·「매달 운영자가 최대 3명에게 줘요」… */
    public String howTo(TitleEntity title) {
        Locale locale = messages.locale();
        String key = title.getTitleKey();
        if (BuiltinTitles.MONTHLY_WRITER.equals(key)) {
            return messages.get(new MessageRef("title.howTo.monthly-writer", String.valueOf(settings.current().monthlyMax())), locale);
        }
        if (BuiltinTitles.YEARLY_WRITER.equals(key)) {
            return messages.get(new MessageRef("title.howTo.yearly-writer"), locale);
        }
        if (BuiltinTitles.HONORARY_WRITER.equals(key)) {
            return messages.get(new MessageRef("title.howTo.honorary-writer", String.valueOf(settings.current().honoraryCount())), locale);
        }
        List<String> parts = new ArrayList<>();
        parts.add(messages.get(new MessageRef("title.method." + title.getMethod().name()), locale));
        for (TitleCondition c : TitleConditions.read(title.getConditions())) {
            parts.add(condition(c));
        }
        if (title.isTiered() && title.getMethod() == TitleMethod.AUTO) {
            parts.add(messages.get(new MessageRef("title.howTo.tiers", messages.get(new MessageRef("title.metric." + title.getTierMetric().name()), locale),
                    String.valueOf(title.getTierBronze()), String.valueOf(title.getTierSilver()), String.valueOf(title.getTierGold())), locale));
        }
        return String.join(" · ", parts);
    }

    /** 단계가 세는 것의 이름(「하루 인기글 상위 진입」…) */
    public String metric(TitleEntity title) {
        return title.getTierMetric() == null ? null : messages.get(new MessageRef("title.metric." + title.getTierMetric().name()));
    }

    private static String boardName(EffectiveBoard b, Locale locale) {
        return "en".equals(locale.getLanguage()) && b.nameEn() != null && !b.nameEn().isBlank() ? b.nameEn() : b.name();
    }

    private static int value(TitleCondition c) {
        return c.value() == null ? 1 : Math.max(1, c.value());
    }
}
