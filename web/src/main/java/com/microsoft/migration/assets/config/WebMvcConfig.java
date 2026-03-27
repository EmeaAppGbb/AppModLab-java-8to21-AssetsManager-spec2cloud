package com.microsoft.migration.assets.config;

import com.microsoft.migration.assets.constants.StorageConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Resource handlers with caching for static content.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Add cache control for CSS, JS, and image files
        registry.addResourceHandler("/css/**", "/js/**", "/images/**")
                .addResourceLocations("classpath:/static/css/", "classpath:/static/js/", "classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
        
        // Add cache control for favicon
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
    }

    /**
     * Interceptors for request logging and file operation monitoring.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FileOperationLoggingInterceptor())
                .addPathPatterns("/" + StorageConstants.STORAGE_PATH + "/**")
                .excludePathPatterns("/" + StorageConstants.STORAGE_PATH + "/view/**"); // Exclude file download endpoints from detailed logging
    }

    /**
     * Custom interceptor using HandlerInterceptorAdapter.
     * This interceptor logs file operations for monitoring and debugging purposes.
     */
    private static class FileOperationLoggingInterceptor implements HandlerInterceptor {
        
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileOperationLoggingInterceptor.class);

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            long startTime = System.currentTimeMillis();
            request.setAttribute("startTime", startTime);
            
            String operation = determineFileOperation(request);
            log.info("[FILE-OP] {} {} - {} started at {}", 
                    request.getMethod(), request.getRequestURI(), operation, startTime);
            
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                  Object handler, Exception ex) {
            long startTime = (Long) request.getAttribute("startTime");
            long duration = System.currentTimeMillis() - startTime;
            String operation = determineFileOperation(request);
            
            if (ex != null) {
                log.error("[FILE-OP] {} {} - {} FAILED in {} ms (Status: {}, Error: {})", 
                        request.getMethod(), request.getRequestURI(), operation, duration, 
                        response.getStatus(), ex.getMessage());
            } else {
                log.info("[FILE-OP] {} {} - {} completed in {} ms (Status: {})", 
                        request.getMethod(), request.getRequestURI(), operation, duration, 
                        response.getStatus());
            }
        }
        
        private String determineFileOperation(HttpServletRequest request) {
            String uri = request.getRequestURI();
            String method = request.getMethod();
            String basePath = "/" + StorageConstants.STORAGE_PATH;
            String subPath = uri.length() > basePath.length()
                    ? uri.substring(basePath.length() + 1).split("/")[0]
                    : "";

            return switch (subPath) {
                case "upload"    -> "FILE_UPLOAD";
                case "delete"    -> "FILE_DELETE";
                case "view"      -> "FILE_DOWNLOAD";
                case "view-page" -> "FILE_VIEW_PAGE";
                case ""          -> "GET".equals(method) ? "FILE_LIST" : "FILE_OPERATION";
                default          -> "FILE_OPERATION";
            };
        }
    }
}
