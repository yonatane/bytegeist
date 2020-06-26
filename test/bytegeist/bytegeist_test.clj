(ns bytegeist.bytegeist-test
  (:require [clojure.test :refer [deftest testing is are assert-expr do-report]]
            [clojure.walk]
            [bytegeist.bytegeist :as g])
  (:import (io.netty.buffer Unpooled)
           (java.util Arrays)
           (clojure.lang ExceptionInfo)))

(def max-ubyte (-> Byte/MAX_VALUE inc (* 2) dec))
(def max-int24 8388607)
(def min-int24 -8388608)
(def max-uint (-> Integer/MAX_VALUE inc (* 2) dec))
(defn max-uvarint [num-bytes] (long (dec (Math/pow 2 (* 7 num-bytes)))))

(defn write-read [s v]
  (let [b (Unpooled/buffer)]
    (g/write s b v)
    (g/read s b)))

(defn seq-bytes [v]
  (clojure.walk/postwalk (fn [x] (if (bytes? x) (seq x) x)) v))

(defn preserved?
  [s v]
  (= (seq-bytes v) (seq-bytes (write-read s v))))

(defmethod assert-expr 'preserved? [msg form]
  (let [s (nth form 1)
        v (nth form 2)]
    `(let [read-back# (write-read ~s ~v)
           report-type# (if (= ~v read-back#) :pass :fail)]
       (do-report {:type report-type#, :message ~msg,
                   :expected '~form, :actual read-back#}))))

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
      (g/add-fields [[:client-id [:string {:length :short}]]])))

(deftest fail-unsupported-input
  (is (thrown-with-msg? Exception #"Unsupported" (g/spec 123))))

(deftest fail-unknown-spec
  (is (thrown-with-msg? Exception #"Unknown" (g/spec :not-here))))

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
      4 g/float Float/MAX_VALUE
      4 g/float Float/MIN_VALUE
      8 g/int64 Long/MAX_VALUE
      8 g/int64 Long/MIN_VALUE
      8 g/double Double/MIN_VALUE
      8 g/double Double/MAX_VALUE))

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
    (are [length v] (let [s [:string {:length length}]
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

  (testing "Adjustment 1"
    (are [length v] (let [s [:string {:length length :adjust 1}]
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

(deftest bytes-spec
  (testing "No adjustment"
    (are [length v] (let [s [:bytes {:length length}]]
                      (preserved? s v))
      0 (byte-array 0)
      3 (byte-array 3 (byte 1))
      :short nil
      :short (byte-array 0)
      :short (byte-array 3 (byte 1))
      :uvarint32 (byte-array 0)
      :uvarint32 (byte-array (max-uvarint 4) (byte 1))))

  (testing "Adjustment 1"
    (are [length v] (let [s [:bytes {:length length :adjust 1}]]
                      (preserved? s v))
      :short nil
      :short (byte-array 0)
      :short (byte-array 3 (byte 1))
      :uvarint32 nil
      :uvarint32 (byte-array 0)
      :uvarint32 (byte-array (max-uvarint 4) (byte 1)))))

(deftest tuple-spec
  (are [s v] (preserved? s v)
    [:tuple :bool]
    [true]

    [:tuple :uvarint32 :bool :int24 [:map [:a [:tuple :int32 :uint32]]]]
    [(max-uvarint 2) false max-int24 {:a [0 max-uint]}]))

(deftest vector-spec
  (are [s v] (preserved? s v)
    [:vector {:length 3} :bool]
    [true false true]

    [:vector {:length :short} :bool]
    nil

    [:vector {:length :short} :bool]
    []

    [:vector {:length :short} :bool]
    [true false true]

    [:vector {:length :uvarint32, :adjust 1} :uvarint32]
    nil

    [:vector {:length :uvarint32, :adjust 1} :uvarint32]
    []

    [:vector {:length :uvarint32, :adjust 1} :uvarint32]
    [0 (max-uvarint 1) (max-uvarint 2) (max-uvarint 3) (max-uvarint 4)]

    [:vector {:length :uvarint32
              :adjust 1}
     [:map
      [:a :int32]
      [:b [:string {:length :uvarint32, :adjust 1}]]
      [:c [:vector {:length :uvarint32, :adjust 1} [:tuple :bool :short]]]]]
    [{:a 1 :b "test-string" :c [[true 1] [false 2] [true 3]]}]))

(deftest map-spec
  (are [s v] (preserved? s v)
    [:map [:a :int32]]
    {:a 1}

    [:map
     [:a :int32]
     [:m [:map [:b :int16]]]]
    {:a 1
     :m {:b 2}}

    [:map
     [:a :int32]
     [:b :uint32]
     [:m [:map
          [:b :uint32]
          [:c :int24]]]]
    {:a 1
     :b 2
     :m {:b (max-uvarint 3)
         :c max-int24}}

    [:map {:length :int32}
     [:a :int32]
     [:b :uint32]
     [:m [:map
          [:b :uint32]
          [:c :int24]]]]
    {:a 1
     :b 2
     :m {:b (max-uvarint 3)
         :c max-int24}}))

(deftest multi-spec-single-field
  (let [role [:multi {:dispatch :type}
              ["student"
               [:map
                [:type [:string {:length :short}]]
                [:grade :short]]]
              ["employee"
               [:map
                [:type [:string {:length :short}]]
                [:salary :int]]]]]
    (testing "Successful dispatch"
      (is (preserved? role {:type "student"
                            :grade 100}))
      (is (preserved? role {:type "employee"
                            :salary 99999})))
    (testing "Unmatched dispatch throws on write"
      (let [b (Unpooled/buffer)]
        (is (thrown? ExceptionInfo #"Invalid dispatch"
                     (g/write role b {:type "lizard"})))))
    (testing "Unmatched dispatch throws on read"
      (let [lizard [:map [:type [:string {:length :short}]]]
            b (Unpooled/buffer)]
        (g/write lizard b {:type "lizard"})
        (is (thrown? ExceptionInfo #"Invalid dispatch"
                     (g/read role b)))))))

(deftest multi-spec-multiple-fields
  (let [message
        [:multi {:dispatch [:type :version]}
         [["produce" 1]
          [:map
           [:type [:string {:length :short}]]
           [:version :short]
           [:data [:string {:length :int}]]]]
         [["produce" 2]
          [:map
           [:type [:string {:length :short}]]
           [:version :short]
           [:client-id [:string {:length :uvarint32}]]
           [:data [:string {:length :uvarint32}]]]]
         [["fetch" 1]
          [:map
           [:type [:string {:length :short}]]
           [:version :short]
           [:partitions [:vector {:length :uvarint32} :uvarint32]]]]]

        produce-v1 {:type "produce", :version 1
                    :data "test data"}
        produce-v2 {:type "produce", :version 2
                    :client-id "test client"
                    :data "test data"}
        fetch-v1 {:type "fetch", :version 1
                  :partitions [0 1 2]}]

    (is (preserved? message produce-v1))
    (is (preserved? message produce-v2))
    (is (preserved? message fetch-v1))))

(deftest multi-spec-fn
  (let [message
        [:multi {:dispatch [:type :version]
                 :dispatch-fn (fn [[t v]] [(keyword t) (if (> v 2) :new :old)])}
         [[:produce :old]
          [:map
           [:type [:string {:length :short}]]
           [:version :short]
           [:data [:string {:length :int}]]]]
         [[:produce :new]
          [:map
           [:type [:string {:length :short}]]
           [:version :short]
           [:client-id [:string {:length :uvarint32}]]
           [:data [:string {:length :uvarint32}]]]]
         [[:fetch :old]
          [:map
           [:type [:string {:length :short}]]
           [:version :short]
           [:partitions [:vector {:length :uvarint32} :uvarint32]]]]]]
    (is (preserved? message {:type "produce", :version 3
                             :client-id "test client"
                             :data "test data"}))))
