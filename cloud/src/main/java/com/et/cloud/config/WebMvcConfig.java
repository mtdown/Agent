//package com.et.cloud.config;
//
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class WebMvcConfig implements WebMvcConfigurer {
//
//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        // 将 URL 路径 "/" 映射到 "classpath:/static/"
//        registry.addResourceHandler("/**")
//                .addResourceLocations("classpath:/static/");
//    }
//}