package com.mbc.puf.common.pdf;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Component;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.AreaBreakType;
import com.mbc.puf.pm.dto.*;
import com.mbc.puf.common.enums.PmEventStatus;
import static com.mbc.puf.common.pdf.PdfVisuals.*;

@Component
public class PmDailyReportPdfGenerator {
 private static final DateTimeFormatter DT=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
 public byte[] generate(PmDailyReport report){
  var out=new ByteArrayOutputStream();
  try(var pdf=new PdfDocument(new PdfWriter(out));var doc=new Document(pdf)){
   doc.setMargins(40,40,65,40);PdfFont f=font();footer(pdf,f);var s=report.summary();
   title(doc,f,"데일리 리포트",report.date()+" · 집계 "+fmt(s.from())+" ~ "+fmt(s.to())+" · 생성 "+fmt(report.generatedAt()));
    if(s.control()!=null){var control=s.control();section(doc,f,"01  지하차도 차단·해제 · 차단 "+control.blockCount()+"건 / 해제 "+control.releaseCount()+"건");
     var data=new ArrayList<String[]>();for(var e:nz(control.events()))data.add(new String[]{fmt(e.createdAt()),dash(e.underpassName()),"BLOCK".equals(e.type())?"차단":"해제",e.automatic()?"자동":dash(e.operatorName()),dash(e.reason())+(e.reasonNote()==null?"":" · "+e.reasonNote())});
     rows(doc,f,new float[]{2,2,1,1.5f,4},new String[]{"발생 시각","지하차도","구분","담당","사유"},data);limit(doc,f,control.blockCount()+control.releaseCount(),data.size());
    }
   if(s.control()==null) {section(doc,f,"01  지하차도 차단·해제");doc.add(p("통제 집계 데이터가 없습니다.",f,10));}
   doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
   title(doc,f,"설비 현황 요약","설비 단계 분포 · 설비 종류별 현황 · 점검 요약");
   doc.add(summary(pdf,f,s));
   doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
   title(doc,f,"우선 점검 및 미수신 현황","점검 권장 설비 · 최근 수신 정보 · 데이터 미수신 현황");
   section(doc,f,"05  우선 점검 대상");equipment(doc,f,s.recommended());
   section(doc,f,"06  데이터 미수신 설비");equipment(doc,f,s.stale());
   doc.add(p("최근 점수는 과거 수신값입니다. 현재 단계는 미수신으로 집계합니다.",f,9).setFontColor(MUTED));
   doc.add(p("점수 수신 이력이 전혀 없는 설비: "+s.neverReceived()+"대 (위 목록과 별도)",f,10));
   doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
   title(doc,f,"점검 이벤트 및 조치 기록","집계 기간 내 신규 점검 · 조치 이력 · 미완료 점검 현황");
   section(doc,f,"07  오늘 새로 발생한 점검 대기 · "+s.newEventCount()+"건");events(doc,f,s.newEvents());
   section(doc,f,"08  오늘의 조치 기록 · "+s.actionCount()+"건");
   var actions=new ArrayList<String[]>();for(var a:nz(s.actions()))actions.add(new String[]{fmt(a.getCreatedAt()),label(a.getName(),a.getEquipId())+" / "+dash(a.getUnderpassName()),dash(a.getManagerName()),"["+dash(a.getKindLabel())+"] "+dash(a.getContent()),num(a.getScoreBefore())+" → "+num(a.getScoreAfter())});
   rows(doc,f,new float[]{2,3,2,5,2},new String[]{"시각","설비 / 지하차도","등록자","조치 내용","점수 변화"},actions);
   section(doc,f,"09  아직 처리 중인 점검 · "+s.openEventCount()+"건");events(doc,f,s.openEvents());
   doc.add(p("신규 점검과 미처리 점검에는 같은 사건이 포함될 수 있습니다.",f,8).setFontColor(MUTED));
   if(s.sinkhole()!=null){
    doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));title(doc,f,"싱크홀 분석 및 조치 기록","영상 분석 결과 · 확인 및 조치 · 미처리 현황");
    if(s.sinkhole()!=null){var road=s.sinkhole();section(doc,f,"10  노면 싱크홀 분석·탐지");
     doc.add(p("분석 "+road.analysisCount()+"건 · 의심 탐지 "+road.detectedCount()+"건 · 신규 사건 "+road.newEventCount()+"건 · 미처리 "+road.openEventCount()+"건",f,10));
     doc.add(p("신뢰도는 영상 탐지 확률이며 설비 고장 점수가 아닙니다. 판정·미처리 상태는 조회 시점 기준입니다.",f,8).setFontColor(MUTED));
     var data=new ArrayList<String[]>();for(var r:nz(road.results()))data.add(roadRow(r));
     rows(doc,f,new float[]{2,3,2,1,2},new String[]{"분석 시각","설비 / 지하차도","결과","신뢰도","확인·조치"},data);limit(doc,f,road.analysisCount(),data.size());
     section(doc,f,"현재 미처리 싱크홀 · "+road.openEventCount()+"건");data.clear();for(var r:nz(road.openIncidents()))data.add(roadRow(r));
     rows(doc,f,new float[]{2,3,2,1,2},new String[]{"분석 시각","설비 / 지하차도","결과","신뢰도","확인·조치"},data);limit(doc,f,road.openEventCount(),data.size());
    }

   }
  }catch(Exception e){throw new RuntimeException("데일리 리포트 PDF 생성 실패",e);}return out.toByteArray();
 }
 private void equipment(Document d,PdfFont f,List<PmReportEquipment> list){var data=new ArrayList<String[]>();for(var e:nz(list))data.add(new String[]{label(e.getName(),e.getEquipId()),dash(e.getUnderpassName()),e.getLevel()==null?"-":e.getLevel().getDbValue(),num(e.getScore()),fmt(e.getLastReceivedAt()),status(e.getOpenEventStatus())});rows(d,f,new float[]{2,3,1,1,2,2},new String[]{"설비","지하차도","단계","점수","최근 수신","점검 상태"},data);}
 private void events(Document d,PdfFont f,List<PmEventItem> list){var data=new ArrayList<String[]>();for(var e:nz(list))data.add(new String[]{fmt(e.getCreatedAt()),label(e.getName(),e.getEquipId())+" / "+dash(e.getUnderpassName()),e.getTriggerType()==null?"-":e.getTriggerType().getDbValue(),num(e.getScore()),num(e.getCurrentScore()),status(e.getStatus())});rows(d,f,new float[]{2,3,2,1,1,2},new String[]{"발생 시각","설비 / 지하차도","발생 이유","발생 점수","현재 점수","상태"},data);}
 private void rows(Document d,PdfFont f,float[] widths,String[] headers,List<String[]> data){Table t=table(widths);for(String h:headers)t.addHeaderCell(cell(h,f,true));for(String[]row:data)for(String v:row)t.addCell(cell(v,f,false));if(data.isEmpty())t.addCell(new Cell(1,headers.length).add(p("해당 내역이 없습니다.",f,9)).setPadding(12));d.add(t);}
 private String[] roadRow(PmSinkholeSummary.Item r){return new String[]{fmt(r.analyzedAt()),dash(r.name())+" / "+dash(r.underpassName()),r.detected()?"싱크홀 의심":"이상 미탐지",r.confidence()==null?"-":r.confidence().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()+"%",roadStatus(r)};}
 private String roadStatus(PmSinkholeSummary.Item r){if(!r.detected())return "이상 미탐지";return switch(r.reviewStatus()){case "FALSE_POSITIVE"->"오탐 확인";case "RESOLVED"->"조치 완료";case "CONFIRMED"->"싱크홀 확인";default->"확인 필요";};}
 private void limit(Document d,PdfFont f,long total,int shown){if(total>shown)d.add(p("최근 "+shown+"건 표시 / 전체 "+total+"건",f,8).setFontColor(MUTED));}
 private String status(PmEventStatus s){return s==null?"-":switch(s){case CHECK_NEEDED->"점검 필요";case IN_PROGRESS->"점검 중";case RESOLVED->"조치 완료";case ETC->"기타 종료";};}
 private static <T> List<T> nz(List<T> l){return l==null?List.of():l;}
 private String label(String n,Long id){return n==null||n.isBlank()?"설비 #"+id:n;}
 private String dash(String s){return s==null||s.isBlank()?"-":s;}
 private String num(BigDecimal n){return n==null?"-":n.stripTrailingZeros().toPlainString();}
 private String fmt(LocalDateTime t){return t==null?"-":t.format(DT);}
}
