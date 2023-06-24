/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock;

import cn.ntopic.lock.impl.NTLockImpl;
import cn.ntopic.lock.model.NTLockDTO;
import cn.ntopic.lock.model.NTLockResult;
import cn.ntopic.lock.utils.NTJDBCUtils;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务单元测试
 *
 * @author obullxl 2023年06月22日: 新增
 */
public class NTLockTest {

    private DruidDataSource makeDataSource() {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:sqlite:/Users/obullxl/CodeSpace/lock-jdbc/LockJDBC.sqlite");
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setPoolPreparedStatements(false);
        dataSource.setMaxPoolPreparedStatementPerConnectionSize(-1);
        dataSource.setTestOnBorrow(true);
        dataSource.setTestOnReturn(false);
        dataSource.setTestWhileIdle(true);
        // dataSource.setValidationQuery("SELECT '1' FROM sqlite_master LIMIT 1");

        return dataSource;
    }

    @Test
    public void test_lock() {
        // 1. 创建数据源
        DruidDataSource dataSource = this.makeDataSource();

        final String testName = "TEST-" + System.currentTimeMillis() + "-" + System.nanoTime();
        try {
            // 2. 清理数据

            // 3. 实例化锁服务
            NTLockImpl ntLock = new NTLockImpl(dataSource);
            ntLock.createTable();
            ntLock.init();

            // 4. 并发测试
            this.multiThreadTest(ntLock, testName);

            // 5. 清理测试数据
        } finally {
            dataSource.close();
        }
    }

    @Test
    public void test_release() {
        // 1. 创建数据源
        DruidDataSource dataSource = this.makeDataSource();

        final String testName = "TEST-" + System.currentTimeMillis() + "-" + System.nanoTime();
        try {
            // 2. 实例化锁服务
            NTLockImpl ntLock = new NTLockImpl(dataSource);
            ntLock.createTable();
            ntLock.init();

            // 3. 排它锁抢占
            NTLockResult lockResult = ntLock.lock(testName, 10, TimeUnit.SECONDS);
            Assert.assertTrue(lockResult.isSuccess());

            // 4. 是否排它锁
            boolean release = ntLock.release(testName);
            Assert.assertTrue(release);

            // 5. 检测数据记录-不存在
            Assert.assertFalse(this.checkLockDTO(dataSource, ntLock, lockResult.getLockDTO()));
        } finally {
            dataSource.close();
        }
    }

    @Test
    public void test_release_NTLockDTO() {
        // 1. 创建数据源
        DruidDataSource dataSource = this.makeDataSource();

        final String testName = "TEST-" + System.currentTimeMillis() + "-" + System.nanoTime();
        try {
            // 2. 实例化锁服务
            NTLockImpl ntLock = new NTLockImpl(dataSource);
            ntLock.createTable();
            ntLock.init();

            // 3. 排它锁抢占
            NTLockResult lockResult = ntLock.lock(testName, 10, TimeUnit.SECONDS);
            Assert.assertTrue(lockResult.isSuccess());

            // 4. 是否排它锁
            boolean release = ntLock.release(lockResult.getLockDTO());
            Assert.assertTrue(release);

            // 5. 检测数据记录-不存在
            Assert.assertFalse(this.checkLockDTO(dataSource, ntLock, lockResult.getLockDTO()));
        } finally {
            dataSource.close();
        }
    }

    /**
     * 查询锁记录
     */
    private boolean checkLockDTO(DataSource dataSource, NTLockImpl ntLock, NTLockDTO lockDTO) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = dataSource.getConnection();

            String selectSQL = String.format("SELECT * FROM %s WHERE pool=? AND name=?", ntLock.getTableName());
            stmt = conn.prepareStatement(selectSQL);

            stmt.setString(1, lockDTO.getPool());
            stmt.setString(2, lockDTO.getName());

            rs = stmt.executeQuery();
            return rs.next();
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        } finally {
            NTJDBCUtils.closeQuietly(rs);
            NTJDBCUtils.closeQuietly(stmt);
            NTJDBCUtils.closeQuietly(conn);
        }
    }

    private void multiThreadTest(NTLock ntLock, String testName) {
        final int threadCount = 3;
        final int tryTimes = 107;

        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        Map<Integer, List<Boolean>> results = new ConcurrentHashMap<>();
        for (int i = 0; i < threadCount; i++) {
            List<Boolean> resultValues = new ArrayList<>();
            results.put(i, resultValues);

            new LockThread(countDownLatch, tryTimes, ntLock, testName, resultValues).start();
        }

        try {
            countDownLatch.await();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // 数据检测
        Set<Boolean> allValues = new HashSet<>();

        Assert.assertEquals(threadCount, results.size());
        for (int i = 0; i < threadCount; i++) {
            allValues.addAll(results.get(i));
            Assert.assertEquals(tryTimes, results.get(i).size());
        }

        Assert.assertTrue(allValues.contains(true));
        Assert.assertTrue(allValues.contains(false));
    }

    private static class LockThread extends Thread {
        private final int tryTimes;
        private final CountDownLatch countDownLatch;
        private final NTLock ntLock;
        private final String testName;
        private final List<Boolean> resultValues;

        public LockThread(CountDownLatch countDownLatch, int tryTimes, NTLock ntLock, String testName, List<Boolean> resultValues) {
            this.countDownLatch = countDownLatch;
            this.tryTimes = tryTimes;
            this.ntLock = ntLock;
            this.testName = testName;
            this.resultValues = resultValues;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < tryTimes; i++) {
                    try {
                        this.resultValues.add(this.ntLock.lock(this.testName, 3, TimeUnit.SECONDS).isSuccess());
                        Thread.sleep(new Random().nextInt(100));
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }

                // 释放信号
                this.countDownLatch.countDown();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
