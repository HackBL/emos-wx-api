package com.example.emos.wx.service;

import com.example.emos.wx.db.pojo.TbUser;

import java.util.Set;

public interface UserService {
    public int registerUser(String registerCode,String code,String nickname,String photo);

    public Integer login(String code);

    public Set<String> searchUserPermissions(int userId);

    public TbUser searchById(int userId);

    public String searchUserHireDate(int userId);

}
