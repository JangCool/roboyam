package com.community.installation.service;

import com.community.aimember.entity.AiAgeBand;
import com.community.aimember.entity.AiGender;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiRelationKind;
import com.community.aimember.entity.AiVoice;
import com.community.aimember.service.AiMemberRules;
import com.community.installation.dto.AiMemberPreset;
import com.community.user.service.UserPolicy;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 설치 구성의 AI 기본 계정 파일({@link AiMemberPreset}) 모양 검사 — 서버가 뜰 때 한 번({@link BoardPresetCatalog}). 어기면 서버가 뜨지 않는다(설치 직후에야 틀린 데이터에 걸리지 않게).
 * 한도·형식은 관리자 「AI 회원」 저장 검사(admin {@code AiMemberAdminService#check})와 같다. 이 DB 에 있어야 하는 것(모델·게시판·아바타)은 넣을 때 본다({@link AiMemberPresetInstaller}).
 */
final class AiMemberPresetValidator {

    /** 관리자 저장 검사와 같은 한도(글자 수 = 코드 포인트) */
    static final int PERSONALITY_MAX = 200;
    static final int TONE_MAX = 100;
    static final int INTERESTS_MAX = 300;
    static final int BIO_MAX = 150;
    static final int EXTRA_PROMPT_MAX = 300;
    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    /** ai_models.model_id 와 같은 형식(V189) */
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SLUG = Pattern.compile("[a-z][a-z0-9-]{1,39}");
    private static final Pattern AVATAR = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private AiMemberPresetValidator() {
    }

    static void validate(String presetId, String where, AiMemberPreset file) {
        if (file == null || file.format() != 1) {
            throw invalid(where, "형식(format)은 1 이어야 한다");
        }
        if (!presetId.equals(file.preset())) {
            throw invalid(where, "다른 구성의 파일이다: " + file.preset());
        }
        if (file.accounts() == null || file.accounts().isEmpty()) {
            throw invalid(where, "계정이 하나도 없다");
        }
        Set<String> nicknames = new HashSet<>();
        for (AiMemberPreset.Account a : file.accounts()) {
            String nick = a.nickname() == null ? "" : a.nickname();
            String at = where + "/" + nick;
            if (!nick.equals(nick.strip()) || UserPolicy.nicknameViolation(nick).isPresent()) {
                throw invalid(at, "닉네임 형식이 아니다");
            }
            if (!nicknames.add(nick.toLowerCase(Locale.ROOT))) {
                throw invalid(at, "닉네임이 겹친다");
            }
            requireEnum(at, "provider", AiProvider.class, a.provider());
            if (a.model() == null || !MODEL.matcher(a.model()).matches()) {
                throw invalid(at, "model 은 모델 아이디(영문·숫자·._- 1~64자)다");
            }
            if (a.avatarId() != null && !AVATAR.matcher(a.avatarId()).matches()) {
                throw invalid(at, "avatarId 형식이 아니다");
            }
            if (a.voice() != null) {
                requireEnum(at, "voice", AiVoice.class, a.voice());
            }
            AiMemberPreset.Persona p = a.persona();
            if (p == null) {
                throw invalid(at, "persona 가 없다");
            }
            requireEnum(at, "persona.gender", AiGender.class, p.gender());
            requireEnum(at, "persona.ageBand", AiAgeBand.class, p.ageBand());
            requireText(at, "persona.personality", p.personality(), PERSONALITY_MAX);
            requireText(at, "persona.tone", p.tone(), TONE_MAX);
            requireText(at, "persona.interests", p.interests(), INTERESTS_MAX);
            if (a.bio() != null && length(a.bio().strip()) > BIO_MAX) {
                throw invalid(at, "bio 는 " + BIO_MAX + "자 이하다");
            }
            LocalTime[] m = window(a.windows() == null ? null : a.windows().morning());
            if (m == null || !m[0].isBefore(m[1]) || m[1].isAfter(LocalTime.NOON)) {
                throw invalid(at, "windows.morning 은 낮 12시 전에 끝나는 시작 < 끝 HH:mm 이다");
            }
            LocalTime[] n = window(a.windows().afternoon());
            if (n == null || !n[0].isBefore(n[1]) || n[0].isBefore(LocalTime.NOON)) {
                throw invalid(at, "windows.afternoon 은 낮 12시 뒤에 시작하는 시작 < 끝 HH:mm 이다");
            }
            if (a.boards() == null || a.boards().isEmpty() || a.boards().stream().anyMatch(s -> s == null || !SLUG.matcher(s).matches())) {
                throw invalid(at, "boards 는 게시판 주소 하나 이상이다");
            }
            if (a.dailyPostMax() != null && (a.dailyPostMax() < AiMemberRules.ACCOUNT_DAILY_POST_MIN || a.dailyPostMax() > AiMemberRules.ACCOUNT_DAILY_POST_MAX)) {
                throw invalid(at, "dailyPostMax 범위를 벗어났다");
            }
            if (a.newsRatio() != null && (a.newsRatio() < 0 || a.newsRatio() > 100)) {
                throw invalid(at, "newsRatio 는 0~100 이다");
            }
            if (a.extraPrompt() != null && length(a.extraPrompt().strip()) > EXTRA_PROMPT_MAX) {
                throw invalid(at, "extraPrompt 는 " + EXTRA_PROMPT_MAX + "자 이하다");
            }
        }
        for (AiMemberPreset.Account a : file.accounts()) {
            if (a.relation() == null) {
                continue;
            }
            String at = where + "/" + a.nickname();
            requireEnum(at, "relation.kind", AiRelationKind.class, a.relation().kind());
            String partner = a.relation().partner();
            if (partner == null || partner.equals(a.nickname()) || !nicknames.contains(partner.toLowerCase(Locale.ROOT))) {
                throw invalid(at, "relation.partner 는 이 파일의 다른 계정 닉네임이다");
            }
        }
    }

    /** {@code HH:mm} 두 개 → 시각 두 개(형식이 틀리면 null) */
    static LocalTime[] window(AiMemberPreset.Window w) {
        if (w == null || w.start() == null || w.end() == null || !TIME.matcher(w.start()).matches() || !TIME.matcher(w.end()).matches()) {
            return null;
        }
        try {
            return new LocalTime[]{LocalTime.parse(w.start(), HHMM), LocalTime.parse(w.end(), HHMM)};
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> void requireEnum(String where, String field, Class<E> type, String raw) {
        try {
            Enum.valueOf(type, raw == null ? "" : raw);
        } catch (IllegalArgumentException e) {
            throw invalid(where, field + " 값을 모른다: " + raw);
        }
    }

    private static void requireText(String where, String field, String value, int max) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty() || length(v) > max) {
            throw invalid(where, field + " 은(는) 1~" + max + "자여야 한다");
        }
    }

    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    private static IllegalStateException invalid(String where, String reason) {
        return new IllegalStateException("설치 프리셋의 AI 기본 계정이 올바르지 않다 [" + where + "]: " + reason);
    }
}
