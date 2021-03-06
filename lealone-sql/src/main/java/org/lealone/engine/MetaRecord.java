/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.engine;

import java.sql.SQLException;

import org.lealone.api.DatabaseEventListener;
import org.lealone.command.Prepared;
import org.lealone.dbobject.DbObject;
import org.lealone.message.DbException;
import org.lealone.message.Trace;
import org.lealone.result.SearchRow;
import org.lealone.value.ValueInt;
import org.lealone.value.ValueString;

/**
 * A record in the system table of the database.
 * It contains the SQL statement to create the database object.
 */
public class MetaRecord implements Comparable<MetaRecord> {

    private final int id;
    private final int objectType;
    private final String sql;

    public MetaRecord(SearchRow r) {
        id = r.getValue(0).getInt();
        objectType = r.getValue(1).getInt();
        sql = r.getValue(2).getString();
    }

    public MetaRecord(DbObject obj) {
        id = obj.getId();
        objectType = obj.getType();
        sql = obj.getCreateSQL();
    }

    public void setRecord(SearchRow r) {
        r.setValue(0, ValueInt.get(id));
        r.setValue(1, ValueInt.get(objectType));
        r.setValue(2, ValueString.get(sql));
    }

    /**
     * Execute the meta data statement.
     *
     * @param db the database
     * @param systemSession the system session
     * @param listener the database event listener
     */
    public void execute(Database db, Session systemSession, DatabaseEventListener listener) {
        try {
            Prepared command = systemSession.prepare(sql);
            command.setObjectId(id);
            command.setLocal(true);
            command.update();
        } catch (DbException e) {
            e = e.addSQL(sql);
            SQLException s = e.getSQLException();
            db.getTrace(Trace.DATABASE).error(s, sql);
            if (listener != null) {
                listener.exceptionThrown(s, sql);
                // continue startup in this case
            } else {
                throw e;
            }
        }
    }

    public int getId() {
        return id;
    }

    public int getObjectType() {
        return objectType;
    }

    public String getSQL() {
        return sql;
    }

    /**
     * Sort the list of meta records by 'create order'.
     *
     * @param other the other record
     * @return -1, 0, or 1
     */
    @Override
    public int compareTo(MetaRecord other) {
        int c1 = getCreateOrder();
        int c2 = other.getCreateOrder();
        if (c1 != c2) {
            return c1 - c2;
        }
        return getId() - other.getId();
    }

    /**
     * Get the sort order id for this object type. Objects are created in this
     * order when opening a database.
     *
     * @return the sort index
     */
    private int getCreateOrder() {
        switch (objectType) {
        case DbObject.SETTING:
            return 0;
        case DbObject.USER:
            return 1;
        case DbObject.SCHEMA:
            return 2;
        case DbObject.FUNCTION_ALIAS:
            return 3;
        case DbObject.USER_DATATYPE:
            return 4;
        case DbObject.SEQUENCE:
            return 5;
        case DbObject.CONSTANT:
            return 6;
        case DbObject.TABLE_OR_VIEW:
            return 7;
        case DbObject.INDEX:
            return 8;
        case DbObject.CONSTRAINT:
            return 9;
        case DbObject.TRIGGER:
            return 10;
        case DbObject.ROLE:
            return 11;
        case DbObject.RIGHT:
            return 12;
        case DbObject.AGGREGATE:
            return 13;
        case DbObject.COMMENT:
            return 14;
        default:
            throw DbException.throwInternalError("type=" + objectType);
        }
    }

    @Override
    public String toString() {
        return "MetaRecord [id=" + id + ", objectType=" + objectType + ", sql=" + sql + "]";
    }

}
