package com.example.emos.wx.service;

import java.util.ArrayList;
import java.util.HashMap;

public interface CheckinService {
    public String validCanCheckIn(int userId, String date);

    public void checkin(HashMap param);

    public void createFaceModel(int userId, String path);

    public HashMap searchTodayCheckin(int userId);

    public long searchCheckinDays(int userId);

    public ArrayList<HashMap> searchWeekCheckin(HashMap param);
}
