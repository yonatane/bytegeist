(ns bytegeist.core
  (:refer-clojure :exclude [get read set])
  (:import (io.netty.buffer ByteBuf)
           (java.nio ByteBuffer)
           (java.nio.charset StandardCharsets)))

(defn- shape
  [s]
  (if (vector? s)
    (first s)
    (throw (IllegalArgumentException. "Only supporting vectors currently"))))

(defn- map-shape?
  [s]
  (= :map (shape s)))

(defn- vector-shape?
  [s]
  (= :vector (shape s)))

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

(defn map-add-fields
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

(defn add-fields [s fields]
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

;TODO have a protocol for ByteBuf to avoid type hints

(def int16
  (reify
    Spec
    (read [_ b]
      (.readShort ^ByteBuf b))
    (write [_ b v]
      (.writeShort ^ByteBuf b (int v)))))

(def int32
  (reify
    Spec
    (read [_ b]
      (.readInt ^ByteBuf b))
    (write [_ b v]
      (.writeInt ^ByteBuf b (int v)))))
