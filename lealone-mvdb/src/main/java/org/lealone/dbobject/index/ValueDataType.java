/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.dbobject.index;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;

import org.lealone.api.ErrorCode;
import org.lealone.engine.DataHandler;
import org.lealone.message.DbException;
import org.lealone.result.SimpleResultSet;
import org.lealone.result.SortOrder;
import org.lealone.type.DataType;
import org.lealone.type.WriteBuffer;
import org.lealone.util.DataUtils;
import org.lealone.value.CompareMode;
import org.lealone.value.Value;
import org.lealone.value.ValueArray;
import org.lealone.value.ValueBoolean;
import org.lealone.value.ValueByte;
import org.lealone.value.ValueBytes;
import org.lealone.value.ValueDate;
import org.lealone.value.ValueDecimal;
import org.lealone.value.ValueDouble;
import org.lealone.value.ValueFloat;
import org.lealone.value.ValueInt;
import org.lealone.value.ValueJavaObject;
import org.lealone.value.ValueLobDb;
import org.lealone.value.ValueLong;
import org.lealone.value.ValueNull;
import org.lealone.value.ValueResultSet;
import org.lealone.value.ValueShort;
import org.lealone.value.ValueString;
import org.lealone.value.ValueStringFixed;
import org.lealone.value.ValueStringIgnoreCase;
import org.lealone.value.ValueTime;
import org.lealone.value.ValueTimestamp;
import org.lealone.value.ValueUuid;

/**
 * A row type.
 */
public class ValueDataType implements DataType {

    private static final int INT_0_15 = 32;
    private static final int LONG_0_7 = 48;
    private static final int DECIMAL_0_1 = 56;
    private static final int DECIMAL_SMALL_0 = 58;
    private static final int DECIMAL_SMALL = 59;
    private static final int DOUBLE_0_1 = 60;
    private static final int FLOAT_0_1 = 62;
    private static final int BOOLEAN_FALSE = 64;
    private static final int BOOLEAN_TRUE = 65;
    private static final int INT_NEG = 66;
    private static final int LONG_NEG = 67;
    private static final int STRING_0_31 = 68;
    private static final int BYTES_0_31 = 100;

    final DataHandler handler;
    final CompareMode compareMode;
    final int[] sortTypes;

    public ValueDataType(CompareMode compareMode, DataHandler handler, int[] sortTypes) {
        this.compareMode = compareMode;
        this.handler = handler;
        this.sortTypes = sortTypes;
    }

    @Override
    public int compare(Object a, Object b) {
        if (a == b) {
            return 0;
        }
        if (a instanceof ValueArray && b instanceof ValueArray) {
            Value[] ax = ((ValueArray) a).getList();
            Value[] bx = ((ValueArray) b).getList();
            int al = ax.length;
            int bl = bx.length;
            int len = Math.min(al, bl);
            for (int i = 0; i < len; i++) {
                int sortType = sortTypes[i];
                int comp = compareValues(ax[i], bx[i], sortType);
                if (comp != 0) {
                    return comp;
                }
            }
            if (len < al) {
                return -1;
            } else if (len < bl) {
                return 1;
            }
            return 0;
        }
        return compareValues((Value) a, (Value) b, SortOrder.ASCENDING);
    }

    private int compareValues(Value a, Value b, int sortType) {
        if (a == b) {
            return 0;
        }
        // null is never stored;
        // comparison with null is used to retrieve all entries
        // in which case null is always lower than all entries
        // (even for descending ordered indexes)
        if (a == null) {
            return -1;
        } else if (b == null) {
            return 1;
        }
        boolean aNull = a == ValueNull.INSTANCE;
        boolean bNull = b == ValueNull.INSTANCE;
        if (aNull || bNull) {
            return SortOrder.compareNull(aNull, sortType);
        }
        int comp = compareTypeSafe(a, b);
        if ((sortType & SortOrder.DESCENDING) != 0) {
            comp = -comp;
        }
        return comp;
    }

    private int compareTypeSafe(Value a, Value b) {
        if (a == b) {
            return 0;
        }
        return a.compareTypeSafe(b, compareMode);
    }

    @Override
    public int getMemory(Object obj) {
        return getMemory((Value) obj);
    }

    private static int getMemory(Value v) {
        return v == null ? 0 : v.getMemory();
    }

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            obj[i] = read(buff);
        }
    }

    @Override
    public void write(WriteBuffer buff, Object[] obj, int len, boolean key) {
        for (int i = 0; i < len; i++) {
            write(buff, obj[i]);
        }
    }

    @Override
    public Object read(ByteBuffer buff) {
        return readValue(buff);
    }

    @Override
    public void write(WriteBuffer buff, Object obj) {
        Value x = (Value) obj;
        writeValue(buff, x);
    }

    private void writeValue(WriteBuffer buff, Value v) {
        if (v == ValueNull.INSTANCE) {
            buff.put((byte) 0);
            return;
        }
        int type = v.getType();
        switch (type) {
        case Value.BOOLEAN:
            buff.put((byte) (v.getBoolean().booleanValue() ? BOOLEAN_TRUE : BOOLEAN_FALSE));
            break;
        case Value.BYTE:
            buff.put((byte) type).put(v.getByte());
            break;
        case Value.SHORT:
            buff.put((byte) type).putShort(v.getShort());
            break;
        case Value.INT: {
            int x = v.getInt();
            if (x < 0) {
                buff.put((byte) INT_NEG).putVarInt(-x);
            } else if (x < 16) {
                buff.put((byte) (INT_0_15 + x));
            } else {
                buff.put((byte) type).putVarInt(x);
            }
            break;
        }
        case Value.LONG: {
            long x = v.getLong();
            if (x < 0) {
                buff.put((byte) LONG_NEG).putVarLong(-x);
            } else if (x < 8) {
                buff.put((byte) (LONG_0_7 + x));
            } else {
                buff.put((byte) type).putVarLong(x);
            }
            break;
        }
        case Value.DECIMAL: {
            BigDecimal x = v.getBigDecimal();
            if (BigDecimal.ZERO.equals(x)) {
                buff.put((byte) DECIMAL_0_1);
            } else if (BigDecimal.ONE.equals(x)) {
                buff.put((byte) (DECIMAL_0_1 + 1));
            } else {
                int scale = x.scale();
                BigInteger b = x.unscaledValue();
                int bits = b.bitLength();
                if (bits <= 63) {
                    if (scale == 0) {
                        buff.put((byte) DECIMAL_SMALL_0).putVarLong(b.longValue());
                    } else {
                        buff.put((byte) DECIMAL_SMALL).putVarInt(scale).putVarLong(b.longValue());
                    }
                } else {
                    byte[] bytes = b.toByteArray();
                    buff.put((byte) type).putVarInt(scale).putVarInt(bytes.length).put(bytes);
                }
            }
            break;
        }
        case Value.TIME: {
            ValueTime t = (ValueTime) v;
            long nanos = t.getNanos();
            long millis = nanos / 1000000;
            nanos -= millis * 1000000;
            buff.put((byte) type).putVarLong(millis).putVarLong(nanos);
            break;
        }
        case Value.DATE: {
            long x = ((ValueDate) v).getDateValue();
            buff.put((byte) type).putVarLong(x);
            break;
        }
        case Value.TIMESTAMP: {
            ValueTimestamp ts = (ValueTimestamp) v;
            long dateValue = ts.getDateValue();
            long nanos = ts.getNanos();
            long millis = nanos / 1000000;
            nanos -= millis * 1000000;
            buff.put((byte) type).putVarLong(dateValue).putVarLong(millis).putVarLong(nanos);
            break;
        }
        case Value.JAVA_OBJECT: {
            byte[] b = v.getBytesNoCopy();
            buff.put((byte) type).putVarInt(b.length).put(b);
            break;
        }
        case Value.BYTES: {
            byte[] b = v.getBytesNoCopy();
            int len = b.length;
            if (len < 32) {
                buff.put((byte) (BYTES_0_31 + len)).put(b);
            } else {
                buff.put((byte) type).putVarInt(b.length).put(b);
            }
            break;
        }
        case Value.UUID: {
            ValueUuid uuid = (ValueUuid) v;
            buff.put((byte) type).putLong(uuid.getHigh()).putLong(uuid.getLow());
            break;
        }
        case Value.STRING: {
            String s = v.getString();
            int len = s.length();
            if (len < 32) {
                buff.put((byte) (STRING_0_31 + len)).putStringData(s, len);
            } else {
                buff.put((byte) type);
                writeString(buff, s);
            }
            break;
        }
        case Value.STRING_IGNORECASE:
        case Value.STRING_FIXED:
            buff.put((byte) type);
            writeString(buff, v.getString());
            break;
        case Value.DOUBLE: {
            double x = v.getDouble();
            if (x == 1.0d) {
                buff.put((byte) (DOUBLE_0_1 + 1));
            } else {
                long d = Double.doubleToLongBits(x);
                if (d == ValueDouble.ZERO_BITS) {
                    buff.put((byte) DOUBLE_0_1);
                } else {
                    buff.put((byte) type).putVarLong(Long.reverse(d));
                }
            }
            break;
        }
        case Value.FLOAT: {
            float x = v.getFloat();
            if (x == 1.0f) {
                buff.put((byte) (FLOAT_0_1 + 1));
            } else {
                int f = Float.floatToIntBits(x);
                if (f == ValueFloat.ZERO_BITS) {
                    buff.put((byte) FLOAT_0_1);
                } else {
                    buff.put((byte) type).putVarInt(Integer.reverse(f));
                }
            }
            break;
        }
        case Value.BLOB:
        case Value.CLOB: {
            buff.put((byte) type);
            ValueLobDb lob = (ValueLobDb) v;
            byte[] small = lob.getSmall();
            if (small == null) {
                buff.putVarInt(-3).putVarInt(lob.getTableId()).putVarLong(lob.getLobId())
                        .putVarLong(lob.getPrecision());
            } else {
                buff.putVarInt(small.length).put(small);
            }
            break;
        }
        case Value.ARRAY: {
            Value[] list = ((ValueArray) v).getList();
            buff.put((byte) type).putVarInt(list.length);
            for (Value x : list) {
                writeValue(buff, x);
            }
            break;
        }
        case Value.RESULT_SET: {
            buff.put((byte) type);
            try {
                ResultSet rs = ((ValueResultSet) v).getResultSet();
                rs.beforeFirst();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                buff.putVarInt(columnCount);
                for (int i = 0; i < columnCount; i++) {
                    writeString(buff, meta.getColumnName(i + 1));
                    buff.putVarInt(meta.getColumnType(i + 1)).putVarInt(meta.getPrecision(i + 1))
                            .putVarInt(meta.getScale(i + 1));
                }
                while (rs.next()) {
                    buff.put((byte) 1);
                    for (int i = 0; i < columnCount; i++) {
                        int t = org.lealone.value.DataType.getValueTypeFromResultSet(meta, i + 1);
                        Value val = org.lealone.value.DataType.readValue(null, rs, i + 1, t);
                        writeValue(buff, val);
                    }
                }
                buff.put((byte) 0);
                rs.beforeFirst();
            } catch (SQLException e) {
                throw DbException.convert(e);
            }
            break;
        }
        //        case Value.GEOMETRY: {
        //            byte[] b = v.getBytes();
        //            int len = b.length;
        //            buff.put((byte) type).putVarInt(len).put(b);
        //            break;
        //        }
        default:
            DbException.throwInternalError("type=" + v.getType());
        }
    }

    private static void writeString(WriteBuffer buff, String s) {
        int len = s.length();
        buff.putVarInt(len).putStringData(s, len);
    }

    /**
     * Read a value.
     *
     * @return the value
     */
    private Object readValue(ByteBuffer buff) {
        int type = buff.get() & 255;
        switch (type) {
        case Value.NULL:
            return ValueNull.INSTANCE;
        case BOOLEAN_TRUE:
            return ValueBoolean.get(true);
        case BOOLEAN_FALSE:
            return ValueBoolean.get(false);
        case INT_NEG:
            return ValueInt.get(-readVarInt(buff));
        case Value.INT:
            return ValueInt.get(readVarInt(buff));
        case LONG_NEG:
            return ValueLong.get(-readVarLong(buff));
        case Value.LONG:
            return ValueLong.get(readVarLong(buff));
        case Value.BYTE:
            return ValueByte.get(buff.get());
        case Value.SHORT:
            return ValueShort.get(buff.getShort());
        case DECIMAL_0_1:
            return ValueDecimal.ZERO;
        case DECIMAL_0_1 + 1:
            return ValueDecimal.ONE;
        case DECIMAL_SMALL_0:
            return ValueDecimal.get(BigDecimal.valueOf(readVarLong(buff)));
        case DECIMAL_SMALL: {
            int scale = readVarInt(buff);
            return ValueDecimal.get(BigDecimal.valueOf(readVarLong(buff), scale));
        }
        case Value.DECIMAL: {
            int scale = readVarInt(buff);
            int len = readVarInt(buff);
            byte[] buff2 = DataUtils.newBytes(len);
            buff.get(buff2, 0, len);
            BigInteger b = new BigInteger(buff2);
            return ValueDecimal.get(new BigDecimal(b, scale));
        }
        case Value.DATE: {
            return ValueDate.fromDateValue(readVarLong(buff));
        }
        case Value.TIME: {
            long nanos = readVarLong(buff) * 1000000 + readVarLong(buff);
            return ValueTime.fromNanos(nanos);
        }
        case Value.TIMESTAMP: {
            long dateValue = readVarLong(buff);
            long nanos = readVarLong(buff) * 1000000 + readVarLong(buff);
            return ValueTimestamp.fromDateValueAndNanos(dateValue, nanos);
        }
        case Value.BYTES: {
            int len = readVarInt(buff);
            byte[] b = DataUtils.newBytes(len);
            buff.get(b, 0, len);
            return ValueBytes.getNoCopy(b);
        }
        case Value.JAVA_OBJECT: {
            int len = readVarInt(buff);
            byte[] b = DataUtils.newBytes(len);
            buff.get(b, 0, len);
            //return ValueJavaObject.getNoCopy(null, b, handler);
            return ValueJavaObject.getNoCopy(null, b);
        }
        case Value.UUID:
            return ValueUuid.get(buff.getLong(), buff.getLong());
        case Value.STRING:
            return ValueString.get(readString(buff));
        case Value.STRING_IGNORECASE:
            return ValueStringIgnoreCase.get(readString(buff));
        case Value.STRING_FIXED:
            return ValueStringFixed.get(readString(buff));
        case FLOAT_0_1:
            return ValueFloat.get(0);
        case FLOAT_0_1 + 1:
            return ValueFloat.get(1);
        case DOUBLE_0_1:
            return ValueDouble.get(0);
        case DOUBLE_0_1 + 1:
            return ValueDouble.get(1);
        case Value.DOUBLE:
            return ValueDouble.get(Double.longBitsToDouble(Long.reverse(readVarLong(buff))));
        case Value.FLOAT:
            return ValueFloat.get(Float.intBitsToFloat(Integer.reverse(readVarInt(buff))));
        case Value.BLOB:
        case Value.CLOB: {
            int smallLen = readVarInt(buff);
            if (smallLen >= 0) {
                byte[] small = DataUtils.newBytes(smallLen);
                buff.get(small, 0, smallLen);
                return ValueLobDb.createSmallLob(type, small);
            } else if (smallLen == -3) {
                int tableId = readVarInt(buff);
                long lobId = readVarLong(buff);
                long precision = readVarLong(buff);
                //ValueLobDb lob = ValueLobDb.create(type, handler, tableId, lobId, null, precision);
                ValueLobDb lob = ValueLobDb.create(type, null, tableId, lobId, null, precision);
                return lob;
            } else {
                throw DbException.get(ErrorCode.FILE_CORRUPTED_1, "lob type: " + smallLen);
            }
        }
        case Value.ARRAY: {
            int len = readVarInt(buff);
            Value[] list = new Value[len];
            for (int i = 0; i < len; i++) {
                list[i] = (Value) readValue(buff);
            }
            return ValueArray.get(list);
        }
        case Value.RESULT_SET: {
            SimpleResultSet rs = new SimpleResultSet();
            rs.setAutoClose(false);
            int columns = readVarInt(buff);
            for (int i = 0; i < columns; i++) {
                rs.addColumn(readString(buff), readVarInt(buff), readVarInt(buff), readVarInt(buff));
            }
            while (true) {
                if (buff.get() == 0) {
                    break;
                }
                Object[] o = new Object[columns];
                for (int i = 0; i < columns; i++) {
                    o[i] = ((Value) readValue(buff)).getObject();
                }
                rs.addRow(o);
            }
            return ValueResultSet.get(rs);
        }
        default:
            if (type >= INT_0_15 && type < INT_0_15 + 16) {
                return ValueInt.get(type - INT_0_15);
            } else if (type >= LONG_0_7 && type < LONG_0_7 + 8) {
                return ValueLong.get(type - LONG_0_7);
            } else if (type >= BYTES_0_31 && type < BYTES_0_31 + 32) {
                int len = type - BYTES_0_31;
                byte[] b = DataUtils.newBytes(len);
                buff.get(b, 0, len);
                return ValueBytes.getNoCopy(b);
            } else if (type >= STRING_0_31 && type < STRING_0_31 + 32) {
                return ValueString.get(readString(buff, type - STRING_0_31));
            }
            throw DbException.get(ErrorCode.FILE_CORRUPTED_1, "type: " + type);
        }
    }

    private static int readVarInt(ByteBuffer buff) {
        return DataUtils.readVarInt(buff);
    }

    private static long readVarLong(ByteBuffer buff) {
        return DataUtils.readVarLong(buff);
    }

    private static String readString(ByteBuffer buff, int len) {
        return DataUtils.readString(buff, len);
    }

    private static String readString(ByteBuffer buff) {
        int len = readVarInt(buff);
        return DataUtils.readString(buff, len);
    }

    @Override
    public int hashCode() {
        return compareMode.hashCode() ^ Arrays.hashCode(sortTypes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ValueDataType)) {
            return false;
        }
        ValueDataType v = (ValueDataType) obj;
        if (!compareMode.equals(v.compareMode)) {
            return false;
        }
        return Arrays.equals(sortTypes, v.sortTypes);
    }

}
