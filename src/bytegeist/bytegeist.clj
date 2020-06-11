(ns bytegeist.bytegeist
  (:refer-clojure :exclude [byte get read read-string set])
  (:import (io.netty.buffer ByteBuf)
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

(defn- map-props
  [s]
  (let [props (nth s 1)]
    (when (map? props)
      props)))

(defn- map-fields
  [s]
  (if (map-props s)
    (nthrest s 2)
    (rest s)))

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
          all-ordered)

        root (if-let [props (map-props s)]
               [:map props]
               [:map])]

    (into root final-fields)))

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

(defn length-based-frame-spec [length-spec data-spec]
  (reify
    Spec
    (read [_ b]
      (some-> length-spec (read b))
      (read data-spec b))
    (write [_ b v]
      (let [frame-index (.writerIndex ^ByteBuf b)]
        (write length-spec b 0)
        (write data-spec b v)
        (let [length (- (.writerIndex ^ByteBuf b) frame-index)]
          (.writerIndex ^ByteBuf b frame-index)
          (write length-spec b length)
          (.writerIndex ^ByteBuf b (+ frame-index length)))))))

(defn- compile-field
  [[field-name field-spec]]
  [field-name (spec field-spec)])

(defn map-spec
  [s]
  (let [props (map-props s)
        length-spec (some-> props :length-based-frame spec)
        compiled-fields (mapv compile-field (map-fields s))
        data-spec (reify
                    Spec
                    (read [_ b]
                      (into {}
                            (map (fn [[field-name field-spec]]
                                   [field-name (read field-spec b)]))
                            compiled-fields))
                    (write [_ b v]
                      (run! (fn [[field-name field-spec]]
                              (write field-spec b (clojure.core/get v field-name)))
                            compiled-fields)))]
    (if length-spec
      (length-based-frame-spec length-spec data-spec)
      data-spec)))

(defn tuple-spec
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

(defn fixed-length-vector-spec
  [length item-spec]
  (let [item-spec (spec item-spec)]
    (reify
      Spec
      (read [_ b]
        (vec (repeatedly length #(read item-spec ^ByteBuf b))))
      (write [_ b v]
        (run! #(write item-spec b %) v)))))

(defn length-delimited-vector-spec
  [delimiter-spec item-spec offset]
  (let [offset (or offset 0)]
    (reify
      Spec
      (read [_ b]
        (let [len (- (read delimiter-spec b) offset)]
          (if (< len 0)
            nil
            (vec (repeatedly len #(read item-spec ^ByteBuf b))))))
      (write [_ b v]
        (if (nil? v)
          (write delimiter-spec b (dec offset))
          (do (write delimiter-spec b (+ (count v) offset))
              (run! #(write item-spec b %) v)))))))

(defn vector-spec
  [[_ length item-spec offset]]
  (cond
    (int? length)
    (fixed-length-vector-spec length item-spec)

    :else
    (length-delimited-vector-spec (spec length) (spec item-spec) offset)))

(defn- read-bytes
  [^ByteBuf b length]
  (let [byts (byte-array length)]
    (.readBytes ^ByteBuf b byts)
    byts))

(defn- write-bytes
  [^ByteBuf b byts]
  (.writeBytes ^ByteBuf b (bytes byts)))

(defn- read-string
  [^ByteBuf b length]
  (.readCharSequence ^ByteBuf b length StandardCharsets/UTF_8))

(defn- write-string
  [^ByteBuf b str]
  (.writeBytes ^ByteBuf b (.getBytes ^String str StandardCharsets/UTF_8)))

(defn- fixed-length-spec
  [length read write]
  (reify
    Spec
    (read [_ b]
      (read b length))
    (write [_ b v]
      (write b v))))

(defn- fixed-length-bytes-spec
  [length]
  (fixed-length-spec length read-bytes write-bytes))

(defn- fixed-length-string-spec
  [length]
  (fixed-length-spec length read-string write-string))

(defn- length-delimited-bytes-spec
  [delimiter-spec offset]
  (let [offset (or offset 0)]
    (reify
      Spec
      (read [_ b]
        (let [length (- (read delimiter-spec b) offset)]
          (if (< length 0)
            nil
            (read-bytes b length))))
      (write [_ b byts]
        (if (nil? byts)
          (write delimiter-spec ^ByteBuf b (dec offset))
          (do (write delimiter-spec ^ByteBuf b (+ (alength (bytes byts)) offset))
              (write-bytes b byts)))))))

(defn- length-delimited-string-spec
  [delimiter-spec offset]
  (let [offset (or offset 0)]
    (reify
      Spec
      (read [_ b]
        (let [length (- (read delimiter-spec b) offset)]
          (if (< length 0)
            nil
            (read-string b length))))
      (write [_ b v]
        (if (nil? v)
          (write delimiter-spec ^ByteBuf b (dec offset))
          (let [byts (.getBytes ^String v StandardCharsets/UTF_8)]
            (write delimiter-spec ^ByteBuf b (+ (alength byts) offset))
            (write-bytes b byts)))))))

(defn- length-spec-compiler
  [fixed-length-spec-fn length-delimited-spec-fn]
  (fn length-delimited-spec
    [[_ length offset]]
    (cond
      (int? length)
      (fixed-length-spec-fn length)

      :else
      (length-delimited-spec-fn (spec length) offset))))

(def string-spec (length-spec-compiler fixed-length-string-spec length-delimited-string-spec))

(def bytes-spec (length-spec-compiler fixed-length-bytes-spec length-delimited-bytes-spec))

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
   :tuple tuple-spec
   :string string-spec
   :bytes bytes-spec})

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
