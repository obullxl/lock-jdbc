/**
 * Author: obullxl@163.com
 * Copyright (c) 2020-2023 All Rights Reserved.
 */
package cn.ntopic.lock.impl;

import cn.ntopic.lock.NTLock;
import cn.ntopic.lock.model.NTLockDTO;
import cn.ntopic.lock.model.NTLockResult;
import cn.ntopic.lock.utils.NTDateUtils;
import cn.ntopic.lock.utils.NTHostUtils;
import cn.ntopic.lock.utils.NTJDBCUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务实现
 *
 * @author obullxl 2023年06月24日: 新增
 */
public class NTLockImpl implements NTLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(NTLockImpl.class);

    /**
     * 属性-数据源
     */
    private final DataSource ntDataSource;

    /**
     * 属性-数据表名
     */
    private String tableName = "nt_lock";

    /**
     * 属性-自动清理过期数据（默认1小时执行1次，清理1小时之前过期的数据）
     */
    private boolean autoClean = true;

    /**
     * CTOR-构建锁组件
     */
    public NTLockImpl(DataSource ntDataSource) {
        if (ntDataSource == null) {
            throw new IllegalArgumentException("锁数据源为NULL.");
        }

        this.ntDataSource = ntDataSource;
    }

    /**
     * 初始化
     */
    public void init() {
        // 1. 自动清理
        if (this.isAutoClean()) {
            new NTLockCleanThread(this.ntDataSource, this.tableName).start();
        }
    }

    /**
     * 尝试创建数据表
     */
    public void createTable() {
        Connection conn = null;
        try {
            conn = this.ntDataSource.getConnection();

            // 1. 检测数据表是否存在
            ResultSet rs = null;
            try {
                rs = conn.getMetaData().getTables(null, null, this.tableName, null);
                if (rs.next()) {
                    LOGGER.info("锁数据表存在-无需创建[{}].", this.tableName);
                    return;
                }
            } finally {
                NTJDBCUtils.closeQuietly(rs);
            }

            // 2. 创建数据表
            PreparedStatement stmt = null;
            try {
                StringBuilder createSQL = new StringBuilder();
                createSQL.append(String.format("CREATE TABLE %s", this.tableName));
                createSQL.append("(");
                createSQL.append("pool      VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',");
                createSQL.append("name      VARCHAR(64) NOT NULL,");
                createSQL.append("own_host  VARCHAR(64) NOT NULL,");
                createSQL.append("own_ip    VARCHAR(64) NOT NULL,");
                createSQL.append("own_id    BIGINT      NOT NULL,");
                createSQL.append("expire    VARCHAR(32) NOT NULL,");
                createSQL.append("size      INT         NOT NULL DEFAULT 1,");
                createSQL.append("times     INT         NOT NULL DEFAULT 1,");
                createSQL.append("modify    VARCHAR(32) NOT NULL,");
                createSQL.append("PRIMARY KEY (pool, name)");
                createSQL.append(")");

                String createTableSQL = createSQL.toString();
                LOGGER.info("锁数据表建表SQL:{}", createTableSQL);

                stmt = conn.prepareStatement(createTableSQL);
                stmt.executeUpdate();
                LOGGER.info("创建锁数据表成功[{}].", this.tableName);
            } finally {
                NTJDBCUtils.closeQuietly(stmt);
            }
        } catch (Throwable e) {
            LOGGER.error("检测锁数据表是否存在异常，请求人工创建锁数据表[{}].", this.tableName, e);
            throw new RuntimeException("检测锁数据表是否存在异常，请求人工创建锁数据表(" + this.tableName + ")", e);
        } finally {
            NTJDBCUtils.closeQuietly(conn);
        }
    }

    @Override
    public NTLockResult lock(String lockName, int timeout, TimeUnit timeUnit) {
        final Date now = new Date();

        // 参数检测
        if (lockName == null || lockName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("锁名称参数非法(" + MAX_NAME_LENGTH + ")");
        }

        if (timeout <= 0) {
            throw new IllegalArgumentException("超时时间参数非法(" + timeout + ")");
        }

        // 组装锁信息
        final Date newExpire = new Date(now.getTime() + timeUnit.toMillis(timeout));

        final NTLockDTO newLockDTO = new NTLockDTO(DEFAULT_POOL, lockName, NTHostUtils.HOST, NTHostUtils.IP
                , Thread.currentThread().getId(), NTDateUtils.format(newExpire));
        newLockDTO.setSize(1);
        newLockDTO.setTimes(1);
        newLockDTO.setModify(NTDateUtils.format(new Date()));

        // 尝试抢占或者延长锁
        return this.tryLock(now, newLockDTO);
    }

    @Override
    public NTLockResult lock(NTLockDTO lockDTO, int timeout, TimeUnit timeUnit) {
        final Date now = new Date();

        // 参数检测
        if (lockDTO == null) {
            throw new IllegalArgumentException("锁对象参数为NULL.");
        }

        String pool = lockDTO.getPool();
        if (pool == null || pool.length() > MAX_POOL_LENGTH) {
            throw new IllegalArgumentException("锁池参数非法(" + MAX_POOL_LENGTH + ")");
        }

        String name = lockDTO.getName();
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("锁名称参数非法(" + MAX_NAME_LENGTH + ")");
        }

        String ownHost = lockDTO.getOwnHost();
        String ownIp = lockDTO.getOwnIp();
        long ownId = lockDTO.getOwnId();
        if (ownHost == null || ownIp == null || ownId < 0L) {
            throw new IllegalArgumentException(String.format("锁服务器参数非法(%s/%s/%s)", ownHost, ownIp, ownId));
        }

        if (timeout <= 0) {
            throw new IllegalArgumentException("超时时间参数非法(" + timeout + ")");
        }

        // 组装锁信息
        final Date newExpire = new Date(now.getTime() + timeUnit.toMillis(timeout));

        final NTLockDTO newLockDTO = new NTLockDTO(lockDTO.getPool(), lockDTO.getName(), NTHostUtils.HOST, NTHostUtils.IP
                , Thread.currentThread().getId(), NTDateUtils.format(newExpire));
        newLockDTO.setSize(1);
        newLockDTO.setTimes(1);
        newLockDTO.setModify(NTDateUtils.format(new Date()));

        // 尝试抢占或者延长锁
        return this.tryLock(now, newLockDTO);
    }

    /**
     * 尝试抢占或者延长锁（包括排他锁或者并发池锁）
     */
    private NTLockResult tryLock(final Date now, final NTLockDTO newLockDTO) {
        final String pool = newLockDTO.getPool();
        final String name = newLockDTO.getName();

        // 抢锁：查询 -> 插入 / 过期检测 -> 更新
        Connection conn = null;
        boolean autoCommit = true;
        try {
            conn = this.ntDataSource.getConnection();
            autoCommit = conn.getAutoCommit();

            if (!autoCommit) {
                conn.setAutoCommit(true);
            }

            // 1. 查询是否已经存在锁
            Optional<NTLockDTO> optLockDTO = this.select(conn, newLockDTO.getPool(), newLockDTO.getName());

            if (!optLockDTO.isPresent()) {
                // 2. 不存在锁，插入锁对象
                try {
                    this.insert(conn, newLockDTO);
                    LOGGER.debug("[{}]锁新增抢占成功-{}.", Thread.currentThread().getId(), newLockDTO);
                } catch (Throwable e) {
                    LOGGER.warn("[{}]锁新增抢占异常[{}]-{}.", Thread.currentThread().getId(), e.getMessage(), newLockDTO);
                    return NTLockResult.makeFailure(newLockDTO, String.format("新增锁数据异常(%s->%s)[%s].", pool, name, e.getMessage()));
                }

                // 插入锁/抢锁成功返回
                return NTLockResult.makeSuccess(newLockDTO);
            }

            // 3. 锁已经存在，则检测是否已经过期
            final NTLockDTO existLockDTO = optLockDTO.get();
            final Date existExpire = existLockDTO.fetchExpireTime();

            if (!existExpire.after(now)) {
                // 3.1 当前锁已过期，尝试重新抢占锁定
                if (this.updateTaken(conn, newLockDTO, existLockDTO)) {
                    // 更新锁/延长锁定成功返回
                    LOGGER.debug("[{}]锁过期抢占成功[{}->{}].", Thread.currentThread().getId(), newLockDTO.getPool(), newLockDTO.getName());
                    return NTLockResult.makeSuccess(newLockDTO);
                }

                // 更新锁/延长锁定失败返回
                LOGGER.debug("[{}]锁过期抢占失败[{}->{}].", Thread.currentThread().getId(), newLockDTO.getPool(), newLockDTO.getName());
                return NTLockResult.makeFailure(newLockDTO, "锁已经过期-抢占失败");
            }

            // 3.2 未过期，检测是否为延长锁定
            if (!existLockDTO.getOwnHost().equals(newLockDTO.getOwnHost())
                    || !existLockDTO.getOwnIp().equals(newLockDTO.getOwnIp())
                    || existLockDTO.getOwnId() != newLockDTO.getOwnId()) {
                // 非当前服务器，锁已经被其他抢占，直接失败
                LOGGER.debug("[{}]锁未过期已被占用[{}->{}]-[{}/{}/{}].", Thread.currentThread().getId(), newLockDTO.getPool()
                        , newLockDTO.getName(), existLockDTO.getOwnHost(), existLockDTO.getOwnIp(), existLockDTO.getOwnId());
                return NTLockResult.makeFailure(newLockDTO, String.format("锁已经被(%s/%s/%s)抢占-过期时间(%s)"
                        , existLockDTO.getOwnHost(), existLockDTO.getOwnIp(), existLockDTO.getOwnId(), existLockDTO.getExpire()));
            } else {
                // 当前服务器，未过期，则当前操作为延长锁定
                if (newLockDTO.fetchExpireTime().after(existExpire)) {
                    if (this.updateExpire(conn, newLockDTO, existLockDTO)) {
                        // 更新锁/延长锁定成功返回
                        LOGGER.debug("[{}]锁延长锁定成功[{}->{}].", Thread.currentThread().getId(), newLockDTO.getPool(), newLockDTO.getName());
                        return NTLockResult.makeSuccess(newLockDTO);
                    }

                    // 更新锁失败，但是还未过期，应该返回成功
                    LOGGER.warn("[{}]锁延长锁定失败-返回之前成功[{}->{}].", Thread.currentThread().getId(), newLockDTO.getPool(), newLockDTO.getName());
                    return NTLockResult.makeSuccess(existLockDTO);
                }

                // 当前锁定时间更晚，直接返回
                return NTLockResult.makeSuccess(existLockDTO);
            }
        } catch (Throwable e) {
            LOGGER.error("排他锁抢占未知异常-{}.", newLockDTO, e);
            return NTLockResult.makeFailure(newLockDTO, String.format("排他锁抢占未知异常(%s->%s)", pool, name));
        } finally {
            if (!autoCommit) {
                NTJDBCUtils.closeAutoCommit(conn);
            }

            NTJDBCUtils.closeQuietly(conn);
        }
    }

    @Override
    public NTLockResult lockPool(String poolName, int count, int timeout, TimeUnit timeUnit) {
        final Date now = new Date();

        // 参数检测
        if (poolName == null || poolName.length() > MAX_POOL_LENGTH) {
            throw new IllegalArgumentException("并发锁池名称参数非法(" + MAX_POOL_LENGTH + ")");
        }

        if (count <= 0) {
            throw new IllegalArgumentException("锁池并发数量参数非法(" + count + ")");
        }

        if (timeout <= 0) {
            throw new IllegalArgumentException("超时时间参数非法(" + timeout + ")");
        }

        // 抢占或者延长抢占锁时间
        // TODO:
        return null;
    }

    @Override
    public boolean release(String lockName) {
        // 参数检测
        if (lockName == null || lockName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("锁名称参数非法(" + MAX_NAME_LENGTH + ")");
        }

        // 组装锁信息
        final NTLockDTO newLockDTO = new NTLockDTO(DEFAULT_POOL, lockName, NTHostUtils.HOST, NTHostUtils.IP
                , Thread.currentThread().getId(), NTDateUtils.format(new Date()));
        newLockDTO.setSize(1);
        newLockDTO.setTimes(1);
        newLockDTO.setModify(NTDateUtils.format(new Date()));

        // 释放排它锁或并发池锁
        return this.release(newLockDTO);
    }

    @Override
    public boolean release(final NTLockDTO lockDTO) {
        // 参数检测
        if (lockDTO == null) {
            throw new IllegalArgumentException("锁对象参数为NULL.");
        }

        String pool = lockDTO.getPool();
        if (pool == null || pool.length() > MAX_POOL_LENGTH) {
            throw new IllegalArgumentException("锁池参数非法(" + MAX_POOL_LENGTH + ")");
        }

        String name = lockDTO.getName();
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("锁名称参数非法(" + MAX_NAME_LENGTH + ")");
        }

        String ownHost = lockDTO.getOwnHost();
        String ownIp = lockDTO.getOwnIp();
        long ownId = lockDTO.getOwnId();
        if (ownHost == null || ownIp == null || ownId < 0L) {
            throw new IllegalArgumentException(String.format("锁服务器参数非法(%s/%s/%s)", ownHost, ownIp, ownId));
        }

        // 删除数据表记录
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean autoCommit = true;
        try {
            // 数据库连接
            conn = this.ntDataSource.getConnection();

            autoCommit = conn.getAutoCommit();
            if (!autoCommit) {
                conn.setAutoCommit(true);
            }

            // 删除锁记录
            String deleteSQL = String.format("DELETE FROM %s WHERE pool=? AND name=? AND own_host=? AND own_ip=? AND own_id=?", this.tableName);
            stmt = conn.prepareStatement(deleteSQL);

            stmt.setString(1, pool);
            stmt.setString(2, name);
            stmt.setString(3, ownHost);
            stmt.setString(4, ownIp);
            stmt.setLong(5, ownId);

            return stmt.executeUpdate() > 0;
        } catch (Throwable e) {
            LOGGER.error("锁释放删除记录异常-{}.", lockDTO, e);
            return false;
        } finally {
            if (!autoCommit) {
                NTJDBCUtils.closeAutoCommit(conn);
            }

            NTJDBCUtils.closeQuietly(stmt);
            NTJDBCUtils.closeQuietly(conn);
        }
    }

    /**
     * 查询锁信息
     */
    private Optional<NTLockDTO> select(Connection conn, String pool, String name) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            String selectSQL = String.format("SELECT * FROM %s WHERE pool=? AND name=?", this.tableName);
            stmt = conn.prepareStatement(selectSQL);
            stmt.setString(1, pool);
            stmt.setString(2, name);

            rs = stmt.executeQuery();

            return this.makeLockDTO(rs);
        } finally {
            NTJDBCUtils.closeQuietly(rs);
            NTJDBCUtils.closeQuietly(stmt);
        }
    }

    /**
     * 插入锁信息
     */
    private void insert(Connection conn, NTLockDTO newLockDTO) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String insertSQL = String.format("INSERT INTO %s (pool,name,own_host,own_ip,own_id,expire,size,times,modify) VALUES (?,?,?,?,?,?,?,?,?)", this.tableName);
            stmt = conn.prepareStatement(insertSQL);

            stmt.setString(1, newLockDTO.getPool());
            stmt.setString(2, newLockDTO.getName());
            stmt.setString(3, newLockDTO.getOwnHost());
            stmt.setString(4, newLockDTO.getOwnIp());
            stmt.setLong(5, newLockDTO.getOwnId());
            stmt.setString(6, newLockDTO.getExpire());
            stmt.setInt(7, newLockDTO.getSize());
            stmt.setInt(8, newLockDTO.getTimes());
            stmt.setString(9, newLockDTO.getModify());

            stmt.executeUpdate();
        } finally {
            NTJDBCUtils.closeQuietly(stmt);
        }
    }

    /**
     * 更新锁信息--延长锁定
     */
    private boolean updateExpire(Connection conn, NTLockDTO newLockDTO, NTLockDTO existLockDTO) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String updateSQL = String.format("UPDATE %s SET expire=?,times=times+1,modify=? WHERE pool=? AND name=? AND own_host=? AND own_ip=? AND own_id=? AND expire=?", this.tableName);
            stmt = conn.prepareStatement(updateSQL);

            stmt.setString(1, newLockDTO.getExpire());
            stmt.setString(2, newLockDTO.getModify());
            stmt.setString(3, newLockDTO.getPool());
            stmt.setString(4, newLockDTO.getName());
            stmt.setString(5, existLockDTO.getOwnHost());
            stmt.setString(6, existLockDTO.getOwnIp());
            stmt.setLong(7, existLockDTO.getOwnId());
            stmt.setString(8, existLockDTO.getExpire());

            boolean update = stmt.executeUpdate() >= 1;
            if (update) {
                newLockDTO.setTimes(newLockDTO.getTimes() + 1);
            }

            return update;
        } finally {
            NTJDBCUtils.closeQuietly(stmt);
        }
    }

    /**
     * 更新锁信息--抢占锁定
     */
    private boolean updateTaken(Connection conn, NTLockDTO newLockDTO, NTLockDTO existLockDTO) throws SQLException {
        PreparedStatement stmt = null;
        try {
            String updateSQL = String.format("UPDATE %s SET own_host=?,own_ip=?,own_id=?,expire=?,times=1,modify=? WHERE pool=? AND name=? AND own_host=? AND own_ip=? AND own_id=? AND expire=?", this.tableName);
            stmt = conn.prepareStatement(updateSQL);

            stmt.setString(1, newLockDTO.getOwnHost());
            stmt.setString(2, newLockDTO.getOwnIp());
            stmt.setLong(3, newLockDTO.getOwnId());
            stmt.setString(4, newLockDTO.getExpire());
            stmt.setString(5, newLockDTO.getModify());
            stmt.setString(6, newLockDTO.getPool());
            stmt.setString(7, newLockDTO.getName());
            stmt.setString(8, existLockDTO.getOwnHost());
            stmt.setString(9, existLockDTO.getOwnIp());
            stmt.setLong(10, existLockDTO.getOwnId());
            stmt.setString(11, existLockDTO.getExpire());

            boolean update = stmt.executeUpdate() >= 1;
            if (update) {
                newLockDTO.setTimes(newLockDTO.getTimes() + 1);
            }

            return update;
        } finally {
            NTJDBCUtils.closeQuietly(stmt);
        }
    }

    /**
     * 构建锁对象
     */
    private Optional<NTLockDTO> makeLockDTO(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }

        // 组装锁对象
        NTLockDTO lockDTO = new NTLockDTO(rs.getString("pool"), rs.getString("name")
                , rs.getString("own_host"), rs.getString("own_ip"), rs.getLong("own_id"), rs.getString("expire"));
        lockDTO.setSize(rs.getInt("size"));
        lockDTO.setTimes(rs.getInt("times"));
        lockDTO.setModify(rs.getString("modify"));

        return Optional.of(lockDTO);
    }

    /**
     * 自动清理过期数据线程
     */
    private static class NTLockCleanThread extends Thread {
        /**
         * 锁数据表数据源
         */
        private final DataSource ntDataSource;

        /**
         * 数据表名
         */
        private final String tableName;

        public NTLockCleanThread(DataSource ntDataSource, String tableName) {
            this.ntDataSource = ntDataSource;
            this.tableName = tableName;
        }

        @Override
        public void run() {
            for (; ; ) {
                try {
                    // 清理
                    this.clean();

                    // 睡眠
                    Thread.sleep(TimeUnit.HOURS.toMillis(1L));
                } catch (Throwable e) {
                    LOGGER.error("周期清理过期数据异常[{}].", this.tableName, e);
                }
            }
        }

        /**
         * 清理过期数据
         */
        private void clean() {
            Connection conn = null;
            PreparedStatement stmt = null;
            boolean autoCommit = true;
            try {
                // 数据库连接
                conn = this.ntDataSource.getConnection();
                autoCommit = conn.getAutoCommit();

                if (!autoCommit) {
                    conn.setAutoCommit(true);
                }

                // 清理数据记录
                String expire = NTDateUtils.format(NTDateUtils.addHours(new Date(), -1));
                String deleteSQL = String.format("DELETE FROM %s WHERE expire<=?", this.tableName);
                stmt = conn.prepareStatement(deleteSQL);

                stmt.setString(1, expire);
                int count = stmt.executeUpdate();

                LOGGER.info("自动清理过期数据[{}]条[{}].", count, this.tableName);
            } catch (Throwable e) {
                LOGGER.warn("清理锁过期数据异常[{}].", this.tableName, e);
            } finally {
                if (!autoCommit) {
                    NTJDBCUtils.closeAutoCommit(conn);
                }

                NTJDBCUtils.closeQuietly(stmt);
                NTJDBCUtils.closeQuietly(conn);
            }
        }
    }

    // ~~~~~~~~~~~~~ getters and setters ~~~~~~~~~~~~~~ //

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("锁数据表名参数非法(" + tableName + ")");
        }

        this.tableName = tableName;
    }

    public boolean isAutoClean() {
        return autoClean;
    }

    public void setAutoClean(boolean autoClean) {
        this.autoClean = autoClean;
    }
}
