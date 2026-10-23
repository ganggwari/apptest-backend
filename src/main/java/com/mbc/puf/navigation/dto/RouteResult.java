package com.mbc.puf.navigation.dto;

import java.util.List;

/**
 * 프런트로 내려주는 최종 경로.
 *
 * @param detoured        위험지점을 피해 우회한 경로인지 (필수 우회로 바꿔 준 경우)
 * @param mandatory       차단(경계 이상)된 지하차도 때문에 우회가 필수였는지
 * @param waypoint        우회에 쓴 경유지 {lat, lng} (우회 아니면 null)
 * @param passesHazards   원래 경로가 근처를 지나는 위험지점(관심 이상) 이름 전체
 * @param blockedHazards  그중 차단(경계 이상) 상태인 곳
 * @param advisoryHazards 그중 관심·주의 단계인 곳 (우회 권유 대상)
 * @param message         화면 로그/음성에 쓸 설명
 * @param suggestedDetour 관심·주의 지하차도만 있을 때 "원하면 이 경로로 우회" 제안 (없거나 못 찾으면 null)
 */
public record RouteResult(int distance,
                          int duration,
                          List<double[]> path,
                          List<NaviRoute.Guide> guides,
                          boolean detoured,
                          boolean mandatory,
                          double[] waypoint,
                          List<String> passesHazards,
                          List<String> blockedHazards,
                          List<String> advisoryHazards,
                          String message,
                          SuggestedDetour suggestedDetour) {

    /** 우회 권유 경로. 사용자가 받아들이면 이 path/guides 로 안내를 갈아탄다 */
    public record SuggestedDetour(int distance,
                                  int duration,
                                  int extraSec,
                                  List<double[]> path,
                                  List<NaviRoute.Guide> guides,
                                  double[] waypoint,
                                  String message) {
    }
}
