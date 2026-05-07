package com.loadingmid.feedback.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final String dataDir;

    public StaticResourceConfig(@Value("${app.data-dir}") String dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String videosLocation = "file:" + Paths.get(dataDir, "videos").toAbsolutePath() + "/";
        registry.addResourceHandler("/videos/**")
                .addResourceLocations(videosLocation);
    }
}
