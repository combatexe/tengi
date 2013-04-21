package com.github.tengi.buffer;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnsafeByteBuf;

import java.nio.ByteOrder;

public class ByteBufMemoryBuffer
    extends AbstractMemoryBuffer
{

    private final ByteBuf buffer;

    public ByteBufMemoryBuffer( ByteBuf buffer )
    {
        this.buffer = buffer;
        this.buffer.order( ByteOrder.BIG_ENDIAN );
    }

    @Override
    protected void writeByte( long offset, byte value )
    {
        int position = buffer.writerIndex();
        buffer.writerIndex( (int) offset );
        buffer.writeByte( value );
        buffer.writerIndex( position );
    }

    @Override
    protected byte readByte( long offset )
    {
        int position = buffer.readerIndex();
        byte value = buffer.readByte();
        buffer.readerIndex( position );
        return value;
    }

    @Override
    public long capacity()
    {
        return buffer.capacity();
    }

    @Override
    public long maxCapacity()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean growing()
    {
        return true;
    }

    @Override
    public void free()
    {
        if ( buffer.isDirect() && buffer instanceof UnsafeByteBuf )
        {
            ( (UnsafeByteBuf) buffer ).free();
        }
    }

    @Override
    public void clear()
    {
        super.clear();
        buffer.clear();
    }

    @Override
    public MemoryBuffer duplicate()
    {
        return new ByteBufMemoryBuffer( buffer.duplicate() );
    }
}
