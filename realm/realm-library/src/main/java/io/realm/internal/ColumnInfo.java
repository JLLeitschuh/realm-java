/*
 * Copyright 2015 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.internal;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import io.realm.RealmFieldType;


/**
 * Objects of this class play two roles:
 * <ul>
 * <li>Subclasses are a fast cache of column indices, for proxy object</li>
 * <li>They cache table (schema) information used by for StandardRealmObjectSchema and StandardRealmSchema</li>
 * </ul>
 * The fast cache functionality is implemented in the Proxy classes generated by {@code RealmProxyClassGenerator}.
 * Be sure to understand what is going on there, before changing things here.
 * <p>
 * While the use of the fields in {@code ColumnDetails} is consistent, there are three subtly different cases:
 * <ul>
 * <li>If the column type is a simple type, the link table field is empty (0L / NULLPTR)</li>
 * <li>If the column type is OBJECT or LINK, the link table field is the class name of the OBJECT/LINK type</li>
 * <li>If the column type is LINKING_OBJECT, the link table field is the class name of the backlink source table
 * and the column index field is the index of the backlink source field, in the source table</li>
 * </ul>
 *
 * <p>
 * Some instances of this class must be thread-safe.  The class support effectively-final semantics.
 * An instance can be mutated, after construction, in four ways:
 * <ul>
 * <li>the {@code copyFrom} method</li>
 * <li>as the dst parameter of the two-argument copy method</li>
 * <li>using the {@code addColumnDetails} method</li>
 * <li>using the {@code addBacklinkDetails} method</li>
 * </ul>
 * Immutable instances of this class protect against the first possibility by throwing on calls
 * to {@code copyFrom}.  There are no checks against the other three mutations.  In order to comply
 * with the effectively-final contract:
 * <ul>
 * <li>the methods {@code addColumnDetails} and {@code addBacklinkDetails} must be called
 * only from within instance constructors</li>
 * <li>an immutable instance must never be the dst parameter of the two-argument copy method</li>
 * </ul>
 */
public abstract class ColumnInfo {

    // Immutable column information
    private static final class ColumnDetails {
        public final long columnIndex;
        public final RealmFieldType columnType;
        public final String linkTable;

        ColumnDetails(long columnIndex, RealmFieldType columnType, String srcTable) {
            this.columnIndex = columnIndex;
            this.columnType = columnType;
            this.linkTable = srcTable;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("ColumnDetails[");
            buf.append(columnIndex);
            buf.append(", ").append(columnType);
            buf.append(", ").append(linkTable);
            return buf.append("]").toString();
        }
    }


    private final Map<String, ColumnDetails> indicesMap;
    private final boolean mutable;

    /**
     * Create a new, empty instance
     *
     * @param mapSize the expected number of columns in the map.
     */
    protected ColumnInfo(int mapSize) {
        this(mapSize, true);
    }

    /**
     * Create an exact copy of the passed instance.
     *
     * @param src the instance to copy
     * @param mutable false to make this instance effectively final
     */
    protected ColumnInfo(@Nullable ColumnInfo src, boolean mutable) {
        this((src == null) ? 0 : src.indicesMap.size(), mutable);
        // ColumnDetails are immutable and may be re-used.
        if (src != null) {
            indicesMap.putAll(src.indicesMap);
        }
    }

    private ColumnInfo(int mapSize, boolean mutable) {
        this.indicesMap = new HashMap<>(mapSize);
        this.mutable = mutable;
    }

    /**
     * Get the mutability state of the instance.
     *
     * @return true if the instance is mutable
     */
    public final boolean isMutable() {
        return mutable;
    }

    /**
     * Returns the index, in the described table, for the named column.
     *
     * @return column index.
     */
    public long getColumnIndex(String columnName) {
        ColumnDetails details = indicesMap.get(columnName);
        return (details == null) ? -1 : details.columnIndex;
    }

    /**
     * Returns the Realm Type, in the described table, of the named column.
     *
     * @return column Realm Type.
     */
    public RealmFieldType getColumnType(String columnName) {
        ColumnDetails details = indicesMap.get(columnName);
        return (details == null) ? RealmFieldType.UNSUPPORTED_TABLE : details.columnType;
    }

    /**
     * Returns the table linked in the described table, to the named column.
     *
     * @return the class name of the linked table, or {@code null} if the column is a primitive type.
     */
    @Nullable
    public String getLinkedTable(String columnName) {
        ColumnDetails details = indicesMap.get(columnName);
        return (details == null) ? null : details.linkTable;
    }

    /**
     * Makes this ColumnInfo an exact copy of {@code src}.
     *
     * @param src The source for the copy.  This instance will be an exact copy of {@code src} after return.
     * {@code src} must not be {@code null}.
     * @throws IllegalArgumentException if {@code other} has different class than this.
     */
    public void copyFrom(ColumnInfo src) {
        if (!mutable) {
            throw new UnsupportedOperationException("Attempt to modify an immutable ColumnInfo");
        }
        if (null == src) {
            throw new NullPointerException("Attempt to copy null ColumnInfo");
        }

        indicesMap.clear();
        indicesMap.putAll(src.indicesMap);
        copy(src, this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("ColumnInfo[");
        buf.append(mutable).append(",");
        if (indicesMap != null) {
            boolean commaNeeded = false;
            for (Map.Entry<String, ColumnDetails> entry : indicesMap.entrySet()) {
                if (commaNeeded) { buf.append(","); }
                buf.append(entry.getKey()).append("->").append(entry.getValue());
                commaNeeded = true;
            }
        }
        return buf.append("]").toString();
    }

    /**
     * Create a new object that is an exact copy of {@code src}.
     * This is the generic factory for ColumnInfo objects.
     * Subclasses are expected to override it with a proxy to a copy constructor.
     *
     * @param mutable false to make an immutable copy.
     */
    protected abstract ColumnInfo copy(boolean mutable);

    /**
     * Make {@code dst} into an exact copy of {@code src}.
     * Intended for use only by subclasses.
     * NOTE: there is no protection against calling this method with an "immutable" instance as dst!
     *
     * @param src The source for the copy
     * @param dst The destination of the copy.  Will be an exact copy of src after return.
     */
    protected abstract void copy(ColumnInfo src, ColumnInfo dst);

    /**
     * Add a new column to the indexMap.
     * <p>
     * <b>For use only in subclass constructors!</b>.
     * Must be called from within the subclass constructor, to maintain the effectively-final contract.
     * <p>
     * No validation done here.  Presuming that all necessary validation takes place in {@code Proxy.validateTable}.
     *
     * @param table The table to search for the column.
     * @param columnName The name of the column whose index is sought.
     * @param columnType Type RealmType of the column.
     * @return the index of the column in the table
     */
    @SuppressWarnings("unused")
    protected final long addColumnDetails(Table table, String columnName, RealmFieldType columnType) {
        long columnIndex = table.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            String linkedTableName = ((columnType != RealmFieldType.OBJECT) && (columnType != RealmFieldType.LIST))
                    ? null
                    : table.getLinkTarget(columnIndex).getClassName();

            indicesMap.put(columnName, new ColumnDetails(columnIndex, columnType, linkedTableName));
        }

        return columnIndex;
    }

    /**
     * Add a new backlink to the indexMap.
     * <b>For use only by subclasses!</b>.
     * Must be called from within the subclass constructor, to maintain the effectively-final contract.
     *
     * @param realm The shared realm.
     * @param columnName The name of the backlink column.
     * @param sourceTableName The name of the backlink source class.
     * @param sourceColumnName The name of the backlink source field.
     */
    @SuppressWarnings("unused")
    protected final void addBacklinkDetails(SharedRealm realm, String columnName, String sourceTableName, String sourceColumnName) {
        long columnIndex = -1;
        try {
            columnIndex = realm.getTable(Table.getTableNameForClass(sourceTableName)).getColumnIndex(sourceColumnName);
        }
        catch (IllegalArgumentException ignore) {
            // Failed to find the source table:  This will be handled in Proxy.validateTable
        }
        indicesMap.put(columnName, new ColumnDetails(columnIndex, RealmFieldType.LINKING_OBJECTS, sourceTableName));
    }

    /**
     * Returns the {@link Map} that is the implementation for this object.
     * <b>FOR TESTING USE ONLY!</b>
     *
     * @return the column details map.
     */
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    //@VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public Map<String, ColumnDetails> getIndicesMap() {
        return indicesMap;
    }
}
