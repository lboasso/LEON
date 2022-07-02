/*
  Copyright 2022 Luca Boasso. All rights reserved.
  Use of this source code is governed by a MIT
  license that can be found in the LICENSE file.
*/

package leon.core;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static leon.core.LeonException.Reason.UnableToPackObj;
import static leon.core.LeonException.Reason.UnableToUnpackObj;


public class LeonUnpacker implements Closeable {
  private final InputStream in;

  public LeonUnpacker(InputStream in) {
    this.in = in;
  }

  public LeonUnpacker(String path) throws IOException {
    this(new BufferedInputStream(new FileInputStream(new File(path))));
  }


  private int readLE32Int() throws IOException {
    int x0, x1, x2, x3, x;
    x0 = in.read();
    x1 = in.read();
    x2 = in.read();
    x3 = in.read();
    if(x0 == -1 || x1 == -1 || x2 == -1 || x3 == -1) {
      throw new LeonException("Unexpected end of input reached", UnableToUnpackObj);
    }
    x = (((((x3 << 8) + x2) << 8) + x1) << 8) + x0;
    return x;
  }


  public boolean unpackBooleanWithTag(int tag) {
    if(!Tags.isBoolean(tag)) {
      throw new LeonException("Expecting a boolean in input stream", UnableToPackObj);
    }
    return tag == Tags.TRUE;
  }

  public boolean unpackBoolean() throws IOException {
    return unpackBooleanWithTag(in.read());
  }

  public Object unpackNullWithTag(int tag) {
    if(!Tags.isNull(tag)) {
      throw new LeonException("Expecting a NULL in input stream", UnableToPackObj);
    }
    return null;
  }

  public Object unpackNull() throws IOException {
    return unpackNullWithTag(in.read());
  }

  public float unpackFloatWithTag(int tag) throws IOException {
    if(!Tags.isFloat(tag)) {
      throw new LeonException("Expecting a float in input stream", UnableToPackObj);
    }
    int bits = readLE32Int();
    return Float.intBitsToFloat(bits);
  }

  public float unpackFloat() throws IOException {
    return unpackFloatWithTag(in.read());
  }

  public double unpackDoubleWithTag(int tag) throws IOException {
    if(!Tags.isDouble(tag)) {
      throw new LeonException("Expecting a double in input stream", UnableToPackObj);
    }
    long bits_low = readLE32Int();
    long bits_high = readLE32Int();
    long bits = (bits_high << 32) | bits_low;
    return Double.longBitsToDouble(bits);
  }

  public double unpackDouble() throws IOException {
    return unpackDoubleWithTag(in.read());
  }

  /*
     smallest unsigned integer that cannot be represented in 6 bits 01000000 = 0x40
     mask last payload with no sign 00011111 = 0x1F
     mask sign bit last payload     00100000 = 0x20
  */

  public long unpackIntWithTag(int tag) throws IOException {
    long n, y, b, x;
    boolean eof = tag == -1;
    if(!Tags.isInt(tag)) {
      throw new LeonException("Expecting an integer in input stream", UnableToUnpackObj);
    }
    n = 0;
    y = 0;
    b = tag;
    while(!eof && b >= 0x40) {
      y += ((b - 0x80) << n);
      n += 7;
      b = in.read();
      eof = b == -1;
    }
    if(eof) {
      throw new LeonException("Unexpected end of input reached", UnableToUnpackObj);
    }
    // sign extend the last 6 bits payload, where the 6th bit is the sign
    b = (b & 0x1F) - (b & 0x20);
    x = y + (b << n);
    return x;
  }

  public long unpackInt() throws IOException {
    return unpackIntWithTag(in.read());
  }


  public String unpackStringWithTag(int tag) throws IOException {
    String str;
    int size;
    byte[] str_utf8;

    if(Tags.isSmallString(tag)) {
      size = tag & Tags.MASK_SIZE_SMALL_STR;
    } else if(Tags.isBigString(tag)) {
      //TODO support long, array.length is a int
      size = (int) unpackInt();
    } else {
      throw new LeonException("Expecting a string in input stream", UnableToUnpackObj);
    }
    str_utf8 = new byte[size];
    int num_read = in.read(str_utf8);
    if(num_read != size) {
      throw new LeonException("Unexpected end of input reached", UnableToUnpackObj);
    }
    str = new String(str_utf8, StandardCharsets.UTF_8);
    return str;
  }

  public String unpackString() throws IOException {
    return unpackStringWithTag(in.read());
  }


  public long unpackListLengthWithTag(int tag) throws IOException {
    long length;

    if(Tags.isSmallList(tag)) {
      length = tag & Tags.MASK_LENGTH_SMALL_LIST;
    } else if(Tags.isBigList(tag)) {
      length = unpackInt();
    } else {
      throw new LeonException("Expecting a list in input stream", UnableToUnpackObj);
    }
    return length;
  }

  public List<Object> unpackListWithTag(int tag) throws IOException {
    ArrayList<Object> list = new ArrayList<>();
    int length;

    if(Tags.isSmallList(tag)) {
      length = tag & Tags.MASK_LENGTH_SMALL_LIST;
    } else if(Tags.isBigList(tag)) {
      //TODO support long: List.size() returns a int
      length = (int) unpackInt();
    } else {
      throw new LeonException("Expecting a list in input stream", UnableToUnpackObj);
    }
    for(int i = 0; i < length; i++) {
      list.add(unpackObject());
    }
    return list;
  }

  public List<Object> unpackList() throws IOException {
    return unpackListWithTag(in.read());
  }


  public long unpackBytesSizeWithTag(int tag) throws IOException {
    long size;

    if(Tags.isBytes(tag)) {
      size = unpackInt();
    } else {
      throw new LeonException("Expecting bytes in input stream", UnableToUnpackObj);
    }
    return size;
  }

  public int unpackRawBytes(byte[] bytes) throws IOException {
    return unpackRawBytes(bytes, 0, bytes.length);
  }

  public int unpackRawBytes(byte[] bytes, int off, int len) throws IOException {
    return in.read(bytes, off, len);
  }

  public byte[] unpackBytesWithTag(int tag) throws IOException {
    byte[] bytes;
    int size;

    if(Tags.isBytes(tag)) {
      //TODO support long, array.length is a int
      size = (int) unpackInt();
    } else {
      throw new LeonException("Expecting bytes in input stream", UnableToUnpackObj);
    }
    bytes = new byte[size];
    for(int i = 0; i < size; i++) {
      int b = in.read();
      if(b == -1) {
        throw new LeonException("Unexpected end of input reached", UnableToUnpackObj);
      }
      bytes[i] = (byte) b;
    }
    return bytes;
  }

  public byte[] unpackBytes() throws IOException {
    return unpackBytesWithTag(in.read());
  }

  public long unpackMapSizeWithTag(int tag) throws IOException {
    long size;

    if(Tags.isMap(tag)) {
      size = unpackInt();
    } else {
      throw new LeonException("Expecting a map in input stream", UnableToUnpackObj);
    }
    return size;
  }

  public Map<Object, Object> unpackMapWithTag(int tag) throws IOException {
    HashMap<Object, Object> map = new HashMap<>();
    int size;

    if(Tags.isMap(tag)) {
      //TODO support long, Map.size() returns a int
      size = (int) unpackInt();
    } else {
      throw new LeonException("Expecting a map in input stream", UnableToUnpackObj);
    }
    for(int i = 0; i < size; i++) {
      Object key = unpackObject();
      Object value = unpackObject();
      map.put(key, value);
    }
    return map;
  }

  public Map<Object, Object> unpackMap() throws IOException {
    return unpackMapWithTag(in.read());
  }

  public Object unpackObject() throws IOException {
    Object obj;
    int tag = in.read();
    if(Tags.isInt(tag)) {
      obj = unpackIntWithTag(tag);
    } else if(Tags.isFloat(tag)) {
      obj = unpackFloatWithTag(tag);
    } else if(Tags.isDouble(tag)) {
      obj = unpackDoubleWithTag(tag);
    } else if(Tags.isNull(tag)) {
      obj = null;
    } else if(tag == Tags.TRUE) {
      obj = true;
    } else if(tag == Tags.FALSE) {
      obj = false;
    } else if(Tags.isString(tag)) {
      obj = unpackStringWithTag(tag);
    } else if(Tags.isList(tag)) {
      obj = unpackListWithTag(tag);
    } else if(Tags.isMap(tag)) {
      obj = unpackMapWithTag(tag);
    } else if(Tags.isBytes(tag)) {
      obj = unpackBytesWithTag(tag);
    } else {
      throw new LeonException("Unable to unpack object", UnableToUnpackObj);
    }
    return obj;
  }

  public int nextTag() throws IOException {
    int tag = in.read();
    if(tag == -1) {
      tag = Tags.EOF;
    } else if(!Tags.isValidTag(tag)) {
      throw new LeonException("Unable to get a valid tag, input stream corrupted or not at a tag boundary", LeonException.Reason.InvalidTag);
    }
    return tag;
  }

  public void skipObject(int tag) throws IOException {
    if(tag == Tags.EOF) {
      return;
    }
    if(!Tags.isValidTag(tag)) {
      throw new LeonException("Invalid tag", LeonException.Reason.InvalidTag);
    }
    if(Tags.isInt(tag)) {
      int b;
      boolean eof = false;
      b = tag;
      while(!eof && b >= 0x80) {
        b = in.read();
        eof = b == -1;
      }
    } else if(Tags.isFloat(tag)) {
      readLE32Int();
    } else if(Tags.isDouble(tag)) {
      readLE32Int();
      readLE32Int();
    } else if(Tags.isNull(tag) || Tags.isBoolean(tag)) {
      // nothing to do
    } else if(Tags.isString(tag)) {
      long size;

      if(Tags.isSmallString(tag)) {
        size = tag & Tags.MASK_SIZE_SMALL_STR;
      } else {
        size = unpackInt();
      }
      while(size > 0) {
        in.read();
        size--;
      }
    } else if(Tags.isList(tag)) {
      long length;

      if(Tags.isSmallList(tag)) {
        length = tag & Tags.MASK_LENGTH_SMALL_LIST;
      } else {
        length = unpackInt();
      }
      while(length > 0) {
        skipObject(nextTag());
        length--;
      }
    } else if(Tags.isMap(tag)) {
      long size = unpackInt() * 2;
      while(size > 0) {
        skipObject(nextTag());
        size--;
      }
    } else if(Tags.isBytes(tag)) {
      long size = unpackInt();
      while(size > 0) {
        in.read();
        size--;
      }
    } else {
      throw new LeonException("Internal error valid tag not handled", LeonException.Reason.InternalError);
    }
  }

  @Override
  public void close() throws IOException {
    in.close();
  }
}