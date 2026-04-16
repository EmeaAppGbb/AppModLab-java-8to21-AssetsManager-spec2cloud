package com.microsoft.migration.assets.config;

import com.microsoft.migration.assets.constants.StorageConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

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
        
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            var startTime = System.currentTimeMillis();
            request.setAttribute("startTime", startTime);
            
            var operation = determineFileOperation(request);
            System.out.printf("[FILE-OP] %s %s - %s started at %d%n", 
                    request.getMethod(), request.getRequestURI(), operation, startTime);
            
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                  Object handler, Exception ex) {
            var startTime = (Long) request.getAttribute("startTime");
            var duration = System.currentTimeMillis() - startTime;
            var operation = determineFileOperation(request);
            
            if (ex != null) {
                System.out.printf("[FILE-OP] %s %s - %s FAILED in %d ms (Status: %d, Error: %s)%n", 
                        request.getMethod(), request.getRequestURI(), operation, duration, 
                        response.getStatus(), ex.getMessage());
            } else {
                System.out.printf("[FILE-OP] %s %s - %s completed in %d ms (Status: %d)%n", 
                        request.getMethod(), request.getRequestURI(), operation, duration, 
                        response.getStatus());
            }
        }
        
        private String determineFileOperation(HttpServletRequest request) {
            var uri = request.getRequestURI();
            var method = request.getMethod();
            
            return switch (uri) {
                case String u when u.contains("/upload") -> "FILE_UPLOAD";
                case String u when u.contains("/delete/") -> "FILE_DELETE";
                case String u when u.contains("/view/") -> "FILE_DOWNLOAD";
                case String u when u.contains("/view-page/") -> "FILE_VIEW_PAGE";
                case String u when "GET".equals(method) && u.equals("/" + StorageConstants.STORAGE_PATH) -> "FILE_LIST";
                default -> "FILE_OPERATION";
            };
        }
    }
}
