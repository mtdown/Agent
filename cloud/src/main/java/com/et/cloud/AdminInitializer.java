package com.et.cloud;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.et.cloud.model.constant.UserConstant;
import com.et.cloud.model.entity.User;
import com.et.cloud.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class AdminInitializer implements CommandLineRunner {

    @Resource
    private UserService userService;

    @Override
    public void run(String... args) throws Exception {
        // 查询数据库中是否已有管理员
        long adminCount = userService.count(new QueryWrapper<User>().eq("userRole", UserConstant.ADMIN_ROLE));
        // 如果没有管理员，则创建一个
        if (adminCount == 0) {
            User admin = new User();
            admin.setUserAccount("admin");
            // 设置一个初始密码
            admin.setUserPassword(userService.getEncryptPassword("12345678"));
            admin.setUserName("超级管理员");
            admin.setUserRole(UserConstant.ADMIN_ROLE);
            userService.save(admin);
            System.out.println(">>>>>>>>>> 已成功初始化默认管理员账号 <<<<<<<<<<");
        }
    }
}
