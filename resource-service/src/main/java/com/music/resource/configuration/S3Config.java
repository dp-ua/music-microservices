package com.music.resource.configuration;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.music.resource.storage.S3Properties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties) {
        var credentials = AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey());

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(properties.isPathStyleAccess())
                .build();
    }

}
