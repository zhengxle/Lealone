/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import org.lealone.engine.Constants;
import org.lealone.message.DbException;

/**
 * The database driver. An application should not use this class directly. 
 */
public class Driver implements java.sql.Driver {

    private static final Driver INSTANCE = new Driver();
    private static final String DEFAULT_URL = Constants.CONN_URL_INTERNAL;
    private static final ThreadLocal<Connection> DEFAULT_CONNECTION = new ThreadLocal<>();

    private static volatile boolean registered;

    static {
        load();
    }

    /**
     * Open a database connection.
     * This method should not be called by an application.
     * Instead, the method DriverManager.getConnection should be used.
     *
     * @param url the database URL
     * @param info the connection properties
     * @return the new connection or null if the URL is not supported
     */
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        try {
            if (info == null) {
                info = new Properties();
            }
            if (!acceptsURL(url)) {
                return null;
            }
            if (url.equals(DEFAULT_URL)) {
                return DEFAULT_CONNECTION.get();
            }

            return new JdbcConnection(url, info);
        } catch (Exception e) {
            throw DbException.toSQLException(e);
        }
    }

    /**
     * Check if the driver understands this URL.
     * This method should not be called by an application.
     *
     * @param url the database URL
     * @return if the driver understands the URL
     */
    @Override
    public boolean acceptsURL(String url) {
        if (url != null) {
            if (url.startsWith(Constants.URL_PREFIX)) {
                return true;
            } else if (url.equals(DEFAULT_URL)) {
                return DEFAULT_CONNECTION.get() != null;
            }
        }
        return false;
    }

    /**
     * Get the major version number of the driver.
     * This method should not be called by an application.
     *
     * @return the major version number
     */
    @Override
    public int getMajorVersion() {
        return Constants.VERSION_MAJOR;
    }

    /**
     * Get the minor version number of the driver.
     * This method should not be called by an application.
     *
     * @return the minor version number
     */
    @Override
    public int getMinorVersion() {
        return Constants.VERSION_MINOR;
    }

    /**
     * Get the list of supported properties.
     * This method should not be called by an application.
     *
     * @param url the database URL
     * @param info the connection properties
     * @return a zero length array
     */
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    /**
     * Check if this driver is compliant to the JDBC specification.
     * This method should not be called by an application.
     *
     * @return true
     */
    @Override
    public boolean jdbcCompliant() {
        return true;
    }

    /**
     * [Not supported]
     */
    // jdk1.7
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw DbException.getUnsupportedException("getParentLogger()");
    }

    /**
     * INTERNAL
     */
    public static synchronized Driver load() {
        if (!registered) {
            registered = true;

            try {
                DriverManager.registerDriver(INSTANCE);
            } catch (SQLException e) {
                DbException.traceThrowable(e);
            }
        }
        return INSTANCE;
    }

    /**
     * INTERNAL
     */
    public static synchronized void unload() {
        if (registered) {
            registered = false;

            try {
                DriverManager.deregisterDriver(INSTANCE);
            } catch (SQLException e) {
                DbException.traceThrowable(e);
            }
        }
    }

    /**
     * INTERNAL
     */
    public static void setDefaultConnection(Connection c) {
        DEFAULT_CONNECTION.set(c);
    }

    /**
     * INTERNAL
     */
    public static void setThreadContextClassLoader(Thread thread) {
        // Apache Tomcat: use the classloader of the driver to avoid the
        // following log message:
        // org.apache.catalina.loader.WebappClassLoader clearReferencesThreads
        // SEVERE: The web application appears to have started a thread named
        // ... but has failed to stop it.
        // This is very likely to create a memory leak.
        try {
            thread.setContextClassLoader(Driver.class.getClassLoader());
        } catch (Throwable t) {
            // ignore
        }
    }

    /**
     * INTERNAL
     */
    public static Connection getConnection(String url, String user, String password) throws SQLException {
        Properties info = new Properties();
        info.setProperty("user", user);
        info.setProperty("password", password);
        return load().connect(url, info);
    }
}
