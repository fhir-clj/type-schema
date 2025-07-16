(ns type-schema.value-set
  (:require
   [type-schema.package-index :as index]))

(defn- extract-inner-concepts [concepts]
  (concat (map #(dissoc % :concept) concepts)
          (mapcat extract-inner-concepts (map :concept concepts))))

(defn extract-concepts-from-codesystem [system-url]
  (let [code-system (index/resource system-url)]
    (if (= (:resourceType code-system) "CodeSystem")
      (->> (:concept code-system)
           extract-inner-concepts
           (map #(assoc % :system system-url)))
      (throw (ex-info "not expanding non-CodeSystem"
                      {:type ::not-expand :system-url system-url})))))

(declare value-set->concepts-inner)

(defn- process-compose-item [compose-rule]
  (cond
    (:filter compose-rule)
    (throw (ex-info "not expanding complex filter rule"
                    {:type ::not-expand}))
    (:concept compose-rule)
    (let [system-url (:system compose-rule)]
      (map #(assoc % :system system-url)
           (:concept compose-rule)))
    :else
    (concat
     (:concept compose-rule)
     (extract-concepts-from-codesystem (:system compose-rule))
     ;; FIXME: check keyword spell
     (value-set->concepts-inner (:valueSet compose-rule)))))

(defn- process-include-exclude
  [items]
  (->> items
       (map #(process-compose-item %))
       (apply concat)))

(defn- value-set->concepts-inner
  "Resolve all codes for a ValueSet, handling includes and excludes.

  NOTE: Concept hierarchy is ignored."
  [value-set]
  (let [compose (:compose value-set)
        included-codes (process-include-exclude (:include compose))
        excluded-codes (process-include-exclude (:exclude compose))]
    (->> included-codes
         (remove (set excluded-codes))
         (mapv #(select-keys % [:system :code :display])))))

(defn value-set->concepts
  "Resolve all codes for a ValueSet, handling includes and excludes."
  [value-set]
  (try
    (value-set->concepts-inner value-set)
    (catch Exception e
      (if (= ::not-expand (:type (ex-data e)))
        nil
        (throw e)))))
