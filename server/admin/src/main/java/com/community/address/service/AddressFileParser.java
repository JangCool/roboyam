package com.community.address.service;

import com.community.address.exception.AddressImportFailure;

/**
 * 행정안전부 도로명주소 개발자센터(business.juso.go.kr) 「주소 DB」 원본 한 줄 읽기 — pionworld2 {@code core.address.service.AddressFileParser} 를 옮겼다
 * (공식 「도로명주소한글활용가이드」·「도로명주소영문활용가이드」 레이아웃으로 검증된 칸 위치). 세 파일 모두 헤더 없음 · 파이프({@code |}) 구분 · 고정 칸 순서다.
 * <ul>
 *   <li>{@code rnaddrkor_<지역>.txt} — 도로명주소 {@value #ROAD_COLUMNS}칸. 지번은 없다(관리번호로 관련지번 파일과 잇는다).</li>
 *   <li>{@code jibun_rnaddrkor_<지역>.txt} — 관련지번 {@value #JIBUN_COLUMNS}칸. 도로명주소 1건에 여러 줄(집합건물 등).</li>
 *   <li>{@code rneng_<지역>.txt} — 도로명주소 영문 {@value #ENGLISH_COLUMNS}칸. 관리번호가 한글과 같다.</li>
 * </ul>
 * 바꾼 것: 칸 수가 모자란 줄은 조용히 건너뛰지 않고 적재를 멈춘다(다른 파일을 올렸을 때 반쯤 빈 DB 로 바뀌지 않게). 관리번호가 빈 줄만 건너뛴다.
 */
public final class AddressFileParser {

    static final int ROAD_COLUMNS = 24;
    static final int JIBUN_COLUMNS = 14;
    static final int ENGLISH_COLUMNS = 17;

    /** 스테이징 표 한 줄 — 지번·영문은 나중에 SQL 로 붙인다 */
    public record RoadRow(String mgmtNo, String zipcode, String sido, String sigungu, String eupmyeondong, String roadName, int buildingNoMain,
                          int buildingNoSub, String buildingName, String roadAddress, String searchText) {
    }

    public record JibunRow(String mgmtNo, String beopjeongDong, String beopjeongRi, boolean mountain, int jibunMain, int jibunSub) {
    }

    public record EnglishRow(String mgmtNo, String roadAddressEn) {
    }

    private AddressFileParser() {
    }

    /** 도로명주소 한 줄 — 관리번호가 비었으면 null(건너뜀), 칸이 모자라면 적재 실패 */
    public static RoadRow road(String line, String file, long lineNo) {
        String[] c = columns(line, ROAD_COLUMNS, file, lineNo);
        String mgmtNo = c[0].strip();
        if (mgmtNo.isEmpty()) {
            return null;
        }
        String sido = c[2].strip();
        String sigungu = c[3].strip();
        String dong = c[4].strip();
        String ri = c[5].strip();
        String roadName = c[10].strip();
        int underground = intOrZero(c[11]);
        int main = intOrZero(c[12]);
        int sub = intOrZero(c[13]);
        String zipcode = c[16].strip();
        boolean apartment = "1".equals(c[19].strip());
        String registerName = c[21].strip();
        String sigunguBuildingName = c[22].strip();

        boolean hasRi = !ri.isEmpty();
        String buildingName = !sigunguBuildingName.isEmpty() ? sigunguBuildingName : registerName;
        String roadAddress = roadAddress(sido, sigungu, dong, hasRi, roadName, underground, main, sub, apartment, buildingName);
        String eupmyeondong = hasRi ? (dong + " " + ri).strip() : dong;
        String searchText = collapse(roadAddress + " " + eupmyeondong + " " + buildingName + " " + zipcode);
        return new RoadRow(mgmtNo, zipcode, sido, sigungu, eupmyeondong, roadName, main, sub, buildingName, roadAddress, searchText);
    }

    /** 관련지번 한 줄 */
    public static JibunRow jibun(String line, String file, long lineNo) {
        String[] c = columns(line, JIBUN_COLUMNS, file, lineNo);
        String mgmtNo = c[0].strip();
        if (mgmtNo.isEmpty()) {
            return null;
        }
        return new JibunRow(mgmtNo, c[4].strip(), c[5].strip(), "1".equals(c[6].strip()), intOrZero(c[7]), intOrZero(c[8]));
    }

    /** 영문 한 줄 */
    public static EnglishRow english(String line, String file, long lineNo) {
        String[] c = columns(line, ENGLISH_COLUMNS, file, lineNo);
        String mgmtNo = c[0].strip();
        if (mgmtNo.isEmpty()) {
            return null;
        }
        return new EnglishRow(mgmtNo, englishAddress(c[2].strip(), c[3].strip(), c[4].strip(), c[5].strip(), c[7].strip(), intOrZero(c[8]), intOrZero(c[9]),
                intOrZero(c[10])));
    }

    /**
     * 도로명주소 표기(「도로명주소한글활용가이드」 붙임1): 시·도 + 시·군·구 + (법정리가 있으면 읍·면) + 도로명 + (지하·공중·수상)건물번호 + (참고항목).
     * 참고항목은 공동주택이면 건물 이름(법정리가 없으면 앞에 동 이름), 아니면 법정리가 없을 때만 동 이름.
     */
    static String roadAddress(String sido, String sigungu, String dong, boolean hasRi, String roadName, int underground, int main, int sub, boolean apartment,
                              String buildingName) {
        StringBuilder text = new StringBuilder(sido);
        if (!sigungu.isEmpty()) {
            text.append(' ').append(sigungu);
        }
        if (hasRi && !dong.isEmpty()) {
            text.append(' ').append(dong);
        }
        text.append(' ').append(roadName).append(' ').append(switch (underground) {
            case 1 -> "지하";
            case 2 -> "공중";
            case 3 -> "수상";
            default -> "";
        }).append(main);
        if (sub > 0) {
            text.append('-').append(sub);
        }
        String reference = reference(dong, hasRi, apartment, buildingName);
        if (!reference.isEmpty()) {
            text.append(" (").append(reference).append(')');
        }
        return text.toString().strip();
    }

    private static String reference(String dong, boolean hasRi, boolean apartment, String buildingName) {
        if (!apartment) {
            return !hasRi && !dong.isEmpty() ? dong : "";
        }
        if (dong.isEmpty() || hasRi) {
            return buildingName;
        }
        return buildingName.isEmpty() ? dong : dong + ", " + buildingName;
    }

    /**
     * 영문 표기(「도로명주소영문활용가이드」 붙임1 예: 87 Yeonghwasa-ro, Gwangjin-gu, Seoul): (지하 B·수상 W)건물번호 + 도로명, (법정리가 있으면 읍·면), 시·군·구, 시·도.
     */
    static String englishAddress(String sido, String sigungu, String eupmyeondong, String ri, String roadName, int underground, int main, int sub) {
        StringBuilder text = new StringBuilder(switch (underground) {
            case 1 -> "B";
            case 3 -> "W";
            default -> "";
        }).append(main);
        if (sub > 0) {
            text.append('-').append(sub);
        }
        text.append(' ').append(roadName);
        if (!ri.isEmpty() && !eupmyeondong.isEmpty()) {
            text.append(", ").append(eupmyeondong);
        }
        if (!sigungu.isEmpty()) {
            text.append(", ").append(sigungu);
        }
        if (!sido.isEmpty()) {
            text.append(", ").append(sido);
        }
        return text.toString();
    }

    private static String[] columns(String line, int expected, String file, long lineNo) {
        String[] c = line.split("\\|", -1);
        if (c.length < expected) {
            throw new AddressImportFailure("address.import.badLine", file, lineNo, expected, c.length);
        }
        return c;
    }

    private static int intOrZero(String value) {
        String text = value.strip();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String collapse(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }
}
