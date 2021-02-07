package com.example.emos.wx.config.shiro;

import com.example.emos.wx.db.pojo.TbUser;
import com.example.emos.wx.service.UserService;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 *  Shiro Framework
 *  Authorization and Authentication of tokens
 * */

@Component
public class OAuth2Realm extends AuthorizingRealm{
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof OAuth2Token;
    }

    /**
     * 授权(验证权限时调用)
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection collection) {
        // "认证"封装了"用户信息"
        // Shiro在执行完"认证"后，自动执行"授权"
        TbUser user = (TbUser)collection.getPrimaryPrincipal();
        int userId = user.getId();
        // 查询用户的权限列表
        Set<String> permsSet = userService.searchUserPermissions(userId);

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        // 把权限列表添加到info对象中
        info.setStringPermissions(permsSet);
        return info;
    }

    /**
     * 认证(验证登录时调用)
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        String token = (String)authenticationToken.getPrincipal();
        // 从令牌中获取userId
        int userId = jwtUtil.getUserId(token);
        TbUser user = userService.searchById(userId);

        // 检测该账户是否被冻结
        // 状态：
        //      员工离职状态 status != 1
        //      Token还是有效的，并没过期
        if (user == null) {
            throw new LockedAccountException("账号已被锁定，请联系管理员");
        }

        // 往info对象中添加用户信息、Token字符串，Realm类名字
        SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(user, token, getName());
        return info;
    }
}
