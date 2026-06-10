package com.asuka.filelist.infrastructure.webdav;

import com.asuka.filelist.application.webdav.WebDavService;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@link WebDavServlet} 注册到 {@code /dav/*}，独立于 DispatcherServlet。
 */
@Configuration
public class WebDavServletConfig {

    @Bean
    public ServletRegistrationBean<WebDavServlet> webDavServletRegistration(
            WebDavService webDavService,
            WebDavDigestAuthenticator authenticator,
            WebDavStreaming streaming) {
        WebDavServlet servlet = new WebDavServlet(webDavService, authenticator, streaming);
        ServletRegistrationBean<WebDavServlet> registration =
                new ServletRegistrationBean<>(servlet, "/dav/*");
        registration.setName("webDavServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
