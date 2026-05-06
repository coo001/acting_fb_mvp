package com.loadingmid.feedback.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class PromptLoader {

    private final ResourceLoader resourceLoader;

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load() {
        Resource resource = resourceLoader.getResource("classpath:prompts/system.md");
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("system.md 읽기 실패", e);
        }
    }
}
