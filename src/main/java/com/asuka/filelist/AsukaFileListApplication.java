package com.asuka.filelist;

import com.asuka.filelist.common.config.AsukaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AsukaProperties.class)
public class AsukaFileListApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsukaFileListApplication.class, args);
    }
}
