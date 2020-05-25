(ns bytegeist.bytegeist-test
  (:require [clojure.test :refer [deftest testing is are]]
            [bytegeist.bytegeist :as g])
  (:import (io.netty.buffer Unpooled)))

(def max-ubyte (-> Byte/MAX_VALUE inc (* 2) dec))
(def max-int24 8388607)
(def min-int24 -8388608)
(def max-uint (-> Integer/MAX_VALUE inc (* 2) dec))
(defn max-uvarint [num-bytes] (long (dec (Math/pow 2 (* 7 num-bytes)))))

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
      (g/add-fields [[:client-id [:string :short]]])))

(deftest spec
  (testing "Spec new version from previous"
    (let [v0 [:map [:a :int32]]
          v1 (g/add-fields v0 [[:b :int16]])
          literal-v1 [:map
                      [:a :int32]
                      [:b :int16]]]
      (is (= literal-v1 v1)))))

(deftest primitive-write-read
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

  (testing "unsigned within range"
    (are [num-bytes s v] (let [b (Unpooled/buffer num-bytes num-bytes)]
                           (g/write s b v)
                           (= v (g/read s b)))
      1 g/ubyte max-ubyte
      4 g/uint32 max-uint
      1 g/uvarint32 0
      1 g/uvarint32 (max-uvarint 1)
      2 g/uvarint32 (max-uvarint 2)
      3 g/uvarint32 (max-uvarint 3)
      4 g/uvarint32 (max-uvarint 4)))
  (testing "unsigned wrap"
    (are [num-bytes s w r] (let [b (Unpooled/buffer num-bytes num-bytes)]
                             (g/write s b w)
                             (= r (g/read s b)))
      1 g/ubyte (inc max-ubyte) 0
      1 g/ubyte -1 max-ubyte
      4 g/uint32 (inc max-uint) 0
      4 g/uint32 -1 max-uint))
  (testing "uvarint out of bounds"
    (is (thrown? Exception
                 (let [b (Unpooled/buffer 1 1)]
                   (g/write g/uvarint32 b (inc (max-uvarint 1))))))
    (is (thrown? Exception
                 (let [b (Unpooled/buffer 2 2)]
                   (g/write g/uvarint32 b (inc (max-uvarint 2))))))
    (is (thrown? Exception
                 (let [b (Unpooled/buffer 3 3)]
                   (g/write g/uvarint32 b (inc (max-uvarint 3))))))
    (is (thrown? Exception
                 (let [b (Unpooled/buffer 4 4)]
                   (g/write g/uvarint32 b (inc (max-uvarint 4))))))))

(deftest string-test
  (testing "No offset"
    (are [length v] (let [s (g/spec [:string length])
                          b (Unpooled/buffer)]
                      (g/write s b v)
                      (= v (g/read s b)))
      0 ""
      3 "abc"
      :short nil
      :short ""
      :short "short-delimited"
      :int "int-delimited"
      :uvarint32 ""
      :uvarint32 "uvarint-delimited"))

  (testing "-1 Offset"
    (are [delimeter v] (let [s (g/spec [:string delimeter 1])
                             b (Unpooled/buffer)]
                         (g/write s b v)
                         (= v (g/read s b)))
      :short nil
      :short ""
      :short "short-delimited"
      :int "int-delimited"
      :uvarint32 nil
      :uvarint32 ""
      :uvarint32 "uvarint-delimited")))

(deftest tuple-write-read
  (are [s v] (let [b (Unpooled/buffer)]
               (g/write s b v)
               (= v (g/read s b)))
    (g/spec [:tuple :bool])
    [true]

    (g/spec [:tuple :uvarint32 :bool :int24 [:map [:a [:tuple :int32 :uint32]]]])
    [(max-uvarint 2) false max-int24 {:a [0 max-uint]}]))

(deftest vector-write-read
  (are [s v] (let [b (Unpooled/buffer)]
               (g/write s b v)
               (= v (g/read s b)))
    (g/spec [:vector 3 :bool])
    [true false true]))

(deftest map-write-read
  (are [s v] (let [b (Unpooled/buffer)]
               (g/write s b v)
               (= v (g/read s b)))
    (g/spec [:map [:a :int32]])
    {:a 1}

    (g/spec [:map
             [:a :int32]
             [:m [:map [:b :int16]]]])
    {:a 1
     :m {:b 2}}

    (g/spec [:map
             [:a :int32]
             [:b :uint32]
             [:m [:map
                  [:b :uint32]
                  [:c :int24]]]])
    {:a 1
     :b 2
     :m {:b (max-uvarint 3)
         :c max-int24}}))
