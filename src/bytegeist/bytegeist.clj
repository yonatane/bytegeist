(ns bytegeist.bytegeist
  (:refer-clojure :exclude [byte double float read read-string])
  (:import (io.netty.buffer ByteBuf)
           (java.nio.charset StandardCharsets)))

(declare spec read write)

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
                (conj r [fname (get final-types fname)]))))
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

(defprotocol ToSpec
  (-to-spec [_]))

(defprotocol Spec
  (-spec? [_])
  (-read [_ b] "Relative read and increment the index")
  (-write [_ b v] "Relative write and increment the index"))

(defprotocol MapSpec
  (-properties [_])
  (-fields [_]))

(def bool
  (reify
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (.readBoolean ^ByteBuf b))
    (-write [_ b v]
      (.writeBoolean ^ByteBuf b (boolean v)))))

(def byte
  (reify
    Spec
    (-read [_ b]
      (.readByte ^ByteBuf b))
    (-write [_ b v]
      (.writeByte ^ByteBuf b (unchecked-int v)))))

(def int16
  (reify
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (.readShort ^ByteBuf b))
    (-write [_ b v]
      (.writeShort ^ByteBuf b (unchecked-int v)))))

(def int24
  (reify
    Spec
    (-read [_ b]
      (.readMedium ^ByteBuf b))
    (-write [_ b v]
      (.writeMedium ^ByteBuf b (unchecked-int v)))))

(def int32
  (reify
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (.readInt ^ByteBuf b))
    (-write [_ b v]
      (.writeInt ^ByteBuf b (unchecked-int v)))))

(def int64
  (reify
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (.readLong ^ByteBuf b))
    (-write [_ b v]
      (.writeLong ^ByteBuf b (unchecked-long v)))))

(def double
  (reify
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (.readDouble ^ByteBuf b))
    (-write [_ b v]
      (.writeDouble ^ByteBuf b (unchecked-double v)))))

(def float
  (reify
    Spec
    (-read [_ b]
      (.readFloat ^ByteBuf b))
    (-write [_ b v]
      (.writeFloat ^ByteBuf b (unchecked-float v)))))

(def ubyte
  (reify
    Spec
    (-read [_ b]
      (.readUnsignedByte ^ByteBuf b))
    (-write [_ b v]
      (.writeByte ^ByteBuf b (unchecked-int v)))))

(def uint32
  (reify
    Spec
    (-read [_ b]
      (.readUnsignedInt ^ByteBuf b))
    (-write [_ b v]
      (.writeInt ^ByteBuf b (unchecked-int v)))))

(def uvarint32
  (reify
    Spec
    (-read [_ b]
      (bytegeist.protobuf.Util/readUnsignedVarint32 ^ByteBuf b))
    (-write [_ b v]
      (bytegeist.protobuf.Util/writeUnsignedVarint32 ^ByteBuf b (unchecked-int v)))))

(declare spec)

(defn- length-based-frame-spec [length-spec data-spec]
  (reify
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (some-> length-spec (-read b))
      (-read data-spec b))
    (-write [_ b v]
      (let [frame-index (.writerIndex ^ByteBuf b)
            _ (-write length-spec b 0)
            length-length (- (.writerIndex ^ByteBuf b) frame-index)]
        (write data-spec b v)
        (let [data-length (- (.writerIndex ^ByteBuf b) frame-index length-length)]
          (.writerIndex ^ByteBuf b frame-index)
          (-write length-spec b data-length)
          (.writerIndex ^ByteBuf b (+ frame-index length-length data-length)))))))

(defn- compile-field
  [field]
  (if (= 2 (count field))
    (let [[field-name field-spec] field]
      [field-name nil (spec field-spec)])
    (let [[field-name field-props field-spec] field]
      [field-name field-props (spec field-spec)])))

(defn map-spec
  [s]
  (let [props (map-props s)
        length-spec (some-> props :length spec)
        fields-data (map-fields s)
        compiled-fields (mapv compile-field fields-data)
        data-spec (reify
                    ToSpec
                    (-to-spec [this] this)
                    MapSpec
                    (-properties [_] props)
                    (-fields [_] compiled-fields)
                    Spec
                    (-read [_ b]
                      (into {}
                            (mapcat (fn [[field-name field-props field-spec]]
                                      (if (:inline field-props)
                                        (-read field-spec b)
                                        [[field-name (-read field-spec b)]])))
                            compiled-fields))
                    (-write [_ b v]
                      (run! (fn [[field-name field-props field-spec :as field]]
                              (try
                                (if (:inline field-props)
                                  (-write field-spec b v)
                                  (-write field-spec b (get v field-name)))
                                (catch Exception e
                                  (throw (ex-info "Failed write" {:field field :v v} e)))))
                            compiled-fields)))]
    (if length-spec
      (length-based-frame-spec length-spec data-spec)
      data-spec)))

(defn map-of-spec
  [[_ {:keys [length adjust]} k-spec v-spec]]
  (let [length-spec (when (not (int? length)) (spec length))
        adjust (or adjust 0)
        k-spec (spec k-spec)
        v-spec (spec v-spec)]
    (if length-spec
      (reify
        Spec
        (-read [_ b]
          (let [len (- (-read length-spec b) adjust)]
            (if (< len 0)
              nil
              (into {} (repeatedly len #(vector (-read k-spec b) (-read v-spec b)))))))
        (-write [_ b m]
          (if (nil? m)
            (-write length-spec b (dec adjust))
            (do (write length-spec b (+ (count m) adjust))
                (run! (fn [[k v]]
                        (-write k-spec b k)
                        (-write v-spec b v))
                      m)))))
      (reify
        Spec
        (-read [_ b]
          (let [len length]
            (into {} (repeatedly len #(vector (-read k-spec b) (-read v-spec b))))))
        (-write [_ b m]
          (run! (fn [[k v]]
                  (-write k-spec b k)
                  (-write v-spec b v))
                m))))))

(defn tuple-spec
  [s]
  (let [specs (mapv spec (rest s))]
    (reify
      ToSpec
      (-to-spec [this] this)
      Spec
      (-read [_ b]
        (mapv (fn [item-spec] (-read item-spec b)) specs))
      (-write [_ b v]
        (loop [i 0]
          (when (< i (count specs))
            (let [item-spec (nth specs i) item (nth v i)]
              (-write item-spec b item)
              (recur (inc i)))))))))

(defn fixed-length-vector-spec
  [length item-spec]
  (let [item-spec (spec item-spec)]
    (reify
      ToSpec
      (-to-spec [this] this)
      Spec
      (-read [_ b]
        (vec (repeatedly length #(-read item-spec ^ByteBuf b))))
      (-write [_ b v]
        (run! #(-write item-spec b %) v)))))

(defn length-delimited-vector-spec
  [delimiter-spec item-spec adjust]
  (let [adjust (or adjust 0)]
    (reify
      ToSpec
      (-to-spec [this] this)
      Spec
      (-read [_ b]
        (let [len (- (-read delimiter-spec b) adjust)]
          (if (< len 0)
            nil
            (vec (repeatedly len #(-read item-spec ^ByteBuf b))))))
      (-write [_ b v]
        (if (nil? v)
          (write delimiter-spec b (dec adjust))
          (do (write delimiter-spec b (+ (count v) adjust))
              (run! #(-write item-spec b %) v)))))))

(defn vector-spec
  [[_ {:keys [length adjust]} item-spec]]
  (cond
    (int? length)
    (fixed-length-vector-spec length item-spec)

    :else
    (length-delimited-vector-spec (spec length) (spec item-spec) adjust)))

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
    ToSpec
    (-to-spec [this] this)
    Spec
    (-read [_ b]
      (read b length))
    (-write [_ b v]
      (write b v))))

(defn- fixed-length-bytes-spec
  [length]
  (fixed-length-spec length read-bytes write-bytes))

(defn- fixed-length-string-spec
  [length]
  (fixed-length-spec length read-string write-string))

(defn- length-delimited-bytes-spec
  [delimiter-spec adjust]
  (let [adjust (or adjust 0)]
    (reify
      ToSpec
      (-to-spec [this] this)
      Spec
      (-read [_ b]
        (let [length (- (-read delimiter-spec b) adjust)]
          (if (< length 0)
            nil
            (read-bytes b length))))
      (-write [_ b byts]
        (if (nil? byts)
          (write delimiter-spec ^ByteBuf b (dec adjust))
          (do (-write delimiter-spec ^ByteBuf b (+ (alength (bytes byts)) adjust))
              (write-bytes b byts)))))))

(defn- length-delimited-string-spec
  [delimiter-spec adjust]
  (let [adjust (or adjust 0)]
    (reify
      ToSpec
      (-to-spec [this] this)
      Spec
      (-read [_ b]
        (let [length (- (-read delimiter-spec b) adjust)]
          (if (< length 0)
            nil
            (read-string b length))))
      (-write [_ b v]
        (if (nil? v)
          (-write delimiter-spec ^ByteBuf b (dec adjust))
          (let [byts (.getBytes ^String v StandardCharsets/UTF_8)]
            (-write delimiter-spec ^ByteBuf b (+ (alength byts) adjust))
            (write-bytes b byts)))))))

(defn- length-spec-compiler
  [fixed-length-spec-fn length-delimited-spec-fn]
  (fn length-delimited-spec
    [[_ {:keys [length adjust]}]]
    (cond
      (int? length)
      (fixed-length-spec-fn length)

      :else
      (length-delimited-spec-fn (spec length) adjust))))

(def string-spec (length-spec-compiler fixed-length-string-spec length-delimited-string-spec))

(def bytes-spec (length-spec-compiler fixed-length-bytes-spec length-delimited-bytes-spec))

(defn- positions
  [pred coll]
  (keep-indexed (fn [idx x]
                  (when (pred x)
                    idx))
                coll))

(defn- multi-spec
  [[_ {:keys [dispatch dispatch-fn] :or {dispatch-fn identity}} & children]]
  (let [cases (into {} (map (fn [[k v]] [k (spec v)])) children)
        first-spec (-> cases vals first)
        first-spec-fields (-fields first-spec)
        first-spec-props (or (-properties first-spec) {})
        field-pred (if (keyword? dispatch) #{dispatch} (set dispatch))
        last-dispatch-pos (last (positions field-pred (map first first-spec-fields)))
        fields-upto-dispatch (subvec first-spec-fields 0 (inc last-dispatch-pos))
        initial-reader (spec (into [:map first-spec-props] fields-upto-dispatch))
        dispatch-f (if (keyword? dispatch) (comp dispatch-fn dispatch) #(dispatch-fn (mapv % dispatch)))]
    (reify
      ToSpec
      (-to-spec [this] this)
      Spec
      (-read [_ b]
        (let [mark (.readerIndex ^ByteBuf b)
              initial (-read initial-reader b)
              _ (.readerIndex ^ByteBuf b mark)
              dispatch-val (dispatch-f initial)]
          (if-some [matched (get cases dispatch-val)]
            (-read matched b)
            (throw (ex-info "Invalid dispatch value" {:dispatch-options (vals cases)
                                                      :dispatch-value dispatch-val})))))

      (-write [_ b v]
        (let [dispatch-val (dispatch-f v)]
          (if-some [matched (get cases dispatch-val)]
            (write matched b v)
            (throw (ex-info "Invalid dispatch value" {:dispatch-options (vals cases)
                                                      :dispatch-value dispatch-val}))))))))

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
   :double double
   :float float
   :ubyte ubyte
   :uint32 uint32
   :uvarint32 uvarint32})

(def f-registry
  {:map map-spec
   :map-of map-of-spec
   :vector vector-spec
   :tuple tuple-spec
   :string string-spec
   :bytes bytes-spec
   :multi multi-spec})

(defn compile-spec-vector
  [s]
  (let [shape (nth s 0)
        f (or (get f-registry shape) (throw (ex-info "Unknown spec" {:input s})))]
    (f s)))

(extend-protocol ToSpec
  clojure.lang.Keyword
  (-to-spec [s]
    (or (get registry s) (throw (ex-info "Unknown spec" {:input s}))))
  clojure.lang.IPersistentVector
  (-to-spec [s]
    (compile-spec-vector s))
  Object
  (-to-spec [s]
    (throw (ex-info "Unsupported spec input type" {:input s}))))

(defn spec [s]
  (-to-spec s)
  #_(cond
      (satisfies? Spec s)
      s

      (keyword? s)
      (or (get registry s) (throw (ex-info "Unknown spec" {:input s})))

      (vector? s)
      (compile-spec-vector s)

      :else
      (throw (ex-info "Unsupported spec input type" {:input s}))))

(defn read [s b]
  (-read (-to-spec s) b))

(defn read-inline
  {:inline (fn [s b] `(-read (-to-spec ~s) ~b))}
  [s b]
  (-read (-to-spec s) b))


(defn write [s b v]
  (-write (-to-spec s) b v))
