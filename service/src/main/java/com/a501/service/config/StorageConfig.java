package com.a501.service.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 내부 클라이언트는 Pod가 저장소를 직접 부를 때, 공개 클라이언트는 브라우저가 쓸 프리사인 주소를 만들 때만 씁니다.
 * 공개 주소는 Pod 안에서 열리지 않을 수 있어 리전을 고정해 두고, 어떤 클라이언트도 주소 발급 중 네트워크를 타지 않게 합니다.
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {
    @Bean
    @Qualifier("minioInternalClient")
    MinioClient minioInternalClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.internalEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    @Bean
    @Qualifier("minioPublicClient")
    MinioClient minioPublicClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }
}
