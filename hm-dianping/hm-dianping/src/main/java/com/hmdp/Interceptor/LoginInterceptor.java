package com.hmdp.Interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.management.Query;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @BelongsProject: hm-dianping
 * @Author: 张宇若
 * @CreateTime: 2024-11-21  19:20
 * @Description: TODO
 * @Version: 1.0
 */
//登录拦截器
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取session
        HttpSession session = request.getSession();
        //2.获取session中的用户
        UserDTO userDTO = (UserDTO) session.getAttribute("userDTO");
        //3.判断用户是否存在
        if (userDTO==null){
            //4.不存在
            response.setStatus(401);
            return false;
        }
        //5.存在，保存用户信息到ThreadLocal

        UserHolder.saveUser(userDTO);

        //6.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户,避免内存泄露
        UserHolder.removeUser();
    }
}
