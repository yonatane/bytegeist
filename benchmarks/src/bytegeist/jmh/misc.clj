(ns bytegeist.jmh.misc
  (:require [bytegeist.bytegeist :as g])
  (:import (org.openjdk.jmh.infra Blackhole)
           (io.netty.buffer ByteBuf Unpooled)))

;; States

(defn consume-cpu [^long l]
  (Blackhole/consumeCPU l))

(defn read-megamorphic
  [s b]
  (g/-read (g/-to-spec s) b))

(defn read-inline
  {:inline (fn [s b] `(g/-read (g/-to-spec ~s) ~b))}
  [s b]
  (g/-read (g/-to-spec s) b))

(defn rolled-tuple
  [s]
  (let [specs (mapv g/spec (rest s))]
      g/ToSpec
      (-to-spec [this] this)
      g/Spec
      (-read [_ b]
        (mapv (fn [item-spec] (g/-read item-spec b)) specs))
      (-write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (g/-write item-spec b item)
              (recur (inc i))))))))

(defn unrolled-2-tuple
  [s]
  (let [[spec1 spec2 :as specs] (mapv g/spec (rest s))]
    (reify
      g/ToSpec
      (-to-spec [this] this)
      g/Spec
      (-read [_ b]
        [(g/-read spec1 b) (g/-read spec2 b)])
      (-write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (g/-write item-spec b item)
              (recur (inc i)))))))))

(defn unrolled-2-tuple-transient
  [s]
  (let [[spec1 spec2 :as specs] (mapv g/spec (rest s))]
    (reify
      g/ToSpec
      (-to-spec [this] this)
      g/Spec
      (-read [_ b]
        (let [t (transient [])]
          (conj! t (g/-read spec1 b))
          (conj! t (g/-read spec2 b))
          (persistent! t)))
      (-write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (g/-write item-spec b item)
              (recur (inc i)))))))))

(defn unrolled-2-tuple-cond
  [s]
  (let [[s1 s2 s3 s4 :as specs] (mapv g/spec (rest s))
        s-count (count specs)]
    (reify
      g/ToSpec
      (-to-spec [this] this)
      (-to-spec [this] this)

      g/Spec
      (-read [_ b]
        (cond
          (= s-count 1) [(g/-read s1 b)]
          (= s-count 2) [(g/-read s1 b) (g/-read s2 b)]
          (= s-count 3) [(g/-read s1 b) (g/-read s2 b) (g/-read s3 b)]
          (= s-count 4) [(g/-read s1 b) (g/-read s2 b) (g/-read s3 b) (g/-read s4 b)]
          :else (mapv (fn [item-spec] (g/-read item-spec b)) specs)))
      (-write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (g/-write item-spec b item)
              (recur (inc i)))))))))

(defn unrolled-tuple-cond
  [s]
  (let [[s1 s2 s3 s4 :as specs] (mapv g/spec (rest s))
        s-count (count specs)]
    (reify
      g/ToSpec
      (-to-spec [this] this)
      g/Spec
      (-read [_ b]
        (cond
          (= s-count 1) [(g/-read s1 b)]
          (= s-count 2) [(g/-read s1 b) (g/-read s2 b)]
          (= s-count 3) [(g/-read s1 b) (g/-read s2 b) (g/-read s3 b)]
          (= s-count 4) [(g/-read s1 b) (g/-read s2 b) (g/-read s3 b) (g/-read s4 b)]
          :else (mapv (fn [item-spec] (g/-read item-spec b)) specs)))
      (-write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (g/-write item-spec b item)
              (recur (inc i)))))))))

(defn compile-tuple-cond-specs [specs]
  (mapv unrolled-2-tuple-cond specs))

(defn compile-rolled-tuple-specs [specs]
  (mapv rolled-tuple specs))

(defn compile-specs [specs]
  (mapv g/spec specs))

(defn rand-spec
  "Picks a rand spec on each invocation. Fist arg is the result of prev invocation and is ignored here."
  [_ specs]
  (rand-nth specs))

(defn prepare-buf [size s v]
  (let [^ByteBuf b (Unpooled/buffer size size)]
    (g/write s b v)
    (.resetReaderIndex b)))

(defn reset-reader [^ByteBuf b]
  (.resetReaderIndex b))

;; Benchmarks

;TODO bench satisfies? vs ToSpec in a reading scenario

(defn baseline [])

(defn read-read [^Blackhole bh s1 s2 b]
  (.consume bh (g/read s1 b))
  (.consume bh (g/read s2 b)))

(defn read-read-direct [^Blackhole bh s1 s2 b]
  (.consume bh (g/-read s1 b))
  (.consume bh (g/-read s2 b)))

(defn read-read-read [^Blackhole bh s1 s2 s3 b]
  (.consume bh (g/read s1 b))
  (.consume bh (g/read s2 b))
  (.consume bh (g/read s3 b)))

(defn read-3x-inline [^Blackhole bh s1 s2 s3 b]
  (.consume bh (g/read-inline s1 b))
  (.consume bh (g/read-inline s2 b))
  (.consume bh (g/read-inline s3 b)))

(defn read-3x-direct-to-spec [^Blackhole bh s1 s2 s3 b]
  (.consume bh (g/-read (g/-to-spec s1) b))
  (.consume bh (g/-read (g/-to-spec s2) b))
  (.consume bh (g/-read (g/-to-spec s3) b)))

(defn read-read-read-direct [^Blackhole bh s1 s2 s3 b]
  (.consume bh (g/-read s1 b))
  (.consume bh (g/-read s2 b))
  (.consume bh (g/-read s3 b)))
