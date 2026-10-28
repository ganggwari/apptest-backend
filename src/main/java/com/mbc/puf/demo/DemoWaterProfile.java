package com.mbc.puf.demo;

import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import com.mbc.puf.underpass.repository.UnderpassRepository;

/** 시연 수위 생성에만 적용한다. 실제 점수/단계 판단 및 모델 입력은 변경하지 않는다. */
@Component
@ConditionalOnProperty(name="puf.demo.enabled",havingValue="true")
public class DemoWaterProfile {
    private final UnderpassRepository underpasses;
    private final double referenceBlock;
    private final double severeExtra;
    private final double maxRisePerStep;
    private final Map<Long,Double> levels=new ConcurrentHashMap<>();       // 지하차도별 지난번 최종 수위
    private final Map<Long,Double> scoreLevels=new ConcurrentHashMap<>();  // 지하차도별 지난번 "위험점수가 만드는 수위"
    public DemoWaterProfile(UnderpassRepository underpasses,
            @Value("${puf.risk.water.default-block-cm:15}") double referenceBlock,
            @Value("${puf.risk.water.severe-extra-cm:15}") double severeExtra,
            @Value("${puf.demo.water-rise-cm-per-step:1.5}") double maxRisePerStep) {
        this.underpasses=underpasses;this.referenceBlock=referenceBlock;this.severeExtra=severeExtra;
        this.maxRisePerStep=Math.max(0.1,maxRisePerStep);
    }
    public OptionalDouble sample(Long id,boolean due,double scenarioWater) {
        var u=underpasses.findByIdAndDeletedFalse(id).orElse(null);
        if(u==null)return OptionalDouble.empty();
        double score=u.getCurrentRiskScore()==null?0:u.getCurrentRiskScore().doubleValue();
        double previous=levels.getOrDefault(id,u.getCurrentWaterCm()==null?0:u.getCurrentWaterCm().doubleValue());
        if(!due&&score<70&&previous<=0.5)return OptionalDouble.empty();
        // 수위는 두 가지 중 큰 값이다. 1) 시나리오 곡선 2) 위험점수가 만드는 수위
        // 모든 지하차도에 동일한 물리 수위 곡선을 쓰므로, 5cm 대상은 15cm 대상보다 먼저 차단된다.
        // 점수가 만드는 수위는 한 번(보통 10초)에 maxRisePerStep(cm) 까지만 오른다. 점수가 갑자기 높아져도 수위는 서서히 따라 오른다
        double scoreTarget=target(score,referenceBlock,severeExtra);
        double previousScoreWater=scoreLevels.getOrDefault(id,previous);
        double scoreWater=scoreTarget>=previousScoreWater
                ?Math.min(scoreTarget,previousScoreWater+maxRisePerStep)
                :Math.max(scoreTarget,previousScoreWater-2.0);
        scoreLevels.put(id,scoreWater);
        double target=Math.max(scenarioWater,scoreWater);
        double next=target>=previous?target:Math.max(target,previous-2.0);   // 내려갈 때는 한 번에 2cm 까지만
        levels.put(id,next);
        return OptionalDouble.of(next);
    }
    // 위험점수 → 수위(cm). 점수 70(주의 시작)까지는 3cm 이하, 85 에서 7.5cm, 95 에서 18cm 로 점수가 아주 높아야 차단 수위(5cm/15cm)를 넘는다.
    // 그래서 점수가 조금 올랐다고 바로 자동 차단되지 않는다
    static double target(double score,double block,double extra) {
        double s=Math.max(0,Math.min(100,score));
        double[] x={0,40,70,85,95,100};
        double[] y={0.2,block*0.08,block*0.2,block*0.5,block*1.2,block+extra+3};
        for(int i=1;i<x.length;i++)if(s<=x[i])return y[i-1]+(y[i]-y[i-1])*(s-x[i-1])/(x[i]-x[i-1]);
        return y[y.length-1];
    }
}
