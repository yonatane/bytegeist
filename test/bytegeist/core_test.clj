(ns bytegeist.core-test
  (:require [clojure.test :refer [deftest testing is are]]
            [bytegeist.core :as g])
  (:import (io.netty.buffer Unpooled ByteBuf)
           (java.nio.charset StandardCharsets)))

(def max-ubyte (-> Byte/MAX_VALUE inc (* 2) dec))
(def max-int24 8388607)
(def min-int24 -8388608)
(def max-uint (-> Integer/MAX_VALUE inc (* 2) dec))

(def nullable-string
  (reify
    g/Spec
    (read [_ b]
      (let [len (.readShort ^ByteBuf b)]
        (if (< len 0)
          nil
          (.readCharSequence ^ByteBuf b len StandardCharsets/UTF_8))))
    (write [_ b v]
      (if (nil? v)
        (.writeShort ^ByteBuf b (int -1))
        (let [byts (.getBytes ^String v StandardCharsets/UTF_8)]
          (.writeShort ^ByteBuf b (int (alength byts)))
          (.writeBytes ^ByteBuf b byts))))))

(def message
  [:map
   [:size :int32]])

(def request-header-v0
  [:map
   [:api-key :int16]
   [:api-version :int16]
   [:correlation-id :int32]])

(def request-header-v1
  (-> request-header-v0
      (g/add-fields [[:client-id nullable-string]])))

(deftest spec
  (testing "Spec new version from previous"
    (let [v0 [:map [:a :int32]]
          v1 (g/add-fields v0 [[:b :int16]])
          literal-v1 [:map
                      [:a :int32]
                      [:b :int16]]]
      (is (= literal-v1 v1)))))

(deftest primitive-write-and-read
  (testing "boolean"
    (are [v] (let [b (Unpooled/buffer 1 1)]
               (g/write g/bool b v)
               (= v (g/read g/bool b)))
      true
      false))

  (testing "signed"
    (are [num-bytes s v] (let [b (Unpooled/buffer num-bytes num-bytes)]
                           (g/write s b v)
                           (= v (g/read s b)))
      1 g/byte Byte/MAX_VALUE
      1 g/byte Byte/MIN_VALUE
      2 g/int16 Short/MAX_VALUE
      2 g/int16 Short/MIN_VALUE
      3 g/int24 max-int24
      3 g/int24 min-int24
      4 g/int32 Integer/MAX_VALUE
      4 g/int32 Integer/MIN_VALUE
      8 g/int64 Long/MAX_VALUE
      8 g/int64 Long/MIN_VALUE))

  (testing "unsigned"
    (are [num-bytes s v] (let [b (Unpooled/buffer num-bytes num-bytes)]
                             (g/write s b v)
                             (= v (g/read s b)))
      1 g/ubyte max-ubyte
      4 g/uint32 max-uint)
    (are [num-bytes s w r] (let [b (Unpooled/buffer num-bytes num-bytes)]
                           (g/write s b w)
                           (= r (g/read s b)))
      1 g/ubyte (inc max-ubyte) 0
      1 g/ubyte -1 max-ubyte
      4 g/uint32 (inc max-uint) 0
      4 g/uint32 -1 max-uint)))

(deftest nullable-string-test
  (testing "nullable-string nil"
    (let [^g/Spec s nullable-string
          b (Unpooled/buffer 2 2)
          v nil]
      (g/write s b v)
      (is (= v (g/read s b)))))
  (testing "nullable-string empty"
    (let [^g/Spec s nullable-string
          b (Unpooled/buffer 2 2)
          v ""]
      (g/write s b v)
      (is (= v (g/read s b)))))
  (testing "nullable-string non-empty"
    (let [^g/Spec s nullable-string
          b (Unpooled/buffer)
          v "a b c"]
      (g/write s b v)
      (is (= v (g/read s b))))))

(deftest map-write-and-read
  (testing "with int32"
    (let [s (g/spec [:map [:a :int32]])
          b (Unpooled/buffer)
          v {:a 1}]
      (g/write s b v)
      (is (= v (g/read s b)))))
  (testing "nested map"
    (let [s (g/spec [:map
                     [:a :int32]
                     [:m [:map
                          [:nested :int16]]]])
          b (Unpooled/buffer)
          v {:a 1
             :m {:nested 2}}]
      (g/write s b v)
      (is (= v (g/read s b))))))
