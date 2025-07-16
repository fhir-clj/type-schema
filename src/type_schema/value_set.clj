(ns type-schema.value-set
  (:require
   [type-schema.package-index :as index]))

(defn- concat-concepts [& results]
  (cond
    (empty? results) nil

    (->> results
         (filter #(= % ::not-expand))
         first
         some?) ::not-expand

    :else (apply concat results)))

(defn- extract-inner-concepts [concepts]
  (concat (map #(dissoc % :concept) concepts)
          (mapcat extract-inner-concepts (map :concept concepts))))

(defn extract-concepts-from-codesystem [system-url]
  (let [code-system (index/resource system-url)]
    (if (= (:resourceType code-system) "CodeSystem")
      (->> (:concept code-system)
           extract-inner-concepts
           (map #(assoc % :system system-url)))
      ::not-expand)))

(declare value-set->concepts-inner)

(defn- process-compose-item [compose-rule]
  (cond
    (:filter compose-rule) ::not-expand
    (:concept compose-rule)
    (let [system-url (:system compose-rule)]
      (map #(assoc % :system system-url)
           (:concept compose-rule)))
    :else
    (concat-concepts
     (:concept compose-rule)
     (extract-concepts-from-codesystem (:system compose-rule))
     ;; FIXME: check keyword spell
     (value-set->concepts-inner (:valueSet compose-rule)))))

(defn- process-include-exclude
  [items]
  (->> items
       (map #(process-compose-item %))
       (apply concat-concepts)))

(defn- value-set->concepts-inner
  "Resolve all codes for a ValueSet, handling includes and excludes.

  NOTE: Concept hierarchy is ignored."
  [value-set]
  (let [compose (:compose value-set)
        included-codes (process-include-exclude (:include compose))
        excluded-codes (process-include-exclude (:exclude compose))]
    (if (or (= included-codes ::not-expand)
            (= excluded-codes ::not-expand))
      ::not-expand
      (->> included-codes
           (remove (set excluded-codes))
           (mapv #(select-keys % [:system :code :display]))))))

(defn value-set->concepts
  "Resolve all codes for a ValueSet, handling includes and excludes."
  [value-set]
  (let [concepts (value-set->concepts-inner value-set)]
    (when-not (= ::not-expand concepts)
      concepts)))
