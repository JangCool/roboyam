package com.community.zconfiguration.message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.stereotype.Component;

/**
 * 키 + 인자({@link MessageRef})를 요청 언어의 문구로 푸는 한 곳. 문구는 {@code messages/messages_ko.properties}·{@code messages_en.properties} 에 있다.
 *
 * <ul>
 *   <li>언어는 {@link LocaleContextHolder} 의 값이다 — <b>요청 스레드 안</b>에서 쓴다({@link RequestLocaleResolver} 가 요청마다 채운다).
 *       필터 단계처럼 그 전에는 로캘을 직접 넘기는 {@code get(ref, locale)} 을 쓴다.</li>
 *   <li>그 언어에 키가 없으면 {@code ko} 로 되돌린다(메시지 소스 설정). 키 이름이 응답에 나가지 않는다. 둘 다 없으면 일반 오류 문구를 주고 오류 로그를 남긴다(코드 결함).</li>
 *   <li>{@code local}·{@code dev} 프로파일에서는 기동 때 ko·en 번들의 키가 서로 같은지 보고 다르면 경고한다.</li>
 * </ul>
 */
@Component
public class Messages {

    private static final Logger log = LoggerFactory.getLogger(Messages.class);

    private final MessageSource source;

    public Messages(MessageSource source, Environment environment,
                    @Value("${spring.messages.basename:" + MessageConfig.DEFAULT_BASENAME + "}") String basename) {
        this.source = source;
        if (environment.acceptsProfiles(Profiles.of("local", "dev"))) {
            warnOnBundleMismatch(basename);
        }
    }

    /** 지금 요청의 언어({@code ko} 또는 {@code en}). 요청 스레드 밖이면 {@code ko} 로 본다 */
    public Locale locale() {
        return RequestLocale.normalize(LocaleContextHolder.getLocale());
    }

    public String get(MessageRef ref) {
        return get(ref, locale());
    }

    public String get(String key, Object... args) {
        return get(new MessageRef(key, args), locale());
    }

    public String get(MessageRef ref, Locale locale) {
        Locale supported = RequestLocale.normalize(locale);
        try {
            return source.getMessage(ref.key(), ref.args(), supported);
        } catch (NoSuchMessageException e) {
            log.error("메시지 키가 번들에 없다: {}", ref.key());
            return source.getMessage("error.INTERNAL", null, "", supported);
        }
    }

    /** 칸별 문구를 푼다(칸 경로 키와 순서는 그대로) */
    public Map<String, String> get(Map<String, MessageRef> fields, Locale locale) {
        Map<String, String> resolved = new LinkedHashMap<>();
        fields.forEach((field, ref) -> resolved.put(field, get(ref, locale)));
        return resolved;
    }

    private void warnOnBundleMismatch(String basename) {
        try {
            Set<String> ko = keys(basename, "ko");
            Set<String> en = keys(basename, "en");
            Set<String> onlyKo = new HashSet<>(ko);
            onlyKo.removeAll(en);
            Set<String> onlyEn = new HashSet<>(en);
            onlyEn.removeAll(ko);
            if (!onlyKo.isEmpty()) {
                log.warn("메시지 번들: en 에 없는 키 {}", onlyKo);
            }
            if (!onlyEn.isEmpty()) {
                log.warn("메시지 번들: ko 에 없는 키 {}", onlyEn);
            }
        } catch (IOException e) {
            log.warn("메시지 번들을 읽지 못해 ko·en 키 비교를 건너뛴다: {}", e.getMessage());
        }
    }

    private static Set<String> keys(String basename, String language) throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(
                new EncodedResource(new ClassPathResource(basename + "_" + language + ".properties"), StandardCharsets.UTF_8));
        return properties.stringPropertyNames();
    }
}
