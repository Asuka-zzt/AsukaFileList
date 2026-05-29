package com.asuka.filelist;

import com.asuka.filelist.common.config.AsukaProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AsukaProperties.class)
@MapperScan("com.asuka.filelist.infrastructure.persistence.mapper")
public class AsukaFileListApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsukaFileListApplication.class, args);
    }
}
