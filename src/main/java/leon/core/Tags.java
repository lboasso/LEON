/*
  Copyright 2022 Luca Boasso. All rights reserved.
  Use of this source code is governed by a MIT
  license that can be found in the LICENSE file.
*/

package leon.core;

public final class Tags {
  private Tags() {
  }

  private static final int MASK_SMALL_INT = 0xC0;
  private static final int SMALL_INT_TAG = 0x00;
  private static final int MASK_INT = 0x80;
  private static final int INT_TAG = 0x80;

  public static final int EOF = -1;
  public static final int NULL = 0x40;
  public static final int TRUE = 0x41;
  public static final int FALSE = 0x42;
  public static final int FLOAT_TAG = 0x43;
  public static final int DOUBLE_TAG = 0x44;
  public static final int BYTES_TAG = 0x45;

  public static final int MAP_TAG = 0x48;
  public static final int MAX_SIZE_SMALL_MAP = 7;
  private static final int MASK_MAP_TAG = 0xF8;
  public static final int MASK_SIZE_SMALL_MAP = 0x7;

  public static final int LIST_TAG = 0x50;
  public static final int MAX_LENGTH_SMALL_LIST = 15;
  private static final int MASK_LIST_TAG = 0xF0;
  public static final int MASK_LENGTH_SMALL_LIST = 0xF;

  public static final int STR_TAG = 0x60;
  public static final int MAX_BYTES_SMALL_STR = 31;
  private static final int MASK_STR_TAG = 0xE0;
  public static final int MASK_SIZE_SMALL_STR = 0x1F;

  public static boolean isInt(int tag) {
    return (tag & MASK_SMALL_INT) == SMALL_INT_TAG || (tag & MASK_INT) == INT_TAG;
  }

  public static boolean isNull(int tag) {
    return tag == NULL;
  }

  public static boolean isBoolean(int tag) {
    return tag == TRUE || tag == FALSE;
  }

  public static boolean isFloat(int tag) {
    return tag == FLOAT_TAG;
  }

  public static boolean isDouble(int tag) {
    return tag == DOUBLE_TAG;
  }

  public static boolean isList(int tag) {
    return (tag & MASK_LIST_TAG) == LIST_TAG;
  }

  public static boolean isString(int tag) {
    return (tag & MASK_STR_TAG) == STR_TAG;
  }

  public static boolean isBytes(int tag) {
    return tag == BYTES_TAG;
  }

  public static boolean isMap(int tag) {
    return (tag & MASK_MAP_TAG) == MAP_TAG;
  }

  public static boolean isValidTag(int tag) {
    return isInt(tag) || isNull(tag) || isBoolean(tag) || isFloat(tag) || isDouble(tag) || isList(tag) || isString(tag) || isBytes(tag) || isMap(tag);

  }
}
