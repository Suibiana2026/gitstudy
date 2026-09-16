package com.bigData.main.service;

import com.bigData.main.pojo.pra;
import com.bigData.main.pojo.userinfo;
import org.springframework.stereotype.Service;

import java.util.List;

public interface UserService {
    pra getUserPassWord(userinfo u);

    //注册
    Integer GetUser(userinfo u);

    Integer Status(userinfo u);

    //判断状态
    public Integer userStatus(userinfo u);

    //添加用户
    public Integer insertUserInfo(userinfo u);

    //查询信息
    public List<userinfo> userlist();

    Integer userDelete(userinfo d);

    Integer userTable(userinfo t);

    //更新数据
    public Integer userSet(userinfo e);

}

