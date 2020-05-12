(ns bytegeist.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [bytegeist.core :as g])
  (:import (io.netty.buffer Unpooled ByteBuf)
           (java.nio.charset StandardCharsets)))

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
  (testing "primitives"
    (testing "int16"
      (let [^g/Spec s g/int16
            b (Unpooled/buffer 2 2)
            v 1]
        (g/write s b v)
        (is (= v (g/read s b)))))
    (testing "int32"
      (let [^g/Spec s g/int32
            b (Unpooled/buffer 4 4)
            v 1]
        (g/write s b v)
        (is (= v (g/read s b)))))))

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

(deftest length-field-based-frame
  (is (= true false) "Implement!"))
