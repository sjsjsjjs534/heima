package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @BelongsProject: hm-dianping
 * @Author: 张宇若
 * @CreateTime: 2024-11-27  20:18
 * @Description: TODO
 * @Version: 1.0
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
