package com.example.emos.wx.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.example.emos.wx.config.SystemConstants;
import com.example.emos.wx.db.dao.TbCheckinDao;
import com.example.emos.wx.db.dao.TbHolidaysDao;
import com.example.emos.wx.db.dao.TbWorkdayDao;
import com.example.emos.wx.service.CheckinService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@Scope("prototype") // 成为多例的对象, 实现线程的异步执行
@Slf4j
public class CheckinServiceImpl implements CheckinService {
    @Autowired
    private SystemConstants constants;

    @Autowired
    private TbHolidaysDao holidaysDao;

    @Autowired
    private TbWorkdayDao workdayDao;

    @Autowired
    private TbCheckinDao checkinDao;


    @Override
    public String validCanCheckIn(int userId, String date) {
        boolean isHoliday = holidaysDao.searchTodayIsHolidays() != null;
        boolean isWorkDay = workdayDao.searchTodayIsWorkday() != null;
        String type = "工作日";

        if (DateUtil.date().isWeekend()) {
            type = "节假日";
        }

        if (isHoliday) {
            type = "节假日";
        }
        else if (isWorkDay) {
            type = "工作日";
        }

        // 当天是节假日
        if (type.equals("节假日")) {
            return "节假日不需要考勤";
        }
        // 当天是工作日
        else {
            DateTime now = DateUtil.date();
            String start = DateUtil.today() + " " + constants.attendanceStartTime;
            String end = DateUtil.today() + " " + constants.attendanceEndTime;
            DateTime attendanceStart = DateUtil.parse(start);
            DateTime attendanceEnd = DateUtil.parse(end);

            if (now.isBefore(attendanceStart)) {
                return "没到上班考勤开始时间";
            }
            else if (now.isAfter(attendanceEnd)) {
                return "超过了上班考勤结束时间";
            }
            else {
                HashMap map = new HashMap();
                map.put("userId", userId);
                map.put("date", date);
                map.put("start", start);
                map.put("end", end);

                // 判断用户是否已经考勤
                boolean checkedIn = checkinDao.haveCheckin(map) != null;
                return checkedIn ? "今日已经考勤，不用重复考勤" : "可以考勤";
            }
        }
    }
}
