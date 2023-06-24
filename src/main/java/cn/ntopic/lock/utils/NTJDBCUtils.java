/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.utils;

import java.sql.Connection;

/**
 * JDBC工具类
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTJDBCUtils {

    /**
     * 设置非自动提交
     */
    public static void closeAutoCommit(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(false);
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    /**
     * 尝试关闭
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

}
