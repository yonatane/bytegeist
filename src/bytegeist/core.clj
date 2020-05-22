(ns bytegeist.core
  (:refer-clojure :exclude [byte get read set])
  (:import (io.netty.buffer ByteBuf)
           (java.nio ByteBuffer)
           (java.nio.charset StandardCharsets)))

(defn- shape
  [s]
  (if (vector? s)
    (first s)
    (throw (IllegalArgumentException. "Only supporting vectors currently"))))

(defn- shape-spec?
  [s]
  (vector? s))

(defn- map-shape?
  [s]
  (and (shape-spec? s)
       (= :map (shape s))))

(defn- vector-shape?
  [s]
  (and (shape-spec? s)
       (= :vector (shape s))))

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
    (map-shape? s)
    (map-add-fields s fields)

    (vector-shape? s)
    (vector-add-fields s fields)

    :else
    (throw (IllegalArgumentException. "Unknown shape passed to add-fields"))))

(defprotocol Spec
  (read [_ b] "Relative read and increment the index")
  (write [_ b v] "Relative write and increment the index"))

;TODO non-yielding fields that increment the index but aren't returned on read.

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

(def registry
  {:bool bool
   :boolean bool
   :byte byte
   :int16 int16
   :int24 int24
   :int32 int32
   :int int32
   :int64 int64
   :long int64
   :ubyte ubyte
   :uint32 uint32
   :uvarint32 uvarint32
   })

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
                     [field-name (read field-spec ^ByteBuf b)]))
              compiled-fields))
      (write [_ b v]
        (run! (fn [[field-name field-spec]]
                (write field-spec b (clojure.core/get v field-name)))
              compiled-fields)))))

(defn spec [s]
  (cond
    (satisfies? Spec s)
    s

    (keyword? s)
    (clojure.core/get registry s)

    (map-shape? s)
    (map-spec s)

    (vector-shape? s)
    (throw (IllegalArgumentException. "Vector spec not implemented yet"))))
