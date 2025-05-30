(ns type-schema.sanity
  (:require
   [clojure.set :as set]))

(defn find-unresolvable-deps [type-schemas]
  (let [schema-ids (->> type-schemas
                        (map :identifier)
                        (into #{}))
        all-deps (->> type-schemas
                      (keep :dependencies)
                      (apply concat)
                      ;; defined as a part of mother schema
                      (remove #(-> % :kind (= "nested")))
                      (into #{}))]
    (set/difference all-deps schema-ids)))
