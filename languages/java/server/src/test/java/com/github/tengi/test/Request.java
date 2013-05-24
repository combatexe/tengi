package com.github.tengi.test;

import com.github.tengi.Protocol;
import com.github.tengi.Streamable;
import com.github.tengi.buffer.MemoryBuffer;

public class Request
    implements Streamable
{

    private Streamable data;

    @Override
    public void readStream( MemoryBuffer memoryBuffer, Protocol serializationFactory )
    {
        if ( memoryBuffer.readByte() == 1 )
        {
            short classId = memoryBuffer.readShort();
            data = serializationFactory.instantiate( classId );
            data.writeStream( memoryBuffer, serializationFactory );
        }
    }

    @Override
    public void writeStream( MemoryBuffer memoryBuffer, Protocol serializationFactory )
    {
        if ( data == null )
        {
            memoryBuffer.writeByte( (byte) 0 );
        }
        else
        {
            memoryBuffer.writeByte( (byte) 1 );
            memoryBuffer.writeShort( serializationFactory.getClassIdentifier( data ) );
            data.writeStream( memoryBuffer, serializationFactory );
        }
    }

}
