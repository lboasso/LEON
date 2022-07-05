![LEON](https://github.com/lboasso/LEON/blob/main/LEON.png "**L**ittle **E**ndian **O**bject **N**otation")

**L**ittle **E**ndian **O**bject **N**otation, is a portable binary
serialization format. LEON was designed to be easy to implement, fast and
compact on the wire.

Everything is stored in little endian. The encoding aims to minimize the size
of the most common type of values: integers, strings and lists.
The format allows for future backward compatible extensions.

This repository includes a reference implementation that focuses on correctness
rather than performance.

**Sample usage of the API**

```Java
LeonPacker pkr = new LeonPacker("my.out");
pkr.packString("LEON is simple").packList(Arrays.asList(true, 1.0, 945L));
Map<Object, Object> map = new HashMap<>();
map.put(0xCAFEBABEL, "class file magic number");
pkr.packMap(map);
pkr.close();

LeonUnpacker unpkr = new LeonUnpacker("my.out");
assertEquals("LEON is simple", unpkr.unpackString());
assertEquals(Arrays.asList(true, 1.0, 945L), unpkr.unpackList());
assertEquals(map, unpkr.unpackMap());
unpkr.close();
```

## Specification

Each value to be serialized has a type tag so that the deserialization process
knows how to interpret the raw bits. Integers require a tag of a few bits,
every other type requires one byte tag. Additional bytes are needed depending
on the type of value to be serialized.
An [EBNF](https://en.wikipedia.org/wiki/Wirth_syntax_notation) notation is used
to define the format. Bytes are written as a sequence of bits (`0`, `1` or `X`)
where a `X` denotes a bit of payload. Text in quotes `"` describes the value
it represents.

```
leon = object {object} .
object = integer | null | true | false  | float | double | list | string | bytes | map .
length = integer .
size = integer .
```

### Integer

Signed integers are stored in a variation of [signed LEB128](https://en.wikipedia.org/wiki/LEB128#Signed_LEB128).
If a number `x` has a value in the range [-32, 32), i.e `-32` <= `x` < `32`, it
is stored as one byte where the two most significant bits are set to `0` and the
remaining 6 bits store the actual value, in sign extended two's complement, as
payload. Otherwise the two's complement value of `x` is chopped in groups of 7
bits, starting from the least significant bit, until the last group has at most
6 bits left. In that order, each group of 7 bits is serialized as one byte with
the most significant bit set to `1`. The last group of 6 bits or less is a signed
number having value in the range [-32, 32) and it is serialized as before.

Integers can be arbitrarily large. A conforming implementation must support at
least integers of 64 bits in size.

```
integer = {1XXXXXXX} 00XXXXXX .
```

**Example**

The number `-741` in two's complement binary representation is `10100011011`.
If we chop it in groups of 7 bits starting from the least significant bit we
have `1010` `0011011`.

`0011011` is serialized first as one byte with the most significant bit set:
`10011011`.
The last group is only 4 bits long and it is a negative number (most
significant bit is 1) with value `-6`. We sign extend it to a 6 bits payload and
store it as one final byte with the two most significant bits set to `0`:
`00111010`.

### Null, true and false

`null`, `true` and `false` are encoded as one byte type tag each, with the
following binary values.

```
null = 01000000 .
true = 01000001 .
false = 01000010 .
```

### Float and double

Float and double have one byte tag each followed by their respective IEEE 754
single or double floating point binary format in little endian.

```
float = 01000011 "32 bits little endian IEEE 754 single precision floating point number" .
double = 01000100 "64 bits little endian IEEE 754 double precision floating point number" .
```

### List

A list of maximum 15 elements can be encoded with one byte type tag `0101XXXX`
where the least significant 4 bits store the length of the list as unsigned
integer, followed by LEON objects as its elements.
A list of an arbitrary number of elements is serialized with one byte type tag
`01000101` followed by a LEON integer representing its length and LEON objects
as its elements.

The length of a list must match the number of encoded elements.

```
list = smallList | bigList .
smallList = 0101XXXX {object} .
bigList = 01000101 length {object} .
```


### String

A string is serialized as encoded [UTF-8](https://en.wikipedia.org/wiki/UTF-8)
raw bytes.
If the size of the string is at most 31 bytes, the string can be encoded with
one byte type tag `011XXXXX`, where the least significant 5 bits store the size
as unsigned integer, followed by its raw bytes.
A string of an arbitrary size is serialized with one byte type tag `01000110`, a
LEON integer representing its size and followed by its raw bytes.

The size of a string must match the number of its raw bytes.

```
string = smallString | bigString .
smallString = 011XXXXX "UTF8 string of at most 31 bytes in size" .
bigString = 01000110 size "UTF8 string of at most 'size' bytes" .
```

### Bytes

A sequence of bytes of data is encoded with one byte type tag `01000111`, a LEON
integer representing its size and followed by its raw bytes

The size of the sequence of bytes must match the number of its raw bytes.


```
bytes = 01000111 size {XXXXXXXX} .
```

### Map

A map is a sequence of `size` key-value pairs.
It is encoded with one byte type tag `01001000`, a LEON integer representing its
size and followed by pairs of LEON objects.

The size of a map must match the number of its encoded pairs.

```
map = 01001000 size {key value} .
key = object .
value = object .
```


## Encoding table

| Type tag      | Interpretation                                                                       |
| ------------- | -------------------------------------------------------------------------------------|
| `00` `XXXXXX` | integer -32 <= x < 32                                                                |
| `1X` `XXXXXX` | variable integer payload                                                             |
| `01` `000000` | null                                                                                 |
| `01` `000001` | true                                                                                 |
| `01` `000010` | false                                                                                |
| `01` `000011` | float - 32 bits little endian IEEE 754 single precision floating point number        |
| `01` `000100` | double - 64 bits little endian IEEE 754 double precision floating point number       |
| `01` `000101` | list - length - elements                                                             |
| `01` `000110` | string - size - UTF8 string bytes                                                    |
| `01` `000111` | bytes - size - bytes                                                                 |
| `01` `001000` | map - num pairs - list of key value pairs                                            |
| `01` `001001` | Reserved for future extensions                                                       |
| `01` `001010` | Reserved for future extensions                                                       |
| `01` `001011` | Reserved for future extensions                                                       |
| `01` `001100` | Reserved for future extensions                                                       |
| `01` `001101` | Reserved for future extensions                                                       |
| `01` `001110` | Reserved for future extensions                                                       |
| `01` `001111` | Reserved for future extensions                                                       |
| `01` `01XXXX` | list of at most 15 elements - elements                                               |
| `01` `1XXXXX` | UTF8 string of at most 31 bytes in size - raw bytes                                  |