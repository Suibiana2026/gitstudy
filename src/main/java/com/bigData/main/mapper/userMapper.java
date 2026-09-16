package com.bigData.main.mapper;

import com.bigData.main.pojo.userinfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper //标注此接口为数据库访问层
public interface userMapper {
    //创建表
    @Update("create table userinfo(uid INT NOT NULL AUTO_INCREMENT key,\n" +
            "uname VARCHAR(50),\n" +
            "upass VARCHAR(50),\n" +
            "uroot INT)")
    public Integer createTable(userinfo t);
    //插入数据
    @Insert("insert into userinfo(uname,upass,uroot,email,status) values(#{uname},#{upass},#{uroot},#{email},#{status})")
    public Integer insertUser(userinfo u);
    //查询信息
    @Select("select * from userinfo")
    public List<userinfo> listSelect();
    //删除表信息
    @Delete("delete from userinfo where uid=#{uid} ")
    public Integer Deleteuser(userinfo d);
    //更新数据
    @Update("update userinfo set uname=#{uname},upass=#{upass},uroot=#{uroot} where uid=#{uid}")
    public Integer userSet(userinfo e);

    //修改状态
    @Update("update userinfo set status=#{status} where uid=#{uid}")
    public Integer Status(userinfo e);
    //登录逻辑--根据用户名查密码
    @Select("select upass,uroot from userinfo where uname=#{uname}")
    public userinfo getUserPass(userinfo u);

    @Select("select status from userinfo where uname=#{uname}")
    public Integer selectstatus(userinfo u);

    //插入数据2.0//插入数据
    @Insert("insert into userinfo(uname,email,upass,uroot) values(#{uname},#{email},#{upass},#{uroot})")
    public Integer userinsert(userinfo u);


    //更新数据2.0//更新数据
    @Update("update userinfo set upass=#{upass},uroot=#{uroot},email=#{email} where uid=#{uid}")
    public Integer userupdate(userinfo e);

}