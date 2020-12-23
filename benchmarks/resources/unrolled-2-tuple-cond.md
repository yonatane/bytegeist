When the tuple spec is the same in all invocations, cond is as good as the 2-tuple unrolled version.

Still requires variation in specs between invocations. 

```clojure
({:args (:state/tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 0,
  :name :rolled-2-tuple,
  :mode :throughput,
  :params {:buf_size 16, :tuple [:tuple :long :long], :v [1 2]},
  :threads 1,
  :score [2.6506626243603908E7 "ops/s"]}
 {:args (:state/tuple :state/buf),
  :samples 1,
  :fn bytegeist.bytegeist/-read,
  :index 0,
  :name :rolled-2-tuple,
  :mode :throughput,
  :params {:buf_size 16, :tuple [:tuple :long :double], :v [1 2]},
  :threads 1,
  :score [2.5922457702928804E7 "ops/s"]})
```
