package com.asuka.filelist.infrastructure.driver.baidu;

import com.asuka.filelist.infrastructure.driver.DriverConfig;
import com.asuka.filelist.infrastructure.driver.DriverInfo;
import com.asuka.filelist.infrastructure.driver.DriverItem;
import com.asuka.filelist.infrastructure.driver.StorageDriver;
import com.asuka.filelist.infrastructure.driver.StorageDriverFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * 百度网盘驱动工厂。共享一个 JDK HttpClient。
 */
@Component
public class BaiduDriverFactory implements StorageDriverFactory {

    public static final String DRIVER_NAME = "BaiduNetdisk";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BaiduDriverFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return DRIVER_NAME;
    }

    /**
     * 驱动配置项，供管理端动态渲染表单。
     */
    @Override
    public DriverInfo info() {
        DriverConfig config = new DriverConfig(DRIVER_NAME, false, false, true, false, false, "/", true);
        List<DriverItem> items = List.of(
                new DriverItem("refreshToken", "Refresh token", "string", true, "", "OAuth refresh_token (long-lived)"),
                new DriverItem("clientId", "Client id", "string", true, "", "App key"),
                new DriverItem("clientSecret", "Client secret", "string", true, "", "Secret key"),
                new DriverItem("rootPath", "Root path", "string", false, "/", "Root path inside netdisk")
        );
        return new DriverInfo(config, items);
    }

    @Override
    public StorageDriver create() {
        return new BaiduDriver(objectMapper, httpClient);
    }
}
