/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.command.dml;

import org.lealone.command.CommandInterface;
import org.lealone.command.Prepared;
import org.lealone.engine.Database;
import org.lealone.engine.Session;
import org.lealone.message.DbException;
import org.lealone.result.ResultInterface;

/**
 * Represents a transactional statement.
 */
public class TransactionCommand extends Prepared {
    public static final String INTERNAL_SAVEPOINT = "_INTERNAL_SAVEPOINT_";

    private final int type;
    private String savepointName;
    private String transactionName;

    public TransactionCommand(Session session, int type) {
        super(session);
        this.type = type;
    }

    public void setSavepointName(String name) {
        if (INTERNAL_SAVEPOINT.equals(name))
            throw DbException.getUnsupportedException("Savepoint name cannot use " + INTERNAL_SAVEPOINT);
        this.savepointName = name;
    }

    @Override
    public int update() {
        switch (type) {
        case CommandInterface.SET_AUTOCOMMIT_TRUE:
            session.setAutoCommit(true);
            break;
        case CommandInterface.SET_AUTOCOMMIT_FALSE:
            session.setAutoCommit(false);
            break;
        case CommandInterface.BEGIN:
            session.begin();
            break;
        case CommandInterface.COMMIT:
            session.commit(false);
            break;
        case CommandInterface.ROLLBACK:
            session.rollback();
            break;
        case CommandInterface.CHECKPOINT:
            session.getUser().checkAdmin();
            session.getDatabase().checkpoint();
            break;
        case CommandInterface.SAVEPOINT:
            session.addSavepoint(savepointName);
            break;
        case CommandInterface.ROLLBACK_TO_SAVEPOINT:
            session.rollbackToSavepoint(savepointName);
            break;
        case CommandInterface.CHECKPOINT_SYNC:
            session.getUser().checkAdmin();
            session.getDatabase().sync();
            break;
        case CommandInterface.PREPARE_COMMIT:
            session.prepareCommit(transactionName);
            break;
        case CommandInterface.COMMIT_TRANSACTION:
            session.getUser().checkAdmin();
            session.setPreparedTransaction(transactionName, true);
            break;
        case CommandInterface.ROLLBACK_TRANSACTION:
            session.getUser().checkAdmin();
            session.setPreparedTransaction(transactionName, false);
            break;
        case CommandInterface.SHUTDOWN_IMMEDIATELY:
            session.getUser().checkAdmin();
            session.getDatabase().shutdownImmediately();
            break;
        case CommandInterface.SHUTDOWN:
        case CommandInterface.SHUTDOWN_COMPACT:
        case CommandInterface.SHUTDOWN_DEFRAG: {
            session.getUser().checkAdmin();
            session.commit(false);
            if (type == CommandInterface.SHUTDOWN_COMPACT || type == CommandInterface.SHUTDOWN_DEFRAG) {
                session.getDatabase().setCompactMode(type);
            }
            // close the database, but don't update the persistent setting
            session.getDatabase().setCloseDelay(0);
            Database db = session.getDatabase();
            // throttle, to allow testing concurrent
            // execution of shutdown and query
            session.throttle();
            for (Session s : db.getSessions(false)) {
                if (db.isMultiThreaded()) {
                    synchronized (s) {
                        s.rollback();
                    }
                } else {
                    // if not multi-threaded, the session could already own
                    // the lock, which would result in a deadlock
                    // the other session can not concurrently do anything
                    // because the current session has locked the database
                    s.rollback();
                }
                if (s != session) {
                    s.close();
                }
            }
            session.close();
            break;
        }
        default:
            DbException.throwInternalError("type=" + type);
        }
        return 0;
    }

    @Override
    public boolean isTransactional() {
        return true;
    }

    @Override
    public boolean needRecompile() {
        return false;
    }

    public void setTransactionName(String string) {
        this.transactionName = string;
    }

    @Override
    public ResultInterface queryMeta() {
        return null;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

}
