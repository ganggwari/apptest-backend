package com.mbc.puf.cctv.client;
import java.io.InputStream;
import java.net.http.HttpResponse;
/** Common proxy contract. Each analysis server keeps its own execution state. */
public interface AiVideoClient {
    HttpResponse<InputStream> openStream(String path);
    HttpResponse<byte[]> getBytes(String path);
    HttpResponse<byte[]> postBytes(String path);
}
