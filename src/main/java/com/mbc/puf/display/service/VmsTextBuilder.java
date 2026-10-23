package com.mbc.puf.display.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mbc.puf.common.enums.RiskLevel;

/**
 * 전광판에 띄울 문구를 만드는 규칙 모음 (DB·스프링 없이 순수 계산만 한다).
 *
 * 문구 규칙 (VMS 자동 표출 로직 PPT 기준)
 *  - 읽는 순서: 장소 → 상황 → 행동. 3줄 이상 전광판은 줄마다 하나씩, 2줄 전광판은 장소 + (상황 행동)
 *  - 한 줄은 전광판 열 수(예: 2단10열이면 10자) 이하. 넘으면 이름의 "지하차도"를 "지하"로 줄이고, 그래도 넘치면 자른다
 *  - 색: 통제 빨강 / 주의·관심 노랑 / 해제 초록
 *  - 우선순위: 통제 > 주의 > 관심 > 해제 안내 (페이지 개수 제한은 두지 않는다)
 * 줄바꿈은 "\n" 으로 이어 붙여 display_page.content 한 칸에 저장한다. 프런트는 "\n" 으로 나눠서 줄마다 그린다.
 */
public final class VmsTextBuilder {

    /** 페이지 종류. 선언 순서가 곧 우선순위(앞이 먼저 보인다) */
    public enum Kind {
        BLOCK("RED"),
        CAUTION("YELLOW"),
        WATCH("YELLOW"),
        RELEASE("GREEN");

        private final String color;

        Kind(String color) {
            this.color = color;
        }

        public String color() {
            return color;
        }
    }

    private static final Pattern SPEC = Pattern.compile("(\\d+)\\s*단\\s*(\\d+)\\s*열");
    private static final int DEFAULT_ROWS = 2;
    private static final int DEFAULT_COLS = 10;

    private VmsTextBuilder() {
    }

    /**
     * 지하차도의 지금 상태로 페이지 종류를 정한다. 띄울 게 없으면 null.
     *
     * @param blocked    차단 중인지
     * @param level      현재 위험단계
     * @param releasedAt 가장 최근 해제 시각(없으면 null)
     * @param now        지금(DB 시계)
     * @param releaseMin 해제 안내를 보여주는 시간(분)
     * @param minLevel   이 단계부터 표출 (예: WATCH 면 관심부터)
     */
    public static Kind kindOf(boolean blocked, RiskLevel level, LocalDateTime releasedAt, LocalDateTime now,
                              int releaseMin, RiskLevel minLevel) {
        if (blocked || level.atLeast(RiskLevel.ALERT)) {
            return Kind.BLOCK;
        }
        if (releasedAt != null && now.isBefore(releasedAt.plusMinutes(releaseMin))) {
            return Kind.RELEASE;
        }
        if (!level.atLeast(minLevel)) {
            return null;
        }
        if (level == RiskLevel.CAUTION) {
            return Kind.CAUTION;
        }
        if (level == RiskLevel.WATCH) {
            return Kind.WATCH;
        }
        return null;
    }

    /**
     * 문구 만들기.
     *
     * @param underpassName 지하차도 이름
     * @param spec          전광판 규격 (예: "2단10열", 모르면 null)
     * @param kind          페이지 종류
     * @param near          근접 전광판인지 (통제일 때 근접은 "통행 금지", 원거리는 "우회하세요")
     */
    public static String build(String underpassName, String spec, Kind kind, boolean near) {
        int rows = DEFAULT_ROWS;
        int cols = DEFAULT_COLS;
        if (spec != null) {
            Matcher m = SPEC.matcher(spec);
            if (m.find()) {
                rows = Integer.parseInt(m.group(1));
                cols = Integer.parseInt(m.group(2));
            }
        }
        String place = fit(shortName(underpassName, cols), cols);

        // 상황·행동 후보. 위가 가장 친절한 표현이고, 열이 모자라면 아래 후보로 내려간다
        List<String[]> candidates = switch (kind) {
            case BLOCK -> near
                    ? List.of(new String[] {"통제 중", "통행 금지"}, new String[] {"통제중", "통행금지"}, new String[] {"통제", "금지"})
                    : List.of(new String[] {"통제 중", "우회하세요"}, new String[] {"통제중", "우회"}, new String[] {"통제", "우회"});
            case CAUTION -> List.of(new String[] {"수위 상승 중", "서행하세요"}, new String[] {"수위상승", "서행하세요"},
                    new String[] {"수위상승", "서행"});
            case WATCH -> List.of(new String[] {"비 내리는 중", "안전운전"}, new String[] {"비 내림", "안전운전"}, new String[] {"비내림", "안전운전"}, new String[] {"비", "안전운전"});
            case RELEASE -> List.of(new String[] {"통행 재개", "서행 통과"}, new String[] {"통행재개", "서행통과"}, new String[] {"재개", "서행"});
        };

        if (rows >= 3) {
            // 줄마다 하나씩: 장소 / 상황 / 행동 (모두 열 수 안에 들어가는 첫 후보)
            String[] pick = candidates.get(candidates.size() - 1);
            for (String[] c : candidates) {
                if (c[0].length() <= cols && c[1].length() <= cols) {
                    pick = c;
                    break;
                }
            }
            return place + "\n" + fit(pick[0], cols) + "\n" + fit(pick[1], cols);
        }

        // 2줄: 장소 / "상황 행동" 한 줄 (열 안에 들어가는 첫 후보, 없으면 마지막 후보를 자른다)
        String merged = null;
        for (String[] c : candidates) {
            String line = c[0] + " " + c[1];
            if (line.length() <= cols) {
                merged = line;
                break;
            }
        }
        if (merged == null) {
            String[] last = candidates.get(candidates.size() - 1);
            merged = fit(last[0] + " " + last[1], cols);
        }
        return place + "\n" + merged;
    }

    /** 한 줄이 열 수보다 길면 이름의 "지하차도"를 "지하"로 줄인다 */
    static String shortName(String name, int cols) {
        String n = name == null ? "" : name.trim();
        if (n.length() > cols) {
            n = n.replace("지하차도", "지하");
        }
        return n;
    }

    private static String fit(String line, int cols) {
        return line.length() <= cols ? line : line.substring(0, cols);
    }
}
