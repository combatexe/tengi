/*
 * Copyright (c) 2015-2016, Christoph Engelbert (aka noctarius) and
 * contributors. All rights reserved.
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
package com.noctarius.tengi.spi.pooling.impl;

import com.noctarius.tengi.core.impl.MathUtil;
import com.noctarius.tengi.core.impl.UnsafeUtil;
import com.noctarius.tengi.spi.logging.Logger;
import com.noctarius.tengi.spi.logging.LoggerManager;
import com.noctarius.tengi.spi.pooling.ObjectHandler;
import com.noctarius.tengi.spi.pooling.ObjectPool;
import com.noctarius.tengi.spi.pooling.ObjectValidator;
import com.noctarius.tengi.spi.pooling.PooledObject;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

public class NonBlockingObjectPool<T>
        implements ObjectPool<T> {

    private static final Logger LOGGER = LoggerManager.getLogger(NonBlockingObjectPool.class);

    private static final long ARRAY_BASE = UnsafeUtil.OBJECT_ARRAY_BASE;
    private static final long ARRAY_SHIFT = UnsafeUtil.OBJECT_ARRAY_SHIFT;
    private static final Unsafe UNSAFE = UnsafeUtil.UNSAFE;

    private static final long OFFSET;

    private static final int ENTRY_FREE = 0;
    private static final int ENTRY_USED = 1;
    private static final int ENTRY_UNPOOLED = 2;

    static {
        try {
            Field field = NonBlockingObjectPool.class.getDeclaredField("nextAcquireIndex");
            field.setAccessible(true);
            OFFSET = UNSAFE.objectFieldOffset(field);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final ThreadLocal<Entry<T>> threadCache = new ThreadLocal<>();

    private final ObjectHandler<T> handler;
    private final ObjectValidator<T> validator;

    private final Object[] entryPool;
    private final int size;

    private volatile int nextAcquireIndex = 0;

    public NonBlockingObjectPool(ObjectHandler<T> handler, ObjectValidator<T> validator, int size) {
        this.handler = handler;
        this.validator = validator;
        this.size = MathUtil.nextPowerOfTwo(size);
        this.entryPool = createEntryPool(handler, this.size);
    }

    @Override
    public PooledObject<T> acquire(Consumer<T> activator) {
        int nextAcquireIndex = this.nextAcquireIndex;

        // ObjectPool closed?
        if (nextAcquireIndex == -1) {
            throw new IllegalStateException("ObjectPool already closed");
        }

        // Maybe thread recently released an entry
        Entry<T> cachedEntry = threadCache.get();
        if (cachedEntry != null) {
            LOGGER.trace("Cached entry found: %s", cachedEntry);

            // If unused, just return the acquired and cached entry
            if (cachedEntry.casState(ENTRY_FREE, ENTRY_USED)) {
                LOGGER.trace("Cached entry was free, acquired: %s", cachedEntry);
                return validateOrRecreateEntry(cachedEntry.index, cachedEntry, activator);
            }
            threadCache.remove();
        }

        // Otherwise let's search a free entry
        int acquireIndex = nextAcquireIndex;
        do {
            Entry<T> entry = (Entry<T>) entryPool[acquireIndex++];
            // ObjectPool closed?
            if (entry == null) {
                throw new IllegalStateException("ObjectPool already closed");
            }

            if (acquireIndex >= size) {
                acquireIndex = 0;
            }

            if (entry.casState(ENTRY_FREE, ENTRY_USED)) {
                LOGGER.trace("Acquired entry from looping, index %s: %s", (acquireIndex - 1), entry);

                updateNextAcquireIndex(nextAcquireIndex, acquireIndex);

                // Validate object and recreate invalid entries
                return validateOrRecreateEntry(acquireIndex - 1, entry, activator);
            }
        } while (acquireIndex != nextAcquireIndex);

        // If pool is full create an intermediate object
        Entry<T> entry = new Entry<>(-1, handler.create(), ENTRY_UNPOOLED).activate(handler, activator);
        LOGGER.trace("Return intermediate entry: %s", entry);
        return entry;
    }

    @Override
    public void release(PooledObject<T> object, Consumer<T> passivator) {
        if (object == null || !(object instanceof Entry)) {
            throw new IllegalArgumentException("Illegal pooled object passed");
        }

        Entry<T> entry = (Entry<T>) object;
        int state = entry.state;

        LOGGER.trace("Returning entry to pool: %s", entry);
        if (state == ENTRY_UNPOOLED) {
            // Intermediate entry, will be discarded by the GC
            LOGGER.trace("Entry is intermediate, let GC handle it: %s", entry);
            entry.passivate(handler, passivator).destroy(handler);
            return;
        } else if (state == ENTRY_FREE) {
            throw new ConcurrentModificationException("Illegal concurrent update on an object pool entry");
        }

        entry.passivate(handler, passivator);
        if (!entry.casState(state, ENTRY_FREE)) {
            throw new ConcurrentModificationException("Illegal concurrent update on an object pool entry");
        }

        LOGGER.trace("Entry returned to the pool, setting as cached entry: %s", entry);
        threadCache.set(entry);
    }

    @Override
    public void close() {
        nextAcquireIndex = -1;
        for (int index = 0; index < size; index++) {
            long indexOffset = offset(index);

            // Retrieve element and destroy it
            Entry<T> entry = (Entry<T>) UNSAFE.getObjectVolatile(entryPool, indexOffset);
            entry.destroy(handler);

            // Clear field
            UNSAFE.putObjectVolatile(entryPool, indexOffset, null);
        }
    }

    private Entry<T>[] createEntryPool(ObjectHandler<T> factory, int size) {
        Entry<T>[] entryPool = new Entry[size];
        for (int i = 0; i < size; i++) {
            entryPool[i] = new Entry<>(i, factory.create());
        }
        return entryPool;
    }

    private void updateNextAcquireIndex(int expected, int newIndex) {
        while (true) {
            // Try updating the index
            if (UNSAFE.compareAndSwapInt(this, OFFSET, expected, newIndex)) {
                return;
            }

            // If failed let's see if the currently set pointer is bigger than
            // our index, if not retry updating
            expected = this.nextAcquireIndex;
            if (expected >= newIndex) {
                return;
            }
        }
    }

    private Entry<T> validateOrRecreateEntry(int index, Entry<T> entry, Consumer<T> activator) {
        if (validator == null) {
            return entry.activate(handler, activator);
        }

        if (validator.isValid(entry.getObject())) {
            return entry.activate(handler, activator);
        }

        // If not valid destroy old element and create a new one
        entry.destroy(handler);

        Entry<T> newEntry = new Entry<>(index, handler.create(), ENTRY_USED);

        if (index != -1) {
            long indexOffset = offset(index);
            if (!UNSAFE.compareAndSwapObject(entryPool, indexOffset, entry, newEntry)) {
                throw new ConcurrentModificationException("Illegal concurrent update on an invalid object pool entry");
            }
        }
        return newEntry.activate(handler, activator);
    }

    private long offset(long index) {
        return (index << ARRAY_SHIFT) + ARRAY_BASE;
    }

    static final class Entry<T>
            implements PooledObject<T> {

        private static final long OFFSET;

        static {
            try {
                Field field = Entry.class.getDeclaredField("state");
                field.setAccessible(true);
                OFFSET = UNSAFE.objectFieldOffset(field);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private final T value;
        private final int index;

        private volatile int state;

        public Entry(int index, T value) {
            this(index, value, ENTRY_FREE);
        }

        public Entry(int index, T value, int state) {
            this.index = index;
            this.value = value;
            this.state = state;
        }

        @Override
        public T getObject() {
            return value;
        }

        private Entry<T> activate(ObjectHandler<T> handler, Consumer<T> activator) {
            if (activator != null) {
                activator.accept(value);
            }
            handler.activateObject(value);
            return this;
        }

        private Entry<T> passivate(ObjectHandler<T> handler, Consumer<T> passivator) {
            if (passivator != null) {
                passivator.accept(value);
            }
            handler.passivateObject(value);
            return this;
        }

        private void destroy(ObjectHandler<T> handler) {
            handler.destroy(value);
        }

        private boolean casState(int expectedState, int newState) {
            return UNSAFE.compareAndSwapInt(this, OFFSET, expectedState, newState);
        }

        @Override
        public String toString() {
            return "Entry{" + "value=" + value + ", state=" +
                    (state == 0 ? "FREE" : (state == 1 ? "USED" : "INTERMEDIATE")) + '}';
        }
    }

}
