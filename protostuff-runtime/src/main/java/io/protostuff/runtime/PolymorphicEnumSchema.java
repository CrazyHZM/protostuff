//================================================================================
//Copyright (c) 2012, David Yu
//All rights reserved.
//--------------------------------------------------------------------------------
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
// 1. Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
// 3. Neither the name of protostuff nor the names of its contributors may be used
//    to endorse or promote products derived from this software without
//    specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.
//================================================================================

package io.protostuff.runtime;

import static io.protostuff.runtime.RuntimeFieldFactory.ID_ENUM;
import static io.protostuff.runtime.RuntimeFieldFactory.STR_ENUM;

import java.io.IOException;

import io.protostuff.GraphInput;
import io.protostuff.Input;
import io.protostuff.Output;
import io.protostuff.Pipe;
import io.protostuff.ProtostuffException;
import io.protostuff.Schema;

/**
 * Used when a field is declared as {@code Enum<?>} (with or with-out generics).
 * 
 * @author David Yu
 * @created Apr 25, 2012
 */
public abstract class PolymorphicEnumSchema extends PolymorphicSchema
{

    static final int ID_ENUM_VALUE = 1;
    static final String STR_ENUM_VALUE = "a";

    static String name(int number)
    {
        switch (number)
        {
            case ID_ENUM_VALUE:
                return STR_ENUM_VALUE;
            case ID_ENUM:
                return STR_ENUM;
            default:
                return null;
        }
    }

    static int number(String name)
    {
        if (name.length() != 1)
            return 0;

        switch (name.charAt(0))
        {
            case 'a':
                return ID_ENUM_VALUE;
            case 'x':
                return ID_ENUM;
            default:
                return 0;
        }
    }

    protected final Pipe.Schema<Object> pipeSchema = new Pipe.Schema<Object>(
            this)
    {
        @Override
        protected void transfer(Pipe pipe, Input input, Output output)
                throws IOException
        {
            transferObject(this, pipe, input, output, strategy);
        }
    };

    public PolymorphicEnumSchema(IdStrategy strategy)
    {
        super(strategy);
    }

    @Override
    public Pipe.Schema<Object> getPipeSchema()
    {
        return pipeSchema;
    }

    @Override
    public String getFieldName(int number)
    {
        return name(number);
    }

    @Override
    public int getFieldNumber(String name)
    {
        return number(name);
    }

    @Override
    public String messageFullName()
    {
        return Enum.class.getName();
    }

    @Override
    public String messageName()
    {
        return Enum.class.getSimpleName();
    }

    @Override
    public void mergeFrom(Input input, Object owner) throws IOException
    {
        setValue(readObjectFrom(input, this, owner, strategy), owner);
    }

    @Override
    public void writeTo(Output output, Object value) throws IOException
    {
        writeObjectTo(output, value, this, strategy);
    }

    static void writeObjectTo(Output output, Object value,
            Schema<?> currentSchema, IdStrategy strategy) throws IOException
    {
        final Class<?> clazz = value.getClass();
        if (clazz.getSuperclass() != null && clazz.getSuperclass().isEnum())
        {
            EnumIO<?> eio = strategy.getEnumIO(clazz.getSuperclass());
            strategy.writeEnumIdTo(output, ID_ENUM, clazz.getSuperclass());
            eio.writeTo(output, ID_ENUM_VALUE, false, (Enum<?>) value);
        }
        else
        {
            EnumIO<?> eio = strategy.getEnumIO(clazz);
            strategy.writeEnumIdTo(output, ID_ENUM, clazz);
            eio.writeTo(output, ID_ENUM_VALUE, false, (Enum<?>) value);
        }
    }

    static Object readObjectFrom(Input input, Schema<?> schema, Object owner,
            IdStrategy strategy) throws IOException
    {
        if (ID_ENUM != input.readFieldNumber(schema))
            throw new ProtostuffException("Corrupt input.");

        final EnumIO<?> eio = strategy.resolveEnumFrom(input);

        if (ID_ENUM_VALUE != input.readFieldNumber(schema))
            throw new ProtostuffException("Corrupt input.");

        final Object value = eio.readFrom(input);

        if (input instanceof GraphInput)
        {
            // update the actual reference.
            ((GraphInput) input).updateLast(value, owner);
        }

        if (0 != input.readFieldNumber(schema))
            throw new ProtostuffException("Corrupt input.");

        return value;
    }

    static void transferObject(Pipe.Schema<Object> pipeSchema, Pipe pipe,
            Input input, Output output, IdStrategy strategy) throws IOException
    {
        if (ID_ENUM != input.readFieldNumber(pipeSchema.wrappedSchema))
            throw new ProtostuffException("Corrupt input.");

        strategy.transferEnumId(input, output, ID_ENUM);

        if (ID_ENUM_VALUE != input.readFieldNumber(pipeSchema.wrappedSchema))
            throw new ProtostuffException("Corrupt input.");

        EnumIO.transfer(pipe, input, output, 1, false);

        if (0 != input.readFieldNumber(pipeSchema.wrappedSchema))
            throw new ProtostuffException("Corrupt input.");
    }

}