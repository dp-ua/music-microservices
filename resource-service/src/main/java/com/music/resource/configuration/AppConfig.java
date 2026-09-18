package com.music.resource.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.music.resource.client.song.SongServiceClientPaths;

@Configuration
@EnableConfigurationProperties
        ({
                SongServiceClientPaths.class
        })
public class AppConfig {

}
