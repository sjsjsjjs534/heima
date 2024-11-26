package com.hmdp.config;

import com.hmdp.Interceptor.LoginInterceptor;
import com.hmdp.Interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @BelongsProject: hm-dianping
 * @Author: 张宇若
 * @CreateTime: 2024-11-21  19:35
 * @Description: TODO
 * @Version: 1.0
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //order设置运行优先级
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/blog/hot")
                .excludePathPatterns("/shop/**")
                .excludePathPatterns("/shop-type/**")
                .excludePathPatterns("/voucher/**")
                //方便测试
                .excludePathPatterns("/upload").order(1);
        //拦所有请求
        //token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }
}
