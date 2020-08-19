We can see how unrolling tuples up to size 4 significantly improves reading
even when the tuples are chosen randomly on each invocation, even if we use cond on each read.

```clojure
({:args (:state/rand-unrolled-tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 0,
  :name :unrolled-tuple-cond-rand,
  :mode :throughput,
  :params
  {:buf_size 40,
   :init_tuple [:tuple :long :double :int :int24 :short],
   :tuples
   [[:tuple :long]
    [:tuple :long :double]
    [:tuple :long :double :int]
    [:tuple :long :double :int :int24]],
   :v [0 1.5 2 3 4]},
  :threads 1,
  :score [1.9808346294511642E7 "ops/s"]}
 {:args (:state/unrolled-n-tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 1,
  :name :unrolled-n-tuple-cond,
  :mode :throughput,
  :params
  {:buf_size 40,
   :init_tuple [:tuple :long :double :int :int24 :short],
   :n 0,
   :tuples
   [[:tuple :long]
    [:tuple :long :double]
    [:tuple :long :double :int]
    [:tuple :long :double :int :int24]],
   :v [0 1.5 2 3 4]},
  :threads 1,
  :score [2.8050441363271646E7 "ops/s"]}
 {:args (:state/unrolled-n-tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 1,
  :name :unrolled-n-tuple-cond,
  :mode :throughput,
  :params
  {:buf_size 40,
   :init_tuple [:tuple :long :double :int :int24 :short],
   :n 1,
   :tuples
   [[:tuple :long]
    [:tuple :long :double]
    [:tuple :long :double :int]
    [:tuple :long :double :int :int24]],
   :v [0 1.5 2 3 4]},
  :threads 1,
  :score [2.577373316548883E7 "ops/s"]}
 {:args (:state/unrolled-n-tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 1,
  :name :unrolled-n-tuple-cond,
  :mode :throughput,
  :params
  {:buf_size 40,
   :init_tuple [:tuple :long :double :int :int24 :short],
   :n 2,
   :tuples
   [[:tuple :long]
    [:tuple :long :double]
    [:tuple :long :double :int]
    [:tuple :long :double :int :int24]],
   :v [0 1.5 2 3 4]},
  :threads 1,
  :score [2.2634448334943667E7 "ops/s"]}
 {:args (:state/unrolled-n-tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 1,
  :name :unrolled-n-tuple-cond,
  :mode :throughput,
  :params
  {:buf_size 40,
   :init_tuple [:tuple :long :double :int :int24 :short],
   :n 3,
   :tuples
   [[:tuple :long]
    [:tuple :long :double]
    [:tuple :long :double :int]
    [:tuple :long :double :int :int24]],
   :v [0 1.5 2 3 4]},
  :threads 1,
  :score [2.0049453683696248E7 "ops/s"]}
 {:args (:state/unrolled-5-tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 2,
  :name :unrolled-5-tuple-cond,
  :mode :throughput,
  :params
  {:buf_size 40,
   :five_tuple [:tuple :long :double :int :int24 :short],
   :init_tuple [:tuple :long :double :int :int24 :short],
   :v [0 1.5 2 3 4]},
  :threads 1,
  :score [4315861.253437471 "ops/s"]})
```
