(ns msgpack.streaming-test
  (:require [clojure.test :refer :all]
            [msgpack.streaming :refer [pack unpack ->Extended]]))

(defn- byte-literals
  [bytes]
  (map unchecked-byte bytes))

(defn- byte-array-literal
  [bytes]
  (byte-array (byte-literals bytes)))

(defn- fill [n c]
  (clojure.string/join "" (repeat n c)))

(defn- ext [type bytes]
  (->Extended type (byte-literals bytes)))

(defmacro packable [thing bytes]
  `(let [thing# ~thing
         bytes# (byte-literals ~bytes)]
     (is (= bytes# (pack thing#)))))

(defmacro round-trip [thing bytes]
  `(let [thing# ~thing
         bytes# (byte-literals ~bytes)]
     (is (= bytes# (pack thing#)))
     (is (= thing# (unpack bytes#)))
     (is (= thing# (unpack (pack thing#))))))

(deftest nil-test
  (testing "nil"
    (round-trip nil [0xc0])))

(deftest boolean-test
  (testing "booleans"
    (round-trip false [0xc2])
    (round-trip true [0xc3])))

(deftest int-test
  (testing "positive fixnum"
    (round-trip 0 [0x00])
    (round-trip 0x10 [0x10])
    (round-trip 0x7f [0x7f]))
  (testing "negative fixnum"
    (round-trip -1 [0xff])
    (round-trip -16 [0xf0])
    (round-trip -32 [0xe0]))
  (testing "uint 8"
    (round-trip 0x80 [0xcc 0x80])
    (round-trip 0xf0 [0xcc 0xf0])
    (round-trip 0xff [0xcc 0xff]))
  (testing "uint 16"
    (round-trip 0x100 [0xcd 0x01 0x00])
    (round-trip 0x2000 [0xcd 0x20 0x00])
    (round-trip 0xffff [0xcd 0xff 0xff]))
  (testing "uint 32"
    (packable 0x10000 [0xce 0x00 0x01 0x00 0x00])
    (packable 0x200000 [0xce 0x00 0x20 0x00 0x00])
    (packable 0xffffffff [0xce 0xff 0xff 0xff 0xff]))
  (testing "uint 64"
    (packable 0x100000000 [0xcf 0x00 0x00 0x00 0x01 0x00 0x00 0x00 0x00])
    (packable 0x200000000000 [0xcf 0x00 0x00 0x20 0x00 0x00 0x00 0x00 0x00])
    (packable 0xffffffffffffffff [0xcf 0xff 0xff 0xff 0xff 0xff 0xff 0xff 0xff]))
  (testing "int 8"
    (packable -33 [0xd0 0xdf])
    (packable -100 [0xd0 0x9c])
    (packable -128 [0xd0 0x80]))
  (testing "int 16"
    (packable -129 [0xd1 0xff 0x7f])
    (packable -2000 [0xd1 0xf8 0x30])
    (packable -32768 [0xd1 0x80 0x00]))
  (testing "int 32"
    (packable -32769 [0xd2 0xff 0xff 0x7f 0xff])
    (packable -1000000000 [0xd2 0xc4 0x65 0x36 0x00])
    (packable -2147483648 [0xd2 0x80 0x00 0x00 0x00]))
  (testing "int 64"
    (packable -2147483649 [0xd3 0xff 0xff 0xff 0xff 0x7f 0xff 0xff 0xff])
    (packable -1000000000000000002 [0xd3 0xf2 0x1f 0x49 0x4c 0x58 0x9b 0xff 0xfe])
    (packable -9223372036854775808 [0xd3 0x80 0x00 0x00 0x00 0x00 0x00 0x00 0x00])))

(deftest float-test
  (testing "float 32"
    (packable 0.0 [0xca 0x00 0x00 0x00 0x00])
    (packable 2.5 [0xca 0x40 0x20 0x00 0x00])
    (packable 5/2 [0xca 0x40 0x20 0x00 0x00])) ; one-way
  (testing "float 64"
    (packable 1e39 [0xcb 0x48 0x7 0x82 0x87 0xf4 0x9c 0x4a 0x1d])))

(deftest str-test
  (testing "fixstr"
    (packable "hello world" [0xab 0x68 0x65 0x6c 0x6c 0x6f 0x20 0x77 0x6f 0x72 0x6c 0x64])
    (packable "" [0xa0])
    (packable "abc" [0xa3 0x61 0x62 0x63])
    (packable 'abc [0xa3 0x61 0x62 0x63])
    (packable :abc [0xa3 0x61 0x62 0x63])
    (packable (fill 31 \a) (cons 0xbf (repeat 31 0x61))))
  (testing "str 8"
    (packable (fill 32 \b)
              (concat [0xd9 0x20] (repeat 32 (byte \b))))
    (packable (fill 100 \c)
              (concat [0xd9 0x64] (repeat 100 (byte \c))))
    (packable (fill 255 \d)
              (concat [0xd9 0xff] (repeat 255 (byte \d)))))
  (testing "str 16"
    (packable (fill 256 \b)
              (concat [0xda 0x01 0x00] (repeat 256 (byte \b))))
    (packable (fill 65535 \c)
              (concat [0xda 0xff 0xff] (repeat 65535 (byte \c)))))
  (testing "str 32"
    (packable (fill 65536 \b)
              (concat [0xdb 0x00 0x01 0x00 0x00] (repeat 65536 (byte \b))))))

(deftest bin-test
  (testing "bin 8"
    (packable (byte-array 0) [0xc4 0x00])
    (packable (byte-array-literal [0x80]) [0xc4 0x01 0x80])
    (packable (byte-array-literal (repeat 32 0x80)) (concat [0xc4 0x20] (repeat 32 0x80)))
    (packable (byte-array-literal (repeat 255 0x80)) (concat [0xc4 0xff] (repeat 255 0x80))))
  (testing "bin 16"
    (packable (byte-array-literal (repeat 256 0x80)) (concat [0xc5 0x01 0x00] (repeat 256 0x80))))
  (testing "bin 32"
    (packable (byte-array-literal (repeat 65536 0x80))
              (concat [0xc6 0x00 0x01 0x00 0x00] (repeat 65536 0x80)))))

(deftest ext-test
  (testing "fixext 1"
    (packable (ext 5 [0x80]) [0xd4 0x05 0x80]))
  (testing "fixext 2"
    (packable (ext 5 [0x80 0x80]) [0xd5 0x05 0x80 0x80]))
  (testing "fixext 4"
    (packable (ext 5 [0x80 0x80 0x80 0x80])
              [0xd6 0x05 0x80 0x80 0x80 0x80]))
  (testing "fixext 8"
    (packable (ext 5 [0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80])
              [0xd7 0x05 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80]))
  (testing "fixext 16"
    (packable (ext 5 (repeat 16 0x80))
              (concat [0xd8 0x05] (repeat 16 0x80))))
  (testing "ext 8"
    (packable (ext 55 [0x1 0x3 0x11])
              [0xc7 0x3 0x37 0x1 0x3 0x11])
    (packable (ext 5 (repeat 255 0x80))
              (concat [0xc7 0xff 0x05] (repeat 255 0x80))))
  (testing "ext 16"
    (packable (ext 5 (repeat 256 0x80))
              (concat [0xc8 0x01 0x00 0x05] (repeat 256 0x80))))
  (testing "ext 32"
    (packable (ext 5 (repeat 65536 0x80))
              (concat [0xc9 0x00 0x01 0x00 0x00 0x05] (repeat 65536 0x80)))))

(deftest array-test
  (testing "fixarray"
    (packable '() [0x90])
    (packable [] [0x90])
    (packable #{} [0x90])
    (packable [[]] [0x91 0x90])
    (packable [5 "abc", true] [0x93 0x05 0xa3 0x61 0x62 0x63 0xc3])
    (packable [true 1 (ext 3 (.getBytes "foo")) 0xff {1 false 2 "abc"} (byte-array-literal [0x80]) [1 2 3] "abc"]
              [0x98 0xc3 0x01 0xc7 0x03 0x03 0x66 0x6f 0x6f 0xcc 0xff 0x82 0x01 0xc2 0x02 0xa3 0x61 0x62 0x63 0xc4 0x01 0x80 0x93 0x01 0x02 0x03 0xa3 0x61 0x62 0x63]))
  (testing "array 16"
    (packable (repeat 16 5)
              (concat [0xdc 0x00 0x10] (repeat 16 5)))
    (packable (repeat 65535 5)
              (concat [0xdc 0xff 0xff] (repeat 65535 5))))
  (testing "array 32"
    (packable (repeat 65536 5)
              (concat [0xdd 0x00 0x01 0x00 0x00] (repeat 65536 5)))))
(deftest map-test
  (testing "fixmap"
    (packable {} [0x80])
    (packable {1 true 2 "abc" 3 (byte-array-literal [0x80])}
              [0x83 0x01 0xc3 0x02 0xa3 0x61 0x62 0x63 0x03 0xc4 0x01 0x80])
    (packable {"abc" 5} [0x81 0xa3 0x61 0x62 0x63 0x05])
    (packable {(byte-array-literal [0x80]) 0xffff}
              [0x81 0xc4 0x01 0x80 0xcd 0xff 0xff])
    (packable {true nil} [0x81 0xc3 0xc0])
    (packable {:compact true :schema 0}
              [0x82 0xa6 0x73 0x63 0x68 0x65 0x6d 0x61 0x00 0xa7 0x63 0x6f 0x6d 0x70 0x61 0x63 0x74 0xc3])
    (packable {1 [{1 2 3 4} {}], 2 1, 3 [false "def"], 4 {0x100000000 "a" 0xffffffff "b"}}
              [0x84 0x01 0x92 0x82 0x01 0x02 0x03 0x04 0x80 0x02 0x01 0x03 0x92 0xc2 0xa3 0x64 0x65 0x66 0x04 0x82 0xcf 0x00 0x00 0x00 0x01 0x00 0x00 0x00 0x00 0xa1 0x61 0xce 0xff 0xff 0xff 0xff 0xa1 0x62]))
  (testing "map 16"
    (packable (zipmap (range 0 16) (repeat 16 5))
              (concat [0xde 0x00 0x10]
                      (interleave (range 0 16) (repeat 16 5))))))
