We can see that using transient similarly to mapv causes similar performance to mapv,
so the benefits of unrolling come from not using this transient, and not from splitting the callsites.

```clojure
({:args (:state/tuple :state/buf),
  :samples 5,
  :fn bytegeist.bytegeist/-read,
  :score-error 516107.2064302273,
  :index 0,
  :score-confidence [7077575.377791067 8109789.790651522],
  :name :rolled-2-tuple,
  :mode :throughput,
  :params {:buf_size 16, :tuple [:tuple :long :long], :v [1 2]},
  :statistics
  {:max 7749633.033373465,
   :mean 7593682.584221294,
   :min 7414335.350945694,
   :n 5,
   :percentiles
   {0.0 7414335.350945694,
    50.0 7645706.48533896,
    90 7749633.033373465,
    95 7749633.033373465,
    99 7749633.033373465,
    99.9 7749633.033373465,
    99.99 7749633.033373465,
    99.999 7749633.033373465,
    99.9999 7749633.033373465,
    100 7749633.033373465},
   :stdev 134031.40254126,
   :sum 3.796841292110647E7,
   :variance 1.796441686717728E10},
  :threads 1,
  :score [7593682.584221294 "ops/s"]}
 {:args (:state/tuple :state/buf),
  :samples 5,
  :fn bytegeist.bytegeist/-read,
  :score-error 555983.5085531995,
  :index 0,
  :score-confidence [7003313.047135351 8115280.06424175],
  :name :rolled-2-tuple,
  :mode :throughput,
  :params {:buf_size 16, :tuple [:tuple :double :double], :v [1 2]},
  :statistics
  {:max 7801579.5702805715,
   :mean 7559296.555688551,
   :min 7443420.652266345,
   :n 5,
   :percentiles
   {0.0 7443420.652266345,
    50.0 7539614.648921129,
    90 7801579.5702805715,
    95 7801579.5702805715,
    99 7801579.5702805715,
    99.9 7801579.5702805715,
    99.99 7801579.5702805715,
    99.999 7801579.5702805715,
    99.9999 7801579.5702805715,
    100 7801579.5702805715},
   :stdev 144387.1515699718,
   :sum 3.7796482778442755E7,
   :variance 2.084764953849001E10},
  :threads 1,
  :score [7559296.555688551 "ops/s"]}
 {:args (:state/tuple :state/buf),
  :samples 5,
  :fn bytegeist.bytegeist/-read,
  :score-error 260381.75001585396,
  :index 0,
  :score-confidence [1.067727539925972E7 1.119803889929143E7],
  :name :rolled-2-tuple,
  :mode :throughput,
  :params {:buf_size 16, :tuple [:tuple :long :double], :v [1 2]},
  :statistics
  {:max 1.1025220832535444E7,
   :mean 1.0937657149275575E7,
   :min 1.084520372224445E7,
   :n 5,
   :percentiles
   {0.0 1.084520372224445E7,
    50.0 1.0941894511067118E7,
    90 1.1025220832535444E7,
    95 1.1025220832535444E7,
    99 1.1025220832535444E7,
    99.9 1.1025220832535444E7,
    99.99 1.1025220832535444E7,
    99.999 1.1025220832535444E7,
    99.9999 1.1025220832535444E7,
    100 1.1025220832535444E7},
   :stdev 67620.31360143526,
   :sum 5.468828574637788E7,
   :variance 4.572506811556451E9},
  :threads 1,
  :score [1.0937657149275575E7 "ops/s"]})
```