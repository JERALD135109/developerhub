package dev.developerhub;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class ApiSecurity implements WebMvcConfigurer {
  @Value("${developerhub.api-token:}") private String apiToken;
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new HandlerInterceptor() {
      public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        String supplied=request.getHeader("X-Platform-Token");
        boolean local=request.getRemoteAddr().equals("127.0.0.1") || request.getRemoteAddr().equals("0:0:0:0:0:0:0:1") || request.getRemoteAddr().equals("::1");
        boolean valid=!apiToken.isBlank() && supplied!=null && MessageDigest.isEqual(apiToken.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));
        if(valid || (apiToken.isBlank() && local)) return true;
        response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"error\":\"Platform API authentication required\"}"); return false;
      }
    }).addPathPatterns("/api/**");
  }
}
