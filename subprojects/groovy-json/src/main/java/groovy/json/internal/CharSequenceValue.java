/*
 * Copyright 2003-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Derived from Boon all rights granted to Groovy project for this fork.
 */
package groovy.json.internal;

import groovy.json.JsonException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;

import static groovy.json.internal.CharScanner.*;
import static groovy.json.internal.Exceptions.die;

/**
 * @author Rick Hightower
 */
public class CharSequenceValue implements Value, CharSequence {

    private final Type type;
    private final boolean checkDate;
    private final boolean decodeStrings;

    private char[] buffer;
    private boolean chopped;
    private int startIndex;
    private int endIndex;
    private Object value;

    public CharSequenceValue( boolean chop, Type type, int startIndex, int endIndex, char[] buffer,
                              boolean encoded, boolean checkDate ) {
        this.type = type;
        this.checkDate = checkDate;
        this.decodeStrings = encoded;

        if ( chop ) {
            try {
                this.buffer = Arrays.copyOfRange( buffer, startIndex, endIndex );
            } catch ( Exception ex ) {
                Exceptions.handle( ex );
            }
            this.startIndex = 0;
            this.endIndex = this.buffer.length;
            this.chopped = true;

        } else {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.buffer = buffer;
        }
    }

    public String toString() {
        if ( startIndex == 0 && endIndex == buffer.length ) {
            return FastStringUtils.noCopyStringFromChars( buffer );
        } else {
            return new String( buffer, startIndex, ( endIndex - startIndex ) );
        }
    }

    @Override
    public final Object toValue() {
        return value != null ? value : ( value = doToValue() );
    }

    @Override
    public <T extends Enum> T toEnum( Class<T> cls ) {

        switch ( type ) {
            case STRING:
                return toEnum( cls, stringValue() );
            case INTEGER:
                return toEnum( cls, intValue() );
            case NULL:
                return null;
        }
        die( "toEnum " + cls + " value was " + stringValue() );
        return null;
    }

    public static <T extends Enum> T toEnum( Class<T> cls, String value ) {
        try {
            return ( T ) Enum.valueOf( cls, value );
        } catch ( Exception ex ) {
            return ( T ) Enum.valueOf( cls, value.toUpperCase().replace( '-', '_' ) );
        }
    }

    public static <T extends Enum> T toEnum( Class<T> cls, int value ) {

        T[] enumConstants = cls.getEnumConstants();
        for ( T e : enumConstants ) {
            if ( e.ordinal() == value ) {
                return e;
            }
        }
        die( "Can't convert ordinal value " + value + " into enum of type " + cls );
        return null;
    }


    @Override
    public boolean isContainer() {
        return false;
    }

    private final Object doToValue() {

        switch ( type ) {
            case DOUBLE:
                return doubleValue();
            case INTEGER:

                int sign = 1;
                boolean negative = false;
                if ( buffer[ startIndex ] == '-' ) {
                    startIndex++;
                    sign = -1;
                    negative = true;

                }


                if ( isInteger( buffer, startIndex, endIndex - startIndex, negative ) ) {
                    return intValue() * sign;
                } else {
                    return longValue() * sign;
                }
            case STRING:
                if ( checkDate ) {
                    Date date = null;
                    if ( Dates.isISO8601QuickCheck( buffer, startIndex, endIndex ) ) {
                        if ( Dates.isJsonDate( buffer, startIndex, endIndex ) ) {
                            date = Dates.fromJsonDate( buffer, startIndex, endIndex );
                        } else if ( Dates.isISO8601( buffer, startIndex, endIndex ) ) {
                            date = Dates.fromISO8601( buffer, startIndex, endIndex );
                        } else {
                            return stringValue();
                        }

                        if ( date == null ) {
                            return stringValue();
                        } else {
                            return date;
                        }
                    }
                }
                return stringValue();
        }
        die();
        return null;
    }

    @Override
    public boolean equals( Object o ) {
        if ( this == o ) return true;
        if ( !( o instanceof Value ) ) return false;

        CharSequenceValue value1 = ( CharSequenceValue ) o;

        if ( endIndex != value1.endIndex ) return false;
        if ( startIndex != value1.startIndex ) return false;
        if ( !Arrays.equals( buffer, value1.buffer ) ) return false;
        if ( type != value1.type ) return false;
        if ( value != null ? !value.equals( value1.value ) : value1.value != null ) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + ( buffer != null ? Arrays.hashCode( buffer ) : 0 );
        result = 31 * result + startIndex;
        result = 31 * result + endIndex;
        result = 31 * result + ( value != null ? value.hashCode() : 0 );
        return result;
    }


    @Override
    public final int length() {
        return buffer.length;
    }

    @Override
    public final char charAt( int index ) {
        return buffer[ index ];
    }

    @Override
    public final CharSequence subSequence( int start, int end ) {
        return new CharSequenceValue( false, type, start, end, buffer, decodeStrings, checkDate );
    }

    public BigDecimal bigDecimalValue() {
        return new BigDecimal( buffer, startIndex, endIndex - startIndex );
    }

    @Override
    public BigInteger bigIntegerValue() {
        return new BigInteger( toString() );
    }

    public String stringValue() {
        if ( this.decodeStrings ) {
            return JsonStringDecoder.decodeForSure( buffer, startIndex, endIndex );
        } else {
            return toString();
        }
    }

    @Override
    public String stringValueEncoded() {
        return JsonStringDecoder.decode( buffer, startIndex, endIndex );
    }

    @Override
    public Date dateValue() {


        if ( type == Type.STRING ) {

            if ( Dates.isISO8601QuickCheck( buffer, startIndex, endIndex ) ) {

                if ( Dates.isJsonDate( buffer, startIndex, endIndex ) ) {
                    return Dates.fromJsonDate( buffer, startIndex, endIndex );

                } else if ( Dates.isISO8601( buffer, startIndex, endIndex ) ) {
                    return Dates.fromISO8601( buffer, startIndex, endIndex );
                } else {
                    throw new JsonException( "Unable to convert " + stringValue() + " to date " );
                }
            } else {

                throw new JsonException( "Unable to convert " + stringValue() + " to date " );
            }
        } else {

            return new Date( Dates.utc( longValue() ) );
        }
    }

    @Override
    public int intValue() {
        int sign = 1;
        if ( buffer[ startIndex ] == '-' ) {
            startIndex++;
            sign = -1;

        }
        return parseInt( buffer, startIndex, endIndex - startIndex ) * sign;
    }

    @Override
    public long longValue() {
        long sign = 1;
        if ( buffer[ startIndex ] == '-' ) {
            startIndex++;
            sign = -1;

        }
        if ( isInteger( buffer, startIndex, endIndex - startIndex, sign < 0 ) ) {
            return parseInt( buffer, startIndex, endIndex - startIndex ) * sign;
        } else {
            return parseLong( buffer, startIndex, endIndex - startIndex ) * sign;
        }
    }

    public byte byteValue() {
        return ( byte ) intValue();
    }

    public short shortValue() {
        return ( short ) intValue();
    }

    private static float fpowersOf10[] = {
            1.0f,
            10.0f,
            100.0f,
            1000.0f,
            10000.0f,
            100000.0f,
            1000000.0f,
            10000000.0f,
            100000000.0f,
            1000000000.0f,
    };

    @Override
    public double doubleValue() {
        return CharScanner.doubleValue( this.buffer, startIndex, endIndex );
    }

    @Override
    public boolean booleanValue() {
        return Boolean.parseBoolean( toString() );
    }

    @Override
    public float floatValue() {

        boolean simple = true;
        int digitsPastPoint = 0;
        boolean foundPoint = false;

        float sign;

        if ( buffer[ startIndex ] == '-' ) {
            startIndex++;
            sign = -1.0f;
        } else {
            sign = 1.0f;
        }

        int length = endIndex - startIndex;
        if ( length > 10 ) {
            return Float.parseFloat( toString() ) * sign;
        }
        loop:
        for ( int index = startIndex; index < endIndex; index++ ) {
            char ch = buffer[ index ];
            switch ( ch ) {
                case 'e':
                    simple = false;
                    break loop;
                case 'E':
                    simple = false;
                    break loop;
                case 'F':
                    simple = false;
                    break loop;
                case 'f':
                    simple = false;
                    break loop;
                case '.':
                    foundPoint = true;
                    continue loop;
            }
            if ( foundPoint ) {
                digitsPastPoint++;
                if ( digitsPastPoint >= fpowersOf10.length ) {
                    simple = true;
                    break;
                }
            }
        }

        if ( simple ) {
            int value;

            value = parseIntIgnoreDot( buffer, startIndex, length );
            if ( digitsPastPoint < fpowersOf10.length ) {
                float power = fpowersOf10[ digitsPastPoint ] * sign;
                return value / power;

            }
        }

        return Float.parseFloat( toString() ) * sign;
    }

    public final void chop() {
        if ( !chopped ) {
            this.chopped = true;
            this.buffer = Arrays.copyOfRange( buffer, startIndex, endIndex );
            this.startIndex = 0;
            this.endIndex = this.buffer.length;
        }
    }

    @Override
    public char charValue() {
        return buffer[ startIndex ];
    }
}

