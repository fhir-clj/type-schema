(ns type-schema.sanity
  (:require
   [clojure.set :as set]))

(defn find-unresolvable-deps [type-schemas]
  (let [schema-ids (->> type-schemas
                        (map :identifier)
                        (into #{}))
        all-deps (->> type-schemas
                      (map :dependencies)
                      (apply concat)
                      ;; defined as a part of mother schema
                      (remove #(-> % :kind (= "nested")))
                      ;; (remove #(-> % :kind (= "binding")))
                      (into #{}))]
    (set/difference all-deps schema-ids)))
