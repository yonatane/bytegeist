(ns bytegeist.bytegeist
  (:refer-clojure :exclude [byte get read set])
  (:import (io.netty.buffer ByteBuf)
           (java.nio ByteBuffer)
           (java.nio.charset StandardCharsets)))

(defn- append
  [s fields]
  (conj s fields))

(defn- override?
  [field]
  (= :override (first field)))

(defn- remove-override-tag
  [field]
  (vec (rest field)))

(defn- normalize-field
  [field]
  (cond-> field (override? field) remove-override-tag))

(defn- vector-add-fields
  [s fields]
  (throw (Exception. "vector-add-fields not implemented yet")))

(defn- map-fields
  [s]
  (rest s))

(defn- field-name
  [field]
  (first field))

(defn- contains-field?
  [coll field]
  (some #{(field-name field)} (map field-name coll)))

(defn- map-add-fields
  [s fields]
  (let [; Ordered normalized fields
        all-ordered
        (mapv normalize-field (concat (map-fields s) fields))

        ; Map field-name to its overridden type
        final-types
        (into {} all-ordered)

        ; Reconstruct the map fields, old fields retain their position but their type might be overridden.
        final-fields
        (reduce
          (fn [r field]
            (let [fname (field-name field)]
              (if (contains-field? r field)
                r
                (conj r [fname (clojure.core/get final-types fname)]))))
          []
          all-ordered)]

    (into [:map] final-fields)))

(defn add-fields
  [s fields]
  (cond
    (= :map (nth s 0))
    (map-add-fields s fields)

    :else
    (throw (IllegalArgumentException. "Unsupported spec for add-fields"))))

(defprotocol Spec
  (read [_ b] "Relative read and increment the index")
  (write [_ b v] "Relative write and increment the index"))

(def bool
  (reify
    Spec
    (read [_ b]
      (.readBoolean ^ByteBuf b))
    (write [_ b v]
      (.writeBoolean ^ByteBuf b (boolean v)))))

(def byte
  (reify
    Spec
    (read [_ b]
      (.readByte ^ByteBuf b))
    (write [_ b v]
      (.writeByte ^ByteBuf b (unchecked-int v)))))

(def int16
  (reify
    Spec
    (read [_ b]
      (.readShort ^ByteBuf b))
    (write [_ b v]
      (.writeShort ^ByteBuf b (unchecked-int v)))))

(def int24
  (reify
    Spec
    (read [_ b]
      (.readMedium ^ByteBuf b))
    (write [_ b v]
      (.writeMedium ^ByteBuf b (unchecked-int v)))))

(def int32
  (reify
    Spec
    (read [_ b]
      (.readInt ^ByteBuf b))
    (write [_ b v]
      (.writeInt ^ByteBuf b (unchecked-int v)))))

(def int64
  (reify
    Spec
    (read [_ b]
      (.readLong ^ByteBuf b))
    (write [_ b v]
      (.writeLong ^ByteBuf b (unchecked-long v)))))

(def ubyte
  (reify
    Spec
    (read [_ b]
      (.readUnsignedByte ^ByteBuf b))
    (write [_ b v]
      (.writeByte ^ByteBuf b (unchecked-int v)))))

(def uint32
  (reify
    Spec
    (read [_ b]
      (.readUnsignedInt ^ByteBuf b))
    (write [_ b v]
      (.writeInt ^ByteBuf b (unchecked-int v)))))

(def uvarint32
  (reify
    Spec
    (read [_ b]
      (bytegeist.protobuf.Util/readUnsignedVarint32 ^ByteBuf b))
    (write [_ b v]
      (bytegeist.protobuf.Util/writeUnsignedVarint32 ^ByteBuf b (unchecked-int v)))))

(declare spec)

(defn- compile-field
  [[field-name field-spec]]
  [field-name (spec field-spec)])

(defn map-spec
  [s]
  (let [compiled-fields (mapv compile-field (map-fields s))]
    (reify
      Spec
      (read [_ b]
        (into {}
              (map (fn [[field-name field-spec]]
                     [field-name (read field-spec b)]))
              compiled-fields))
      (write [_ b v]
        (run! (fn [[field-name field-spec]]
                (write field-spec b (clojure.core/get v field-name)))
              compiled-fields)))))

(defn vector-spec
  [s]
  (let [specs (mapv spec (rest s))]
    (reify
      Spec
      (read [_ b]
        (mapv (fn [item-spec] (read item-spec b)) specs))
      (write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (write item-spec b item)
              (recur (inc i)))))))))

(defn fixed-length-string-spec
  [length]
  (reify
    Spec
    (read [_ b]
      (.readCharSequence ^ByteBuf b length StandardCharsets/UTF_8))
    (write [_ b v]
      (.writeBytes ^ByteBuf b (.getBytes ^String v StandardCharsets/UTF_8)))))

(defn length-delimited-string-spec
  [delimiter-spec offset]
  (let [offset (or offset 0)]
    (reify
      Spec
      (read [_ b]
        (let [len (- (read delimiter-spec b) offset)]
          (if (< len 0)
            nil
            (.readCharSequence ^ByteBuf b len StandardCharsets/UTF_8))))
      (write [_ b v]
        (if (nil? v)
          (write delimiter-spec ^ByteBuf b (dec offset))
          (let [byts (.getBytes ^String v StandardCharsets/UTF_8)]
            (write delimiter-spec ^ByteBuf b (+ (alength byts) offset))
            (.writeBytes ^ByteBuf b byts)))))))

(defn string-spec
  [[_ length offset]]
  (cond
    (int? length)
    (fixed-length-string-spec length)

    :else
    (length-delimited-string-spec (spec length) offset)))

;; Registry

(def registry
  {:bool bool
   :boolean bool
   :byte byte
   :int16 int16
   :short int16
   :int24 int24
   :int32 int32
   :int int32
   :int64 int64
   :long int64
   :ubyte ubyte
   :uint32 uint32
   :uvarint32 uvarint32})

(def f-registry
  {:map map-spec
   :vector vector-spec
   :string string-spec})

(defn compile-spec-vector
  [s]
  (let [shape (nth s 0)
        f (clojure.core/get f-registry shape)]
    (f s)))

(defn spec [s]
  (cond
    (satisfies? Spec s)
    s

    (keyword? s)
    (clojure.core/get registry s)

    (vector? s)
    (compile-spec-vector s)))
