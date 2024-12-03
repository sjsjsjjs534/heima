package com.hmdp.utils;

import cn.hutool.core.lang.func.Func;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @BelongsProject: hm-dianping
 * @Author: 张宇若
 * @CreateTime: 2024-12-03  10:39
 * @Description: 缓存工具类
 * @Version: 1.0
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
    * 缓存，过期时间使用TTL
    * */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /*
    * 缓存，使用逻辑过期时间
    * */
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){
        //封装RedisData,里面包含逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /*
    * 解决缓存穿透
    * */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit){
        String key=keyPrefix+id;
        //1.从redis当中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            return JSONUtil.toBean(json,type);
        }
        //判断命中的是否为空值
        if (json!=null){
            //直接返回null,因为这样说明json为空
            return null;
        }

        //4.不存在,根据id查询数据库
        //由于对于不同类型的来说，查询数据库的方法不同,所有要靠调用者来传函数逻辑
        R r=dbFallback.apply(id);
        //5.不存在，返回错误
        if (r==null){
            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误值
            return null;
        }
        //6.存在，写入redis
        this.set(key,r,time,unit);

        return r;
    }

    private static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> doFallback,Long time,TimeUnit unit){
        String key=keyPrefix+id;
        //1.从redis查询商铺缓存
        //这里我们尝试使用String类型存储对象，当然也可以和前面一样使用哈希存储
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.为了逻辑完整，我们还是会考虑一下未命中的情况，虽然我们的机制是热点事件必定命中
        if (StrUtil.isBlank(json)){
            return null;
        }
        //3.命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //由于我们的data没有写死是店铺，所以做反序列化的时候她不一定知道是店铺类型，所以我们还需要手动的反序列化data
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        //4.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //4.1未过期，直接返回对象
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //4.2过期，需要进行缓存重建
        //5.缓存重建
        //5.1获取互斥锁

        //5.2获取失败，直接返回过期对象
        if (!tryLock(RedisConstants.LOCK_SHOP_KEY+id)) {
            return r;
        }
        //5.3获取成功，开启独立线程处理实现缓存重建，返回过期对象
        //获取成功，再检查一下redis缓存是否过期，因为可能前一个释放锁的人修改好了缓存时间
        redisData = JSONUtil.toBean(json, RedisData.class);
        //由于我们的data没有写死是店铺，所以做反序列化的时候她不一定知道是店铺类型，所以我们还需要手动的反序列化data
        r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }

        //确定了时间还是过期的，所以还是要开启一个独立线程去做
        CACHE_REBUILD_EXECUTOR.submit(()->{

            try {//查询数据库
                R r1 = doFallback.apply(id);
                //写入redis
                this.setWithLogicalExpire(key, r1, time, unit);
            } catch (Exception e){
                throw  new RuntimeException(e);
            }finally {
                //释放锁
                unlock(RedisConstants.LOCK_SHOP_KEY+id);
            }

        });

        return r;
    }
    /*
     * 获取锁，获取到了就返回true
     * */
    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);
        log.info(stringRedisTemplate.opsForValue().get(key));
        return flag;
    }
    /*
     * 释放锁
     * */
    private void unlock(String key){
        log.info(stringRedisTemplate.opsForValue().get(key));
        stringRedisTemplate.delete(key);
    }

}
