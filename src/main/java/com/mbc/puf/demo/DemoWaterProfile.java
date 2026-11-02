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
    private final double calmMaxCm;
    private final Map<Long,Double> levels=new ConcurrentHashMap<>();       // 지하차도별 지난번 최종 수위
    private final Map<Long,Double> scoreLevels=new ConcurrentHashMap<>();  // 지하차도별 지난번 "위험점수가 만드는 수위"
    private final Map<Long,Double> rainTargets=new ConcurrentHashMap<>();  // 지하차도별 지난번 "강수가 만드는 수위" (평상시)
    public DemoWaterProfile(UnderpassRepository underpasses,
            @Value("${puf.risk.water.default-block-cm:15}") double referenceBlock,
            @Value("${puf.risk.water.severe-extra-cm:15}") double severeExtra,
            @Value("${puf.demo.water-rise-cm-per-step:1.5}") double maxRisePerStep,
            @Value("${puf.demo.calm-water-max-cm:2.5}") double calmMaxCm) {
        this.underpasses=underpasses;this.referenceBlock=referenceBlock;this.severeExtra=severeExtra;
        this.maxRisePerStep=Math.max(0.1,maxRisePerStep);
        this.calmMaxCm=Math.max(0,calmMaxCm);
    }
    /** 시연 위험단계 고정(DemoLevelService)이 수위를 직접 넣을 때 기억값을 맞춘다. 고정을 풀면 여기서부터 10초에 2cm씩 내려간다 */
    public void sync(Long id,double waterCm) {
        levels.put(id,waterCm);
        scoreLevels.put(id,waterCm);
    }
    /**
     * 평상시 수위(cm): 최근 강수에서 만든다. 비가 안 오면 0, 비가 오면 그만큼만 오른다.
     * latestMm = 가장 최근 관측 강수량, rain1hMm = 직전 1시간 누적. 두 값의 합(최근 1시간 누적)의 12% 이고,
     * 화면에 같이 보이는 "강우량(최근 관측)" 의 90% 를 넘지 않는다 → 수위(cm)가 강우량(mm)보다 높게 보이는 일이 없다.
     */
    public static double rainWaterCm(double latestMm,double rain1hMm) {
        double latest=Math.max(0,latestMm),acc=latest+Math.max(0,rain1hMm);
        return Math.min(0.12*acc,0.9*latest);
    }
    public OptionalDouble sample(Long id,boolean due,double scenarioWater,boolean active) {
        return sample(id,due,scenarioWater,active,OptionalDouble.empty());
    }
    /**
     * active=true 면 호우 시나리오 중. 시나리오가 아닐 때는 점수가 아무리 높아도 수위가 calmMaxCm 을 넘지 않게 한다(평상시 자동 통제 방지).
     * rainWater 가 있으면(평상시) 위험점수가 아니라 강수(rainWaterCm)에서 수위를 만든다. 없으면 지난번 값, 그것도 없으면 점수에서 만든다.
     */
    public OptionalDouble sample(Long id,boolean due,double scenarioWater,boolean active,OptionalDouble rainWater) {
        var u=underpasses.findByIdAndDeletedFalse(id).orElse(null);
        if(u==null)return OptionalDouble.empty();
        double score=u.getCurrentRiskScore()==null?0:u.getCurrentRiskScore().doubleValue();
        double previous=levels.getOrDefault(id,u.getCurrentWaterCm()==null?0:u.getCurrentWaterCm().doubleValue());
        if(!due&&score<70&&previous<=0.5)return OptionalDouble.empty();
        // 수위는 두 가지 중 큰 값이다. 1) 시나리오 곡선 2) 위험점수가 만드는 수위
        // 모든 지하차도에 동일한 물리 수위 곡선을 쓰므로, 5cm 대상은 15cm 대상보다 먼저 차단된다.
        // 점수가 만드는 수위는 한 번(보통 10초)에 maxRisePerStep(cm) 까지만 오른다. 점수가 갑자기 높아져도 수위는 서서히 따라 오른다
        double scoreTarget=target(score,referenceBlock,severeExtra);
        if(!active){
            if(rainWater.isPresent())rainTargets.put(id,rainWater.getAsDouble());
            Double rt=rainTargets.get(id);
            scoreTarget=rt!=null?Math.min(rt,calmMaxCm):Math.min(scoreTarget,calmMaxCm);
        }else rainTargets.remove(id);
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
