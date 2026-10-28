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
  var profile=new DemoWaterProfile(repo,15,15,1.5);double first=profile.sample(1L,false,0).orElseThrow();
  assertEquals(1.5,first,.001);   // 점수가 갑자기 99 가 되어도 수위는 한 번에 1.5cm 만 오른다
  double high=first;for(int i=0;i<19;i++)high=profile.sample(1L,false,0).orElseThrow();
  assertTrue(high>20);u.setCurrentRiskScore(BigDecimal.ZERO);double drained=profile.sample(1L,false,0).orElseThrow();assertEquals(high-2,drained,.001);
  for(int i=0;i<20;i++)profile.sample(1L,true,0);assertTrue(profile.sample(1L,true,0).orElseThrow()<.5);
 }
 @Test void honorsScenarioPeakAndLeavesCalmSamplingCadence(){
  var repo=mock(UnderpassRepository.class);var u=new Underpass();when(repo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(u));
  var profile=new DemoWaterProfile(repo,15,15,1.5);assertTrue(profile.sample(1L,false,0).isEmpty());
  assertEquals(33,profile.sample(1L,true,33).orElseThrow(),.001);
 }
}
