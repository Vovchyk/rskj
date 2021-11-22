/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.datasource;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;

public interface KeyValueDataSource extends DataSource {
    @Nullable
    byte[] get(byte[] key);

    /**
     * null puts() are NOT allowed.
     * @return the same value it received
     */
    byte[] put(byte[] key, byte[] value);

    void delete(byte[] key);

    Set<byte[]> keys();

    /**
     * Note that updateBatch() does not imply the operation is atomic:
     * if somethings breaks, it's possible that some keys get written and some
     * others don't.
     * IMPORTANT: keysToRemove override entriesToUpdate
     * @param entriesToUpdate
     * @param keysToRemove
     */
    void updateBatch(Map<ByteArrayWrapper, byte[]> entriesToUpdate, Set<ByteArrayWrapper> keysToRemove);

    /**
     * This makes things go to disk. To enable caching.
     */
    void flush();

    @Nonnull
    static KeyValueDataSource makeDataSource(@Nonnull Path datasourcePath, @Nonnull DbKind kind) {
        String name = datasourcePath.getFileName().toString();
        String databaseDir = datasourcePath.getParent().toString();

        KeyValueDataSource ds;
        switch (kind) {
            case LEVEL_DB:
                ds = new LevelDbDataSource(name, databaseDir);
                break;
            case ROCKS_DB:
                ds = new RocksDbDataSource(name, databaseDir);
                break;
            default:
                throw new IllegalArgumentException("kind");
        }

        ds.init();

        return ds;
    }

    static void mergeDataSources(@Nonnull Path destinationPath, @Nonnull List<Path> originPaths, @Nonnull DbKind kind) {
        Map<ByteArrayWrapper, byte[]> mergedStores = new HashMap<>();
        for (Path originPath : originPaths) {
            KeyValueDataSource singleOriginDataSource = makeDataSource(originPath, kind);
            for (byte[] key : singleOriginDataSource.keys()) {
                mergedStores.put(ByteUtil.wrap(key), singleOriginDataSource.get(key));
            }
            singleOriginDataSource.close();
        }
        KeyValueDataSource destinationDataSource = makeDataSource(destinationPath, kind);
        destinationDataSource.updateBatch(mergedStores, Collections.emptySet());
        destinationDataSource.close();
    }
}
