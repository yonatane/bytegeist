# bytegeist

Binary protocol specs for clojure

WIP

```clojure
[bytegeist "0.1.0-SNAPSHOT"]
```

### Current goals

1. [Malli](https://github.com/metosin/malli)-like flexible data-driven specs for byte codecs
2. Support multiple protocol versions easily by basing one version on another
3. Protobuf compatibility without generating code, emphasizing rapid prototyping over performance.

### Examples

[The tests](test/bytegeist/bytegeist_test.clj) contain the most updated examples.

[Skaza (Kafka proxy)](https://github.com/yonatane/skazka/blob/851873f7a75b9c37f3313d041c4caeddfafa9db0/src/skazka/protocol.clj#L1)
implements some of the kafka protocol.

Reading Metadata V9 kafka response:

```clojure
(require '[bytegeist.bytegeist :as g])

(def compact-string
  "String with unsigned varint length delimiter set to N+1 (N is number of bytes).
  N=0 means empty string \"\". N=-1 means nil"
  (g/spec [:string :uvarint32 1]))

(defn compact-array
  "Array with unsigned varint length delimiter set to N+1 (N is number of items).
  N=0 means empty vector `[]`. N=-1 means nil"
  [s]
  (g/spec [:vector :uvarint32 s 1]))

(def tagged-fields
  (reify
    g/Spec
    (read [_ b]
      #_(impl-read))
    (write [_ b v]
      #_(impl-write))))

(def broker
  (g/spec [:map
           [:node-id :int32]
           [:host compact-string]
           [:port :int32]
           [:rack compact-string]
           [:tagged-fields tagged-fields]]))

(def brokers
  (compact-array broker))

(def topic
  #_(etc))

(def metadata-res-v9
  (g/spec [:map
           [:correlation-id :int32]
           [:header-tagged-fields tagged-fields]
           [:throttle-time-ms :int32]
           [:brokers brokers]
           [:cluster-id compact-string]
           [:controller-id :int32]
           [:topics (compact-array topic)]
           [:cluster-authorized-operations :int32]
           [:tagged-fields tagged-fields]]))
```

### Previous work

[Octet](https://github.com/funcool/octet) is a great tool and bytegeist basic internals draw from it.<br>

## License

Copyright © 2020 Yonatan Elhanan

Distributed under the MIT License
