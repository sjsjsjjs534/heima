package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
    * 查看类别列表，外加缓存功能
    * */
    @Override
    public Result queryTypeList() {
        //1.查看redis缓存中是否有
        String shopTypeList = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_TYPE_KEY);
        //2.存在，则直接返回
        if (!StrUtil.isBlank(shopTypeList)){
            List<ShopType> typeList = JSONUtil.toList(shopTypeList,ShopType.class);
            return Result.ok(typeList);
        }

        //3.不存在，查数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        //4.数据库为空，返回错误信息
        if (list.isEmpty()){
            return Result.fail("无店铺类型可用");
        }
        //5.数据库不为空，写入redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_TYPE_KEY, JSONUtil.toJsonStr(list),RedisConstants.CACHE_TYPE_TTL, TimeUnit.MINUTES);
        //6.返回数据
        return Result.ok(list);
    }
}
