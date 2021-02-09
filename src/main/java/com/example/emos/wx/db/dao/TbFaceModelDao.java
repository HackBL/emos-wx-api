package com.example.emos.wx.db.dao;

import com.example.emos.wx.db.pojo.TbFaceModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TbFaceModelDao {
    public String searchFaceModel(int userId);

    public void insertFaceModel(TbFaceModel faceModel);
    
    public void deleteFaceModel(int userId);
}