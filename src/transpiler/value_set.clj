(ns transpiler.value-set)

(defn- concat-concepts [& results]
  (cond
    (empty? results) nil

    (->> results
         (filter #(= % ::not-expand))
         first
         some?) ::not-expand

    :else (apply concat results)))

(defn- extract-codes-from-system [package-index system-url]
  (let [code-system (get package-index system-url)]
    (if (= (:resourceType code-system) "CodeSystem")
      (->> (:concept code-system)
           (map #(assoc % :system system-url)))
      ::not-expand)))

(declare value-set->concepts-inner)

(defn process-include-exclude-item [package-index system-or-value-set]
  (cond
    (:filter system-or-value-set) ::not-expand

    :else
    (concat-concepts
     (:concept system-or-value-set)
     (extract-codes-from-system package-index (:system system-or-value-set))
     ;; FIXME: check keyword spell
     (value-set->concepts-inner package-index (:valueSet system-or-value-set)))))

(defn process-include-exclude
  "Process include or exclude elements to extract codes."
  [package-index items]
  (->> items
       (map #(process-include-exclude-item package-index %))
       (apply concat-concepts)))

(defn- value-set->concepts-inner
  "Resolve all codes for a ValueSet, handling includes and excludes."
  [package-index value-set]
  (let [compose (:compose value-set)
        included-codes (process-include-exclude package-index (:include compose))
        excluded-codes (process-include-exclude package-index (:exclude compose))]
    (if (or (= included-codes ::not-expand)
            (= excluded-codes ::not-expand))
      ::not-expand
      (->> (remove (set excluded-codes)
                   included-codes)
           (map #(select-keys % [:system :code :display]))
           (into [])))))

(defn value-set->concepts
  "Resolve all codes for a ValueSet, handling includes and excludes."
  [package-index value-set]
  (let [concepts (value-set->concepts-inner package-index value-set)]
    (when-not (= ::not-expand concepts)
      concepts)))
