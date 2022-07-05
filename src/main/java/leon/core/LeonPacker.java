/*
  Copyright 2022 Luca Boasso. All rights reserved.
  Use of this source code is governed by a MIT
  license that can be found in the LICENSE file.
*/

package leon.core;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static leon.core.Tags.BYTES_TAG;
import static leon.core.Tags.DOUBLE_TAG;
import static leon.core.Tags.FALSE;
import static leon.core.Tags.FLOAT_TAG;
import static leon.core.Tags.LIST_TAG;
import static leon.core.Tags.MAP_TAG;
import static leon.core.Tags.MAX_BYTES_SMALL_STR;
import static leon.core.Tags.MAX_LENGTH_SMALL_LIST;
import static leon.core.Tags.NULL;
import static leon.core.Tags.SMALL_LIST_TAG;
import static leon.core.Tags.SMALL_STR_TAG;
import static leon.core.Tags.STR_TAG;
import static leon.core.Tags.TRUE;




/*
  LEON

  leon = object {object} .
  object = integer | null | true | false  | float | double | list | string | bytes | map .
  length = integer .
  size = integer .

  integer = {1XXXXXXX} 00XXXXXX .
  null = 01000000 .
  true = 01000001 .
  false = 01000010 .
  float = 01000011 "32 bits little endian IEEE 754 single precision floating point number" .
  double = 01000100 "64 bits little endian IEEE 754 double precision floating point number" .
  list = smallList | bigList .
  smallList = 0101XXXX {object} .
  bigList = 01000101 length {object} .
  string = smallString | bigString .
  smallString = 011XXXXX "UTF8 string of at most 31 bytes in size" .
  bigString = 01000110 size "UTF8 string of at most size bytes" .
  bytes = 01000111 size {XXXXXXXX} .
  map = 01001000 length {key value} .
  key = object .
  value = object .


  00 XXXXXX  integer -32 <= x < 32
  1X XXXXXX  variable integer payload
  01 000000  null
  01 000001  true
  01 000010  false
  01 000011  float - 32 bits little endian IEEE 754 single precision floating point number
  01 000100  double - 64 bits little endian IEEE 754 double precision floating point number
  01 000101  list - length - elements
  01 000110  string - size - UTF8 string bytes
  01 000111  bytes - size - bytes
  01 001000  map - num pairs - list of key value pairs
  01 001001  Reserved for future extensions
  01 001010  Reserved for future extensions
  01 001011  Reserved for future extensions
  01 001100  Reserved for future extensions
  01 001101  Reserved for future extensions
  01 001110  Reserved for future extensions
  01 001111  Reserved for future extensions
  01 01XXXX  list of at most 15 elements - elements
  01 1XXXXX  UTF8 string of at most 31 bytes in size - raw bytes

*/


public class LeonPacker implements Closeable, Flushable {
  private OutputStream out;

  public LeonPacker(OutputStream out) {
    this.out = out;
  }

  public LeonPacker(String path) throws FileNotFoundException {
    this.out = new BufferedOutputStream(new FileOutputStream(new File(path)));
  }

  private void writeLE32Int(int x) throws IOException {
    out.write((byte) (x & 0xFF));
    out.write((byte) ((x >> 8) & 0xFF));
    out.write((byte) ((x >> 16) & 0xFF));
    out.write((byte) ((x >> 24) & 0xFF));
  }

  public LeonPacker packBoolean(boolean x) throws IOException {
    if(x) {
      out.write((byte) TRUE);
    } else {
      out.write((byte) FALSE);
    }
    return this;
  }

  public LeonPacker packNull() throws IOException {
    out.write((byte) NULL);
    return this;
  }

  public LeonPacker packFloat(float x) throws IOException {
    out.write((byte) FLOAT_TAG);
    int bits = Float.floatToIntBits(x);
    writeLE32Int(bits);
    return this;
  }

  public LeonPacker packDouble(double x) throws IOException {
    out.write((byte) DOUBLE_TAG);
    long bits = Double.doubleToLongBits(x);
    writeLE32Int((int) bits);
    writeLE32Int((int) (bits >>> 32));
    return this;
  }


  /*
  With 6 bits I can store numbers in this range:
   -2^5 <= x <= 2^5-1
   -32 <= x <= 31
   -32 <= x < 32

   mask 7 bits payload 01111111 = 0x7F
   mask 6 bits payload 00111111 = 0x3F
   tag 7 bits payload  10000000 = 0x80
  */
  public LeonPacker packInt(long x) throws IOException {
    while(x < -32 || x >= 32) {
      out.write((byte) ((x & 0x7F) + 0x80));
      x = x >> 7;
    }
    out.write((byte) (x & 0x3F));
    return this;
  }

  public LeonPacker packString(String str) throws IOException {
    byte[] str_utf8 = str.getBytes(StandardCharsets.UTF_8);
    int length = str_utf8.length;
    if(length <= MAX_BYTES_SMALL_STR) {
      int header = SMALL_STR_TAG | length;
      out.write((byte) header);
    } else {
      out.write((byte) STR_TAG);
      packInt(length);
    }
    for(int i = 0; i < length; i++) {
      out.write(str_utf8[i]);
    }
    return this;
  }


  public LeonPacker packList(List<Object> list) throws IOException {
    int length = list.size();
    packListTag(length);
    for(int i = 0; i < length; i++) {
      packObject(list.get(i));
    }
    return this;
  }

  public LeonPacker packListTag(long length) throws IOException {
    if(length <= MAX_LENGTH_SMALL_LIST) {
      int header = SMALL_LIST_TAG | (int) length;
      out.write((byte) header);
    } else {
      out.write((byte) LIST_TAG);
      packInt(length);
    }
    return this;
  }

  public LeonPacker packBytes(byte[] bytes) throws IOException {
    int length = bytes.length;
    packBytesTag(length);
    out.write(bytes);
    return this;
  }

  public LeonPacker packRawBytes(byte[] bytes) throws IOException {
    out.write(bytes);
    return this;
  }

  public LeonPacker packBytesTag(long length) throws IOException {
    out.write((byte) BYTES_TAG);
    packInt(length);
    return this;
  }

  public LeonPacker packMap(Map<Object, Object> map) throws IOException {
    int size = map.size();
    packMapTag(size);
    for(Object key : map.keySet()) {
      packObject(key);
      packObject(map.get(key));
    }
    return this;
  }

  public LeonPacker packMapTag(long size) throws IOException {
    out.write((byte) MAP_TAG);
    packInt(size);
    return this;
  }

  public LeonPacker packObject(Object obj) throws IOException {
    if(obj instanceof Byte) {
      packInt((Byte) obj);
    } else if(obj instanceof Short) {
      packInt((Short) obj);
    } else if(obj instanceof Integer) {
      packInt((Integer) obj);
    } else if(obj instanceof Long) {
      packInt((Long) obj);
    } else if(obj instanceof Float) {
      packFloat((float) obj);
    } else if(obj instanceof Double) {
      packDouble((double) obj);
    } else if(obj == null) {
      packNull();
    } else if(obj instanceof Boolean) {
      packBoolean((boolean) obj);
    } else if(obj instanceof String) {
      packString((String) obj);
    } else if(obj instanceof List) {
      packList((List<Object>) obj);
    } else if(obj instanceof Map) {
      packMap((Map<Object, Object>) obj);
    } else if(obj instanceof byte[]) {
      packBytes((byte[]) obj);
    } else if(obj instanceof ToLeon) {
      ((ToLeon) obj).toLeon(this);
    } else {
      throw new LeonException("Unable to pack object, implement ToLeon interface", LeonException.Reason.UnableToPackObj);
    }
    return this;
  }

  @Override
  public void close() throws IOException {
    try {
      flush();
    } finally {
      out.close();
    }
  }

  @Override
  public void flush() throws IOException {
    out.flush();
  }
}
