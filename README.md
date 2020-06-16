# bytegeist

Binary protocol specs for clojure

WIP

```clojure
[bytegeist "0.1.0-SNAPSHOT"]
```

### Current goals

1. Flexible data-driven specs for byte codecs
2. Support multiple protocol versions easily by basing one version on another
3. Protobuf compatibility without generating code, emphasizing rapid prototyping over performance.

### Examples

[The tests](test/bytegeist/bytegeist_test.clj) contain the most updated examples.

[Skazka (Kafka proxy)](https://github.com/yonatane/skazka/blob/851873f7a75b9c37f3313d041c4caeddfafa9db0/src/skazka/protocol.clj#L1)
implements some of the kafka protocol.

Reading Metadata V9 kafka response:

```clojure
(require '[bytegeist.bytegeist :as g])

(def compact-string
  "String with unsigned varint length delimiter set to N+1 (N is number of bytes).
  N=0 means empty string \"\". N=-1 means nil"
  (g/spec [:string {:length :uvarint32, :adjust 1}]))

(defn compact-array
  "Array with unsigned varint length delimiter set to N+1 (N is number of items).
  N=0 means empty vector `[]`. N=-1 means nil"
  [s]
  (g/spec [:vector {:length :uvarint32, :adjust 1} s]))

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

### Multi spec
You can select a spec at runtime according to any field,
as long as all fields up to, and including that field, are the same.

```clojure
(def person
  (g/spec
    [:multi {:dispatch :type}
     ["student"
      [:map
       [:type [:string {:length :short}]]
       [:grade :short]]]
     ["employee"
      [:map
       [:type [:string {:length :short}]]
       [:salary :int]]]]))

(g/write person buffer {:type "employee"
                        :salary 99999})

(g/write person buffer {:type "student"
                        :grade 100})
```

### Acknowledgements

[Funcool/octet](https://github.com/funcool/octet) was used before bytegeist and the internals draw from it.

[Metosin/malli](https://github.com/metosin/malli) defines the schema notation bytegeist adopted.

![YourKit](https://www.yourkit.com/images/yklogo.png)<br>
YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>.

## License

Copyright © 2020 Yonatan Elhanan

Distributed under the MIT License
