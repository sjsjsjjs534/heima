package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
    * 发送短信
    * */
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            //2.如果不符合，返回错误信息
            return Result.fail("输入的手机号格式错误");
        }
        //3.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码和手机号到session（优化）
        //session.setAttribute("code",code);
        //session.setAttribute("phone",phone);

        //4.保存验证码到redis 设置有效期为2分钟 set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        log.debug("验证码为："+code);
        //6.返回ok
        return Result.ok();
    }

    /*
    * 用户登录
    * */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.获取手机号和验证码
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        //2.校验验证码（从redis当中取出来）
        String redisCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if (code==null||!code.equals(redisCode)){
            return Result.fail("验证码错误");
        }
        //3.查询用户(mybatis-plus查询)
        User user = query().eq("phone", phone).one();

        //4.判断是否为空，为空则创建一个user并保存在数据库当中
        if (user==null){
            user=createUserWithPhone(phone);
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //5.保存用户信息到redis当中
        //5.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //5.2 将User对象转为Hash存储
        Map<String, String> userMap = new HashMap<>();
        //这个stringRedisTemplate不允许传入Long型数据，要求全是String
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName",userDTO.getNickName());
        userMap.put("icon",userDTO.getIcon());
        //5.3 存储
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //5.4设置token有效期(用户30分钟不操作才删除)，在拦截器里面操作一下，每次访问重置拦截器
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        //6 返回token
        return Result.ok(token);
    }

    /*
    * 创建新用户
    * */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));

        //保存用户
        save(user);
        return user;
    }
}
