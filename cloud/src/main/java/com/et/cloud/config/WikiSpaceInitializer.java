package com.et.cloud.config;

import com.et.cloud.service.WikiSpaceService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class WikiSpaceInitializer implements CommandLineRunner {

    @Resource
    private WikiSpaceService wikiSpaceService;

    @Override
    public void run(String... args) {
        wikiSpaceService.ensurePublicSpace();
    }
}
