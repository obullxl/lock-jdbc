/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 分布式工具类
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTLockUtils {

    /**
     * 当前服务器
     */
    public static final String HOST;

    /**
     * 当前服务器IP地址
     */
    public static final String IP;

    static {
        String host;
        String ip;
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            host = inetAddress.getHostName();
            ip = inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
            host = "localhost";
            ip = "127.0.0.1";
        }

        HOST = host;
        IP = ip;
    }

    /**
     * 格式化时间
     */
    public static String format(Date time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(time);
    }

    /**
     * 解析时间对象
     *
     * @throws RuntimeException 解析时间异常
     */
    public static Date parse(String value) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(value);
        } catch (ParseException e) {
            throw new RuntimeException("解析时间异常(" + value + ")", e);
        }
    }

}
