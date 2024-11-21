package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.captcha.generator.RandomGenerator;
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
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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
        session.setAttribute("code",code);
        session.setAttribute("phone",phone);
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
        //2.校验手机号和验证码（要求和session中的匹配）
        if (RegexUtils.isPhoneInvalid(phone)||!session.getAttribute("phone").toString().equals(phone)){
            return Result.fail("手机号和验证码不匹配");
        }
        if (!session.getAttribute("code").toString().equals(code)){
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
        //5.保存用户信息到session中
        session.setAttribute("userDTO",userDTO);
        return Result.ok();
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
