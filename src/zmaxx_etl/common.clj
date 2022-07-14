(ns zmaxx-etl.common)
(require 'clojure.edn)







(def configmy
  (clojure.edn/read-string (slurp "config/con.edn")))

(def config_mint
  (clojure.edn/read-string (slurp "config/mint.edn")))


