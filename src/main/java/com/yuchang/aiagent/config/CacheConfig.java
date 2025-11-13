package com.yuchang.aiagent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置
 */
@Configuration
public class CacheConfig {

    /**
     * Steam 游戏排行榜缓存
     * 缓存时间：30分钟
     * 最大缓存条目：100
     */
    @Bean("steamRankingCache")
    public Cache<String, String> steamRankingCache() {
        return Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}

