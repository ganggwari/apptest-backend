package com.mbc.puf.common.pdf;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.*;
import com.itextpdf.kernel.colors.*;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.pdf.event.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import com.itextpdf.layout.borders.*;
import org.springframework.core.io.ClassPathResource;
import com.mbc.puf.pm.dto.*;
import java.util.*;
import java.util.List;

final class PdfVisuals {
 static final DeviceRgb INK=rgb(0x182332),MUTED=rgb(0x667383),BLUE=rgb(0x2359A8),LINE=rgb(0xDCE2E8),PALE=rgb(0xF3F5F7);
 static final DeviceRgb[] COLORS={rgb(0x279878),rgb(0xE8A138),rgb(0xDB5656),rgb(0x9AA5B2)};
 static DeviceRgb rgb(int n){return new DeviceRgb(n>>16&255,n>>8&255,n&255);}
 static PdfFont font() throws Exception {
  try(var in=new ClassPathResource("fonts/NotoSansKR.ttf").getInputStream()){
   return PdfFontFactory.createFont(in.readAllBytes(),PdfEncodings.IDENTITY_H,PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
  }
 }
 static Paragraph p(String text,PdfFont f,float size){return new Paragraph(text==null?"-":text).setFont(f).setFontSize(size).setFontColor(INK);}
 static void title(Document d,PdfFont f,String title,String desc){
  d.add(p("PUF  /  지하차도 통합 관제",f,9).setFontColor(BLUE).setMarginBottom(16));
  d.add(p(title,f,22).setMarginBottom(4));d.add(p(desc,f,9).setFontColor(MUTED).setMarginBottom(20));
 }
 static void section(Document d,PdfFont f,String title){d.add(p(title,f,12).setMarginTop(16).setMarginBottom(8).setKeepWithNext(true));}
 static Table table(float...widths){return new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();}
 static Cell cell(String s,PdfFont f,boolean header){return new Cell().add(p(s,f,9)).setPadding(8)
  .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINE,.5f)).setBackgroundColor(header?PALE:ColorConstants.WHITE);}
 static void footer(PdfDocument pdf,PdfFont f){
  pdf.addEventHandler(PdfDocumentEvent.END_PAGE,new AbstractPdfDocumentEventHandler(){
   @Override protected void onAcceptedEvent(AbstractPdfDocumentEvent e){
    var ev=(PdfDocumentEvent)e;var page=ev.getPage();var c=new PdfCanvas(page);
    c.setStrokeColor(LINE).setLineWidth(.5f).moveTo(40,48).lineTo(555,48).stroke();
    text(c,f,40,32,"PUF · 지하차도 침수 예방 시스템",8,MUTED);
    text(c,f,520,32,String.valueOf(pdf.getPageNumber(page)),8,MUTED);
   }
  });
 }
 static void text(PdfCanvas c,PdfFont f,double x,double y,String s,float size,Color color){
  c.beginText().setFontAndSize(f,size).setFillColor(color).moveText(x,y).showText(s==null?"-":s).endText();
 }
 static Image summary(PdfDocument pdf,PdfFont f,PmPeriodSummary s){
  var form=new PdfFormXObject(new Rectangle(515,540));var c=new PdfCanvas(form,pdf);
  var n=s.levelCounts();var prev=s.previousLevelCounts();int[]v={n.safe(),n.caution(),n.danger(),n.noData()};
  int[]b=prev==null?null:new int[]{prev.safe(),prev.caution(),prev.danger(),prev.noData()};String[]labels={"안전","주의","위험","미수신"};
  int total=Arrays.stream(v).sum();
  for(int i=0;i<4;i++){
   float x=i*131; c.setFillColor(PALE).rectangle(x,451,122,85).fill();c.setFillColor(COLORS[i]).rectangle(x,532,122,4).fill();
   text(c,f,x+12,512,labels[i],10,MUTED);text(c,f,x+12,480,""+v[i],26,COLORS[i]);
   String delta=b==null?"비교 데이터 없음":"전일 시작 대비 "+(v[i]-b[i]>0?"+":"")+(v[i]-b[i]);
   text(c,f,x+12,462,delta,8,MUTED);
  }
  text(c,f,0,423,"02  전체 설비 단계 분포",12,INK);
  double start=90;
  if(total==0)c.setStrokeColor(LINE).setLineWidth(19).circle(88,344,48).stroke();
  for(int i=0;i<4;i++){
   if(v[i]>0){double extent=-360.0*v[i]/total;c.setStrokeColor(COLORS[i]).setLineWidth(19).arc(40,296,136,392,start,extent).stroke();start+=extent;}
   c.setFillColor(COLORS[i]).rectangle(211,382-i*27,8,8).fill();text(c,f,228,382-i*27,labels[i],10,INK);
   text(c,f,381,382-i*27,v[i]+"대",10,INK);text(c,f,465,382-i*27,total==0?"0%":String.format(java.util.Locale.ROOT,"%.0f%%",100.0*v[i]/total),10,MUTED);
  }
  text(c,f,72,345,""+total,24,INK);text(c,f,67,323,"전체 설비",8,MUTED);
  text(c,f,0,252,"03  설비 종류별 단계 비교",12,INK);
  int i=0;
  for(PmTypeCounts row:s.byType()==null?List.<PmTypeCounts>of():s.byType()){
   double y=221-i++*30;String name=switch(row.type()){case CCTV->"CCTV";case PUMP->"배수펌프";case DISPLAY->"전광판";case WATER_LEVEL->"수위계";case ROAD->"노면";};
   text(c,f,0,y,name,10,INK);double x=77;int[]counts={row.safe(),row.caution(),row.danger(),row.noData()};
   for(int j=0;j<4;j++)if(counts[j]>0&&row.total()>0){double w=392.0*counts[j]/row.total();c.setFillColor(COLORS[j]).rectangle(x,y-6,w,18).fill();if(w>18)text(c,f,x+w/2-3,y-1,""+counts[j],8,ColorConstants.WHITE);x+=w;}
   text(c,f,487,y-1,row.total()+"대",8,MUTED);
  }
  text(c,f,77,57,"막대 안 숫자: 설비 수 · 단계 색상은 상단과 동일",8,MUTED);
  text(c,f,0,28,"04  오늘의 점검 요약",12,INK);
  text(c,f,0,2,"신규 "+s.newEventCount()+"건",13,BLUE);text(c,f,177,2,"조치 기록 "+s.actionCount()+"건",13,BLUE);text(c,f,353,2,"미처리 "+s.openEventCount()+"건",13,BLUE);
  return new Image(form).setWidth(515).setHeight(540);
 }
}
