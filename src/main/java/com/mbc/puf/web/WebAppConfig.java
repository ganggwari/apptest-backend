package com.mbc.puf.web;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 빌드된 프런트(Vue PWA)를 같은 주소에서 서빙한다. puf.web.root(=PUF_WEB_ROOT) 가 있을 때만 켜진다.
 * 파일이 있으면 그 파일, 없으면(= 화면 경로) index.html 을 돌려준다(SPA). /api, /ws 는 건드리지 않는다.
 */
@Configuration
@ConditionalOnExpression("!'${puf.web.root:}'.isEmpty()")
public class WebAppConfig implements WebMvcConfigurer {

	private final String root;

	public WebAppConfig(@Value("${puf.web.root}") String root) {
		this.root = root.endsWith("/") ? root : root + "/";
	}

	/** 루트(/)는 리소스 핸들러가 빈 경로를 건너뛰므로 index.html 로 직접 연결한다 */
	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/").setViewName("forward:/index.html");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("file:" + root)
				// 서비스 워커/매니페스트/html 은 항상 최신을 확인하고, 해시가 붙은 assets 는 오래 캐시한다
				.setCacheControl(CacheControl.noCache())
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")
								|| resourcePath.startsWith("tunnel-source/")) {
							return null;
						}
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable() && !resourcePath.isEmpty()
								&& !requested.getFile().isDirectory()) {
							return requested;
						}
						Resource index = new FileSystemResource(root + "index.html");
						return index.exists() ? index : null;
					}
				});
	}
}
