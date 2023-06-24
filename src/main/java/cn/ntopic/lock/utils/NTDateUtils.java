/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期时间工具类
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTDateUtils {

    /**
     * 日期时间格式化
     */
    private static final String FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    /**
     * 格式化时间
     */
    public static String format(Date time) {
        return new SimpleDateFormat(FORMAT).format(time);
    }

    /**
     * 解析时间对象
     *
     * @throws RuntimeException 解析时间异常
     */
    public static Date parse(String value) {
        try {
            return new SimpleDateFormat(FORMAT).parse(value);
        } catch (ParseException e) {
            throw new RuntimeException("解析时间异常(" + value + ")", e);
        }
    }

    /**
     * 增加指定小时值
     */
    public static Date addHours(Date date, int hours) {
        if (hours == 0) {
            return date;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR_OF_DAY, hours);

        return calendar.getTime();
    }
}
