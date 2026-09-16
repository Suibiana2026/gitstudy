package com.bigData.main.controller;

import com.bigData.main.pojo.pra;
import com.bigData.main.pojo.userinfo;
import com.bigData.main.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
@RestController
public class userController {
    //此接口实现用户查询
    @Resource
    UserService userServic;
    //用户查看信息
    @RequestMapping("/select")
    public List<userinfo> Userselect(){
        return userServic.userlist();
    }
    //更新数据
    @RequestMapping("/show/info")
    public Integer userSet(userinfo e){
        return userServic.userSet(e);
    }
    //删除数据
    @RequestMapping("/delete")
    public Integer Deleteuser(userinfo d){
        return userServic.userDelete(d);
    }

    //登录校验方法
    @RequestMapping("/Login")
    public pra login(userinfo u){
        return userServic.getUserPassWord(u);
    }

    //用户注册
    @RequestMapping("/insertuser")
    public Integer Getuser(userinfo u){
        return userServic.GetUser(u);
    }

//    修改权限
    @RequestMapping("/updateStatus")
    public Integer Status(userinfo u){return userServic.Status(u);}

    //添加用户
    @RequestMapping("/insertUserInfou")
    public Integer insertUserInfos(userinfo u){
        return userServic.insertUserInfo(u);
    }

    //查看状态
    @RequestMapping("/statusser")
    public Integer statususerinfo(userinfo u){
        return userServic.userStatus(u);
    }







}
