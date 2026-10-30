package com.xiaoying.workshop;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final FileStorage storage;
    public WebConfiguration(FileStorage storage) { this.storage = storage; }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(storage.root().toUri().toString())
                .setCacheControl(CacheControl.noCache());
    }
}
