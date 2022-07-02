/*
  Copyright 2022 Luca Boasso. All rights reserved.
  Use of this source code is governed by a MIT
  license that can be found in the LICENSE file.
*/

import junit.framework.TestCase;
import leon.core.LeonPacker;
import leon.core.LeonUnpacker;
import leon.core.Tags;
import leon.core.ToLeon;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoreTest extends TestCase {

  public void testBasic() throws IOException {
    String out_bin = "out/testBasic.out";
    Map<Object, Object> map = new HashMap<>();
    map.put(0xCAFEBABEL, "class file magic number");
    LeonPacker pkr = new LeonPacker(out_bin);
    pkr.packString("LEON is simple").packList(Arrays.asList(true, 1.0, 945L));
    pkr.packMap(map);
    pkr.close();

    LeonUnpacker unpkr = new LeonUnpacker(out_bin);
    assertEquals("LEON is simple", unpkr.unpackString());
    assertEquals(Arrays.asList(true, 1.0, 945L), unpkr.unpackList());
    assertEquals(map, unpkr.unpackMap());
    unpkr.close();
    deleteFile(out_bin);
  }

  public void testPackingUnpacking() throws IOException {
    int MIN = -70;
    int MAX = 70;

    String out_bin = "out/testPackUnpack.out";
    LeonPacker pkr = new LeonPacker(out_bin);
    String long_str = "Hello loooooooooooooooooooooooong!";
    MyObj myobj = new MyObj();
    byte[] blob;
    for(int i = MIN; i < MAX; i++) {
      pkr.packInt(i);
    }
    pkr.packInt(-741).packObject(myobj).packString("Hello!");
    pkr.packString(long_str).packBoolean(true).packNull();
    ArrayList<Object> sub_list = new ArrayList<>(Arrays.asList(false, 78L));
    blob = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
    ArrayList<Object> list = new ArrayList<>(Arrays.asList(3, true, null, "abc", blob, long_str, sub_list));
    ArrayList<Object> long_list = new ArrayList<>();
    for(int i = 0; i < 52; i++) {
      long_list.add(true);
    }
    pkr.packList(list).packList(long_list);
    HashMap<Object, Object> map = new HashMap<>();
    map.put("One", 1L);
    map.put(42L, sub_list);
    map.put(true, 1.0f);
    map.put(false, 2.0);
    pkr.packMap(map);
    pkr.close();

    LeonUnpacker unpkr = new LeonUnpacker(out_bin);
    for(int i = MIN; i < MAX; i++) {
      long x = unpkr.unpackInt();
      assertEquals(x, i);
    }

    assertEquals(-741, unpkr.unpackInt());
    assertEquals(7, unpkr.unpackInt());
    assertEquals("Hello!", unpkr.unpackString());
    assertEquals(long_str, unpkr.unpackString());
    assertTrue(unpkr.unpackBoolean());
    assertNull(unpkr.unpackNull());
    List<Object> dec_list = unpkr.unpackList();
    assertEquals(list.size(), dec_list.size());
    assertEquals((long) (int) list.get(0), dec_list.get(0));
    assertEquals(list.get(1), dec_list.get(1));
    assertSame(list.get(2), dec_list.get(2));
    assertEquals(list.get(3), dec_list.get(3));

    byte[] dec_blob = (byte[]) dec_list.get(4);
    for(int i = 0; i < dec_blob.length; i++) {
      assertEquals(blob[i], dec_blob[i]);
    }
    assertEquals(list.get(5), dec_list.get(5));
    assertEquals(list.get(6), dec_list.get(6));
    List<Object> dec_long_list = unpkr.unpackList();
    assertEquals(dec_long_list, long_list);
    Map<Object, Object> dec_map = unpkr.unpackMap();
    assertEquals(map.size(), dec_map.size());
    for(Object key : dec_map.keySet()) {
      assertTrue(map.containsKey(key));
      assertEquals(map.get(key), dec_map.get(key));
    }

    unpkr.close();
    deleteFile(out_bin);
  }

  public void testRawApi() throws IOException {
    String out_bin = "out/testRawApi.out";
    LeonPacker pkr = new LeonPacker(out_bin);
    ArrayList<Object> list = new ArrayList<>(Arrays.asList(3L, true, null, 2.13));
    pkr = pkr.packListTag(list.size());
    for(Object o : list) {
      pkr.packObject(o);
    }
    pkr.packString("Hello");
    HashMap<Object, Object> map = new HashMap<>();
    map.put("One", 1L);
    map.put(true, 1.0f);
    map.put(42L, "123456789");
    map.put(false, 2.0);
    pkr.packMapTag(map.size());
    for(Object key : map.keySet()) {
      pkr.packObject(key);
      pkr.packObject(map.get(key));
    }
    ArrayList<Object> list2 = new ArrayList<>(Arrays.asList(false, 784599, null));
    pkr.packList(list2);
    HashMap<Object, Object> map2 = new HashMap<>();
    map2.put("Two", 2L);
    map2.put("Three", 3L);
    map2.put(4L, 4L);
    pkr.packMap(map2);
    pkr.packList(list2);
    pkr.packBytesTag(3);
    pkr.packRawBytes(new byte[]{1, 2});
    pkr.packRawBytes(new byte[]{3});
    pkr.packBytes(new byte[4096]);
    pkr.packString("END");
    pkr.close();

    LeonUnpacker unpkr = new LeonUnpacker(out_bin);
    int tag = unpkr.nextTag();
    assertTrue(Tags.isList(tag));
    List<Object> dec_list = unpkr.unpackListWithTag(tag);
    assertEquals(list, dec_list);
    tag = unpkr.nextTag();
    assertTrue(Tags.isString(tag));
    unpkr.skipObject(tag);
    tag = unpkr.nextTag();
    assertTrue(Tags.isMap(tag));
    unpkr.skipObject(tag);
    tag = unpkr.nextTag();
    long list2_length = unpkr.unpackListLengthWithTag(tag);
    tag = unpkr.nextTag();
    while(list2_length != 2) {
      unpkr.skipObject(tag);
      tag = unpkr.nextTag();
      list2_length--;
    }
    assertTrue(Tags.isInt(tag));
    assertEquals(784599, unpkr.unpackIntWithTag(tag));
    assertNull(unpkr.unpackNull());
    tag = unpkr.nextTag();
    assertTrue(Tags.isMap(tag));
    long map_size = unpkr.unpackMapSizeWithTag(tag);
    assertEquals(3, map_size);
    Map<Object, Object> decoded_map2 = new HashMap<>();
    for(int i = 0; i < map_size; i++) {
      Object key = unpkr.unpackObject();
      Object value = unpkr.unpackObject();
      decoded_map2.put(key, value);
    }
    assertEquals(map2, decoded_map2);
    tag = unpkr.nextTag();
    assertTrue(Tags.isList(tag));
    unpkr.skipObject(tag);
    tag = unpkr.nextTag();
    assertTrue(Tags.isBytes(tag));
    int bytes_size = (int) unpkr.unpackBytesSizeWithTag(tag);
    byte[] decoded_bytes = new byte[bytes_size];
    int tot = unpkr.unpackRawBytes(decoded_bytes);
    assertEquals(bytes_size, tot);
    assertTrue(Arrays.equals(new byte[]{1, 2, 3}, decoded_bytes));
    tag = unpkr.nextTag();
    assertTrue(Tags.isBytes(tag));
    unpkr.skipObject(tag);
    tag = unpkr.nextTag();
    assertTrue(Tags.isString(tag));
    assertEquals("END", unpkr.unpackStringWithTag(tag));
    deleteFile(out_bin);
  }

  private static void deleteFile(String path) {
    assertTrue(new File(path).delete());
  }

  static class MyObj implements ToLeon {
    private int a = 7;

    @Override
    public LeonPacker toLeon(LeonPacker packer) throws IOException {
      return packer.packInt(a);
    }
  }
}
