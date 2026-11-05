package com.community.emoticon.service;

import com.community.emoticon.exception.EmoticonNotOwnedException;
import com.community.emoticon.repository.master.EmoticonAcquisitionStore;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigInteger;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글·댓글 본문의 로보티콘 토큰 검사 — <b>한 곳</b>(계약 「목업 5차」, user-web {@code util-emoticon-rule.ts} 와 같은 규칙). 검사 지점 = 글쓰기·글 고치기·소모임 글쓰기·댓글 쓰기·고치기.
 *
 * <ol>
 *   <li><b>개수</b>({@link #countInto}): 정규식 {@code :emo-(\d{1,19}):} 를 <b>본문 전체 글자</b>(HTML 이면 정화된 HTML 전체 — 링크·코드 안도)에서 센다. 같은 토큰 여러 번 · 없는 ID ·
 *       long 범위를 넘는 19자리 ID 도 센다. 21개부터 {@code 400 VALIDATION fields.content} — 그 칸에 다른 오류가 이미 있으면 그 오류가 남는다. 입력 오류와 함께 모은다.</li>
 *   <li><b>자격</b>({@link #requireOwned}): 입력 오류가 없을 때, 본문 순서로 long 범위 안 + 카탈로그(상점에 나왔던 팩의 상점 판)에 있는 로보티콘이 작성자가 지금 가진 팩의 것이 아니면
 *       {@code 409 EMOTICON_NOT_OWNED}(최상위 {@code emoticonId} = 처음 걸린 것). 없는 ID·범위 밖 ID 는 글자라 오류가 아니다. 비회원(작성자 없음)은 아무 팩도 가지지 않았다.
 *       <b>운영진·최고 관리자 면제는 없다</b>(회원과 같이 가진 팩만). 「가진 팩」에는 작가 자신이 만든 상점 팩이 들어간다(후속 결정 1 — {@link EmoticonAcquisitionStore#ownedAmong}).</li>
 *   <li><b>사용함</b>({@link #recordUse} — S3): 저장이 성공하는 <b>같은 트랜잭션</b>에서(글·소모임 글·댓글의 행을 쓴 뒤) 본문 토큰의 팩마다 그 회원이 산(보유 중인) 구매에 처음 쓴
 *       시각을 찍고, 같은 트랜잭션에서 자격을 한 번 더 본다 — 검사와 저장 사이에 환불·빼기가 끼어들면 409 로 저장 전체가 되돌아간다(환불은 구매 행을 잠그고 「안 씀」을 보므로
 *       찍힌 뒤의 셀프 환불은 막힌다). 고르기 창에서 넣기만 하고 저장하지 않으면 찍히지 않는다.</li>
 * </ol>
 * 오류 차례는 입력 오류(400, 개수 포함) → 자격(409) → 금지어·속도 제한(부르는 서비스가 그다음에 본다).
 */
@Component
public class EmoticonGuard {

    /** 토큰 하나 — ID 는 1~19자리 숫자 */
    public static final Pattern TOKEN = Pattern.compile(":emo-(\\d{1,19}):");
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final EmoticonReadStore reads;
    private final EmoticonAcquisitionStore acquisitions;
    private final EmoticonPurchaseStore purchases;
    private final Clock clock;

    public EmoticonGuard(EmoticonReadStore reads, EmoticonAcquisitionStore acquisitions, EmoticonPurchaseStore purchases, ObjectProvider<Clock> clocks) {
        this.reads = reads;
        this.acquisitions = acquisitions;
        this.purchases = purchases;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 「사용함」 기록 — 저장 트랜잭션 안에서, 글·댓글 행을 쓴 뒤에 부른다(규칙은 클래스 설명). 찍은 구매 수.
     *
     * @param authorId 작성자(비회원이면 null — 할 일 없음)
     * @throws EmoticonNotOwnedException 그사이 환불·빼기로 가지지 않게 된 팩의 로보티콘이 있으면(저장 전체가 되돌아간다)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int recordUse(Long authorId, String content) {
        if (authorId == null) {
            return 0;
        }
        List<Long> ids = ids(content);
        if (ids.isEmpty()) {
            return 0;
        }
        Map<Long, Long> packOf = reads.packsOfEmoticons(new LinkedHashSet<>(ids));
        if (packOf.isEmpty()) {
            return 0;
        }
        int stamped = purchases.markUsed(authorId, new LinkedHashSet<>(packOf.values()), clock.instant().truncatedTo(ChronoUnit.MICROS));
        requireOwned(authorId, content);
        return stamped;
    }

    /** 토큰 수(같은 토큰 여러 번 · 없는 ID · 범위 밖 ID 도) */
    public static int count(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        Matcher m = TOKEN.matcher(content);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 본문 순서의 토큰 ID 중 long 으로 읽히는 것(겹치면 겹친 대로) */
    public static List<Long> ids(String content) {
        List<Long> out = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return out;
        }
        Matcher m = TOKEN.matcher(content);
        while (m.find()) {
            String digits = m.group(1);
            if (digits.length() < 19 || new BigInteger(digits).compareTo(LONG_MAX) <= 0) {
                out.add(Long.parseLong(digits));
            }
        }
        return out;
    }

    /** 개수 검사 — 넘으면 {@code fields.<field>}(그 칸에 오류가 이미 있으면 그대로 둔다) */
    public static void countInto(String content, String field, Map<String, MessageRef> fields) {
        if (count(content) > EmoticonSpec.TOKENS_PER_BODY && !fields.containsKey(field)) {
            fields.put(field, new MessageRef("emoticon.body.tooMany", EmoticonSpec.TOKENS_PER_BODY));
        }
    }

    /**
     * 자격 검사 — 개수 검사를 지난 본문에서 부른다.
     *
     * @param authorId 작성자(비회원이면 null — 아무 팩도 가지지 않았다)
     * @throws EmoticonNotOwnedException 가지지 않은 팩의 로보티콘이 있으면
     */
    public void requireOwned(Long authorId, String content) {
        List<Long> ids = ids(content);
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Long> packOf = reads.packsOfEmoticons(new LinkedHashSet<>(ids));
        if (packOf.isEmpty()) {
            return;
        }
        Set<Long> owned = authorId == null ? Set.of() : acquisitions.ownedAmong(authorId, new LinkedHashSet<>(packOf.values()));
        for (Long id : ids) {
            Long pack = packOf.get(id);
            if (pack != null && !owned.contains(pack)) {
                throw new EmoticonNotOwnedException(id);
            }
        }
    }
}
