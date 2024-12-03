package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j

public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    /*
    * 商铺查询和redis缓存商铺数据
    * */
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿问题
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);

    }


    /*
    *使用锁来解决缓存击穿
    * */
    public Shop queryWithMutex(Long id){
        //1.从redis查询商铺缓存
        //这里我们尝试使用String类型存储对象，当然也可以和前面一样使用哈希存储
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)){

            //3.存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if(shopJson!=null){
            //即为""，说明我们已经做过处理，将其写入过redis缓存（“”数据）
            //则直接返回错误信息
            return null;

        }
        //4.不存在,实现缓存重建
        //4.1 获取锁
        Shop shop=new Shop();
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        try{
            if (!tryLock(lockKey)) {
                //4.2失败，休眠且重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.2获取锁成功,再检查一次redis中是否有，没有就查数据库，有则直接返回
            shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            //2.判断是否存在
            if (StrUtil.isNotBlank(shopJson)){
                //3.存在，直接返回
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return shop;
            }

            shop = getById(id);
            //模拟重建延迟
            Thread.sleep(200);
            //5.数据库不存在，返回错误
            if (shop == null) {
                //设置在redis中该id对应的为空值，且缓存倒计时为2分钟
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.数据库存在，写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw  new RuntimeException(e);
        }finally {
            //7.释放锁
            unlock(lockKey);
        }
        //7.返回
        return shop;
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

    public void saveShop2Redis(Long id,Long expireSeconds){
        //1.查询店铺数据
        Shop shop=getById(id);
        //2.封装逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //加入到redis当中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /*
    * 更新shop，按照策略2，更新数据库中数据，删除redis中缓存
    * */
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除redis中缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
