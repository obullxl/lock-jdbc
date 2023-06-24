/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 服务器工具类
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTHostUtils {

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


}
