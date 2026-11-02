package com.mbc.puf.demo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.mbc.puf.risk.RiskPolicy;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.UnderpassRepository;
class DemoWaterProfileTest {
 RiskPolicy policy(){return new RiskPolicy(new BigDecimal("15"),new BigDecimal("5"),new BigDecimal("15"),new BigDecimal("3"),BigDecimal.ONE);}
 @Test void highScoresCreateElevatedWaterWithoutChangingFiveCentimeterBlocking(){
  var p=policy();var early=new Underpass();early.setEarlyBlock(true);var normal=new Underpass();
  assertEquals(new BigDecimal("5"),p.blockCm(early));assertTrue(p.reachedBlock(early,new BigDecimal("5")));
  assertFalse(p.reachedBlock(early,new BigDecimal("4.9")));
  assertTrue(DemoWaterProfile.target(55,15,15)<5);assertTrue(DemoWaterProfile.target(80,15,15)>5);assertTrue(DemoWaterProfile.target(80,15,15)<15);assertTrue(DemoWaterProfile.target(99,15,15)>=29.9);
  double last=0;for(int score=0;score<=100;score++){double next=DemoWaterProfile.target(score,15,15);assertTrue(next>=last);last=next;}
 }
 @Test void readsRealScoreEvenWhenModelStubIsOffAndDrainsGradually(){
  var repo=mock(UnderpassRepository.class);var u=new Underpass();u.setEarlyBlock(true);u.setCurrentRiskScore(new BigDecimal("99"));
  when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(u));
  var profile=new DemoWaterProfile(repo,15,15,1.5,2.5);double first=profile.sample(1L,false,0,true).orElseThrow();
  assertEquals(1.5,first,.001);   // 점수가 갑자기 99 가 되어도 수위는 한 번에 1.5cm 만 오른다
  double high=first;for(int i=0;i<19;i++)high=profile.sample(1L,false,0,true).orElseThrow();
  assertTrue(high>20);u.setCurrentRiskScore(BigDecimal.ZERO);double drained=profile.sample(1L,false,0,true).orElseThrow();assertEquals(high-2,drained,.001);
  for(int i=0;i<20;i++)profile.sample(1L,true,0,true);assertTrue(profile.sample(1L,true,0,true).orElseThrow()<.5);
 }
 @Test void calmWeatherNeverRaisesWaterAboveCalmMaxEvenWithHighScore(){
  var repo=mock(UnderpassRepository.class);var u=new Underpass();u.setEarlyBlock(true);u.setCurrentRiskScore(new BigDecimal("99"));
  when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(u));
  var profile=new DemoWaterProfile(repo,15,15,1.5,2.5);double v=0;for(int i=0;i<60;i++)v=profile.sample(1L,false,0,false).orElseThrow();
  assertEquals(2.5,v,.001);assertTrue(v<5);   // 점수가 99 여도 평상시에는 2.5cm 에서 멈춘다 (5cm 자동 통제 안 됨)
 }
 @Test void honorsScenarioPeakAndLeavesCalmSamplingCadence(){
  var repo=mock(UnderpassRepository.class);var u=new Underpass();when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(u));
  var profile=new DemoWaterProfile(repo,15,15,1.5,2.5);assertTrue(profile.sample(1L,false,0,true).isEmpty());
  assertEquals(33,profile.sample(1L,true,33,true).orElseThrow(),.001);
 }
 @Test void calmWaterFollowsRainAndStaysBelowObservedRain(){
  var repo=mock(UnderpassRepository.class);var u=new Underpass();u.setEarlyBlock(true);u.setCurrentRiskScore(new BigDecimal("99"));
  when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(u));
  var profile=new DemoWaterProfile(repo,15,15,1.5,2.5);double v=0;
  for(int i=0;i<10;i++)v=profile.sample(1L,true,0,false,java.util.OptionalDouble.of(DemoWaterProfile.rainWaterCm(0,0))).orElseThrow();
  assertEquals(0,v,.001);   // 점수가 99 여도 비가 없으면 수위는 0
  for(int i=0;i<10;i++)v=profile.sample(1L,true,0,false,java.util.OptionalDouble.of(DemoWaterProfile.rainWaterCm(1.0,8))).orElseThrow();
  assertEquals(0.9,v,.001);assertTrue(v<=1.0);   // 소나기: 수위(cm)가 관측 강우량(mm)보다 낮다
  assertEquals(0,DemoWaterProfile.rainWaterCm(0,5),0);   // 방금 비가 그쳤으면 수위도 바로 0 쪽
 }
}
