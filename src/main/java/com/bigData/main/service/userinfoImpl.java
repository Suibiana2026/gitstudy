package com.bigData.main.service;
import com.bigData.main.mapper.userMapper;
import com.bigData.main.pojo.pra;
import com.bigData.main.pojo.userinfo;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
@Service//标注此类为业务实现层
public class userinfoImpl implements UserService {

    @Resource //从Spring容器中的获取对象
//    @Autowired
    userMapper umap;
    @Override
    public Integer insertUserInfo(userinfo u) {
        System.out.println(u);
        return umap.userinsert(u);
    }
    @Override
    public List<userinfo> userlist() {
        return umap.listSelect();
    }
    @Override
    public Integer userDelete(userinfo d) {
        return umap.Deleteuser(d);
    }
    @Override
    public Integer userTable(userinfo t) {
        return umap.createTable(t);
    }
    @Override
    public Integer userSet(userinfo e) {
        return umap.userupdate(e);
    }
    @Override
    public pra getUserPassWord(userinfo u) {
        //调用根据用户名查密码
        userinfo user = umap.getUserPass(u);

        //保存返回值
        int re = 0;
        if (user!=null){
            //用户存在
            String upass=user.getUpass();
            if (upass.equals(u.getUpass())){
                //登录成功
                re = 1;
            }else {
                //用户名或密码错误
                re = 2;
            }
        }else {
            //用户不存在
            re = 3;
        }
        pra p=new pra();
        p.setRe(re);
        p.setUroot(user.getUroot());
        return p;
    }

    //注册
    @Override
    public Integer GetUser(userinfo u) {
        //查看用户是否存在
        userinfo userPass = umap.getUserPass(u);
        //保存返回值
        int re = 0;
        if (userPass != null){
            //用户存在
            re = 2;
        }else {
            //用户不存在可以注册
            re=umap.insertUser(u);
        }

        return re;
    }
    @Override
    public Integer Status(userinfo u) {
        return umap.Status(u);
    }
    @Override
    public Integer userStatus(userinfo u) {
        return umap.selectstatus(u);
    }


}



