# bytegeist

Binary protocol specs for clojure

WIP

```clojure
[bytegeist "0.1.0-SNAPSHOT"]
```

### Current goals

1. Define the binary encoding of any protocol using plain data
2. Simply define multiple versions of a protocol without duplication or extra programming
3. Protobuf without code generation
4. Support NIO ByteBuffer, Netty ByteBuf and InputStream/OutputStream
5. Enable extensions for custom types and I/O
6. Fast

### Examples

[The tests](test/bytegeist/bytegeist_test.clj) contain the most updated examples.

[Skazka (Kafka proxy)](https://github.com/yonatane/skazka/blob/851873f7a75b9c37f3313d041c4caeddfafa9db0/src/skazka/protocol.clj#L1)
implements some of the kafka protocol.

Reading and writing simple data:

```clojure
(require '[bytegeist.bytegeist :as g])
(import '(io.netty.buffer ByteBuf Unpooled))
(def b (Unpooled/buffer))

;; Just an integer
(g/write :int32 b 2020)
(g/read :int32 b)
;=> 2020

;; A length-delimited string, first 2 bytes hold the length
(def username [:string {:length :short}])

(g/write username b "byteme72")
(g/read username b)
;=> "byteme72"
```

Maps:

```clojure
;; A map with predefined fields
(def user [:map
           [:username [:string {:length :short}]]
           [:year :int32]])
;; Or reuse previously defined types and also compile the spec once
(def user (g/spec [:map
                   [:username username]
                   [:year :int32]]))

(g/write user b {:username "byteme72"
                 :year 2020})
(g/read user b)
;=> {:username "byteme72", :year 2020}
```

Map-of:

```clojure
;; Map-of int to bytes, prefixed by the number of fields
(def tagged-fields
  (let [tag :uvarint32
        data [:bytes {:length :uvarint32}]]
    [:map-of {:length :uvarint32} tag data]))

(g/write tagged-fields b {0 (.getBytes "hello")
                          1 (byte-array 10000)})
(g/read tagged-fields b)
;=> {0 #object["[B" 0x667e83eb "[B@667e83eb"], 1 #object["[B" 0xed01c80 "[B@ed01c80"]}
```

### Multi spec

You can select a spec at runtime according to any field,
as long as all fields up to, and including that field, are the same.

```clojure
(def person
  [:multi {:dispatch :type}
   ["student"
    [:map
     [:type [:string {:length :short}]]
     [:grade :short]]]
   ["employee"
    [:map
     [:type [:string {:length :short}]]
     [:salary :int]]]])

(g/write person buf {:type "employee"
                     :salary 99999})

(g/write person buf {:type "student"
                     :grade 100})

(g/read person buf)
; => {:type "employee", :salary 99999}
(g/read person buf)
; => {:type "student", :grade 100}
```

Dispatch on multiple fields, with a function of those fields to determine the dispatch value:

```clojure
(def message
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
     [:client-id [:string {:length :uvarint32}]] ; A new field in versions 3 and up
     [:data [:string {:length :uvarint32}]]]]
   [[:fetch :old]
    [:map
     [:type [:string {:length :short}]]
     [:version :short]
     [:partitions [:vector {:length :uvarint32} :uvarint32]]]]])

(g/write message buf {:type "produce"
                      :version 3
                      :client-id "test client"
                      :data "test data"})

(g/read message buf)
; => {:type "produce", :version 3, :client-id "test client", :data "test data"}
```

### Built-in types

`:bool`
`:boolean`
`:byte`
`:int16`
`:short`
`:int24`
`:int32`
`:int`
`:int64`
`:long`
`:double`
`:float`
`:ubyte`
`:uint32`
`:uvarint32`
`:map`
`:map-of`
`:vector`
`:tuple`
`:string`
`:bytes`
`:multi`

### Acknowledgements

[Metosin/malli](https://github.com/metosin/malli) defines the schema notation bytegeist adopted.

[Funcool/octet](https://github.com/funcool/octet) was used before bytegeist.

![YourKit](https://www.yourkit.com/images/yklogo.png)<br>
YourKit supports open source projects with innovative and intelligent tools for monitoring and profiling Java applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>.

## License

Copyright © 2020 Yonatan Elhanan

Distributed under the MIT License
