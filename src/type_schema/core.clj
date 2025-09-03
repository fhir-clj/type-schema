(ns type-schema.core
  (:require
   [clojure.data :as data]
   [type-schema.binding :as binding]
   [type-schema.identifier :as identifier]
   [type-schema.log :as log]
   [type-schema.package-index :as package]
   [type-schema.value-set :as value-set]))

(defn ensure-url [type-name]
  (if (re-matches #".*/.*" type-name)
    type-name
    (or (:url (package/fhir-schema type-name))
        (str "http://hl7.org/fhir/StructureDefinition/" type-name))))

(defn fhir-schema-parent [fhir-schema]
  (some-> fhir-schema :base (package/fhir-schema)))

(defn fhir-schema-specialization-parent [fhir-schema]
  (let [p (fhir-schema-parent fhir-schema)]
    (assert (some? p) "Expect parent with 'specialization' derivation")
    (if (= "specialization" (:derivation p))
      p
      (fhir-schema-specialization-parent p))))

(defn fhir-schema-hierarchy [fhir-schema]
  (if (nil? fhir-schema)
    nil
    (cons fhir-schema
          (fhir-schema-hierarchy (some-> fhir-schema
                                         :base
                                         (package/fhir-schema))))))

(defn element-hierarchy [fhir-schema path]
  (->> (fhir-schema-hierarchy fhir-schema)
       (keep (fn [fs]
               (get-in fs
                       (->> path
                            (map (fn [elem-name] [:elements elem-name]))
                            (apply concat)))))))

;; TODO: Current way (select last word) to merge elements into snapshor is
;; incorrects. E.g.:
;;
;; (package/load-fhir-schema! {:url      "A"
;;                             :elements {:foo {:type  "string"
;;                                              :array true
;;                                              :min   2}}})
;; (package/load-fhir-schema! {:base     "A"
;;                             :url      "B"
;;                             :required ["foo"]
;;                             :elements {:foo {:min 1
;;                                              :max 2}
;;                                        :bar {:type "code"}}})
;;
;; We should select most specific elements props. Two ways to do that:
;;
;; - merge snapshots should be very smart
;; - avoid direct access to elements, prefer to use specific getter like for `is-required?`

(defn element-snapshot [fhir-schema path]
  (let [whitelist      [:choices :short :index :elements :required :excluded
                        :binding ;; TODO: check and fixit
                        :refers :elementReference
                        :mustSupport
                            ;; FIXME: Should not be presented in accordance to fhir schema spec
                        :slices :slicing
                        :url :extensions]
        elem-hierarchy (element-hierarchy fhir-schema path)
        snapshot       (->> elem-hierarchy (reverse) (apply merge))
        rev-snapshot   (->> elem-hierarchy (apply merge))
        [a b _ab]      (data/diff (apply dissoc rev-snapshot whitelist)
                                  (apply dissoc snapshot whitelist))]
    (when (or (not (empty? a)) (not (empty? b)))
      (log/warn :duplicate-elements-in-hierarchy
                (str "Duplicate elements in hierarchy for " (:name fhir-schema) " at path " path
                     " diff " a b)))

    snapshot))

(defn- is-prop-constrained? [fhir-schema path prop-key]
  (let [prop-path (conj (->> (drop-last path)
                             (map (fn [k] [:elements k]))
                             (apply concat)
                             (vec))
                        prop-key)
        required (->> (fhir-schema-hierarchy fhir-schema)
                      (mapcat #(get-in % prop-path))
                      (set))
        elem-name (name (last path))]
    (contains? required elem-name)))

(defn- is-required? [fhir-schema path]
  (is-prop-constrained? fhir-schema path :required))

(defn- is-excluded? [fhir-schema path]
  (is-prop-constrained? fhir-schema path :excluded))

(defn build-reference [el-snapshot]
  (when (:refers el-snapshot)
    (let [references (get-in el-snapshot [:refers])]
      (->> references
           (map (fn [refer]
                  (if-let [fhir-schema (package/fhir-schema refer)]
                    (identifier/schema-type fhir-schema)
                    (identifier/schema-type {:url  refer
                                             :name refer}))))))))

(defn remove-empty-vals [m]
  (->> m
       (remove (fn [[_ v]]
                 (when (seqable? v)
                   (empty? v))))
       (into {})))

(defn build-field-type [fhir-schema _path el-snapshot]
  (or (some-> (or (some-> el-snapshot :type (ensure-url))
                  (:defaultType el-snapshot))
              (package/fhir-schema)
              (identifier/schema-type))

      (when-let [fhir-schema-path (:elementReference el-snapshot)]
        (identifier/nested-type fhir-schema
                                (->> fhir-schema-path
                                     (drop 1)
                                     (keep-indexed (fn [i key]
                                                     (when (odd? i)
                                                       key)))

                                     (map keyword)
                                     (into []))))

      #_(when (and (= (:kind fhir-schema) "logical")
                   (nil? (:type element))
                   (not (contains? element :choices)))
          (log/warn :force-default-type-for-logic-model (:name fhir-schema) path element)
          (-> (package/fhir-schema "string")
              (identifier/schema-type)))))

(defn build-field [fhir-schema path el-snapshot]
  (remove-empty-vals
   {:type     (build-field-type fhir-schema path el-snapshot)
    :array    (true? (:array el-snapshot))
    :required (is-required? fhir-schema path)
    :excluded (is-excluded? fhir-schema path)

    :min (:min el-snapshot)
    :max (:max el-snapshot)

    :choices  (:choices el-snapshot)
    :choiceOf (:choiceOf el-snapshot)

    :enum      (binding/build-enum el-snapshot)
    :binding   (binding/build-binding fhir-schema path el-snapshot)
    :reference (build-reference el-snapshot)}))

(defn build-nested-field [fhir-schema path el-snapshot]
  {:type     (identifier/nested-type fhir-schema path)
   :array    (true? (:array el-snapshot))
   :required (is-required? fhir-schema path)
   :excluded (is-excluded? fhir-schema path)})

(defn is-nested-element? [element]
  (or (= (:type element) "BackboneElement")
      (< 0 (count (:elements element)))))

(defn iterate-over-elements [fhir-schema path elements]
  (->> elements
       (reduce (fn [m [key element]]
                 (let [el-snapshot (element-snapshot fhir-schema (conj path key))
                       m           (update m key merge element)]
                   (reduce (fn [m2 choice-key]
                             (update m2 (keyword choice-key) merge {}))
                           m
                           (:choices el-snapshot))))

               {})
       (map (fn [[key _element]]
              (let [path        (conj path key)
                    el-snapshot (element-snapshot fhir-schema path)
                    constraint? (identifier/is-constraint? fhir-schema)
                    nested?     (is-nested-element? el-snapshot)]
                (cond
                  (and constraint? nested?)
                  (let [parent (fhir-schema-specialization-parent fhir-schema)]
                    (assert (some? parent)
                            (str "Constraint must have base to build nested type for "
                                 (:name fhir-schema) " at path " path))
                    [key (build-nested-field parent path el-snapshot)])

                  nested?
                  [key (build-nested-field fhir-schema path el-snapshot)]

                  :else
                  [key (build-field fhir-schema path el-snapshot)]))))
       (into {})))

(defn collect-nested-elements [fhir-schema path elements]
  (->> elements
       (map (fn [[key element]]
              (let [path (conj path key)
                    nested (when (is-nested-element? element)
                             (collect-nested-elements fhir-schema path (:elements element)))]
                (cons [path element] nested))))
       (apply concat)
       (into [])))

(defn iterate-over-backbone-element [fhir-schema path elements]
  (->> (collect-nested-elements fhir-schema path elements)
       (filter (fn [[_path element]] (is-nested-element? element)))
       (map (fn [[path element]]
              {:identifier (identifier/nested-type fhir-schema path)
               :base       (some-> (ensure-url "BackboneElement")
                                   (package/fhir-schema)
                                   (identifier/schema-type))
               :fields     (iterate-over-elements fhir-schema path (:elements element))}))
       (into [])))

(defn extract-dependencies [fields]
  (concat (->> fields
               (remove (fn [[_key element]] (= "nested" (get-in element [:type :kind]))))
               (keep (fn [[_key element]] (:type element))))
          (->> fields
               (keep (fn [[_key element]] (get-in element [:binding]))))))

(defn extract-dependencies-from-nested [nested-types]
  (concat
   (->> nested-types
        (keep (fn [nested-type]
                (extract-dependencies (:fields nested-type))))
        (apply concat))
   (when-let [base (get-in (first nested-types) [:base])]
     [base])))

(defn collect-binding-schemas
  "Collect all binding schemas from a FHIR schema"
  [fhir-schema deep-nested-elements]
  (->> (deep-nested-elements fhir-schema [] (:elements fhir-schema))
       (keep (fn [[path _element]]
               (let [el-snapshot (element-snapshot fhir-schema path)]
                 (when (some? (:binding el-snapshot))
                   (binding/generate-binding-type fhir-schema path el-snapshot
                                                  (build-field-type fhir-schema path el-snapshot))))))
       (sort-by #(get-in % [:identifier :name]))
       (distinct)))

(defn translate-fhir-schema [fhir-schema]
  (let [parent      (fhir-schema-parent fhir-schema)

        identifier  (identifier/schema-type fhir-schema)
        constraint? (identifier/is-constraint? fhir-schema)
        base        (some-> parent (identifier/schema-type))
        description (:description fhir-schema)

        elements    (:elements fhir-schema)
        fields      (iterate-over-elements fhir-schema [] elements)

        nested      (when-not constraint?
                      (->> (iterate-over-backbone-element fhir-schema [] elements)
                           (sort-by #(-> % :identifier :url))))

        binding-type-schemas
        (collect-binding-schemas fhir-schema collect-nested-elements)

        depends
        (->> (concat (when base [base])
                     (extract-dependencies fields)
                     (when-not constraint?
                       (extract-dependencies-from-nested nested))
                     (when constraint?
                       (let [p (fhir-schema-specialization-parent fhir-schema)]
                         (->> (iterate-over-backbone-element p [] (:elements p))
                              (sort-by #(-> % :identifier :url))
                              (map :identifier)))))
             (distinct)
             (sort-by #(get-in % [:name]))
             (remove #(-> % :name (= (:name identifier))))
             (into []))

        resource-type-schema
        (remove-empty-vals {:identifier identifier
                            :base base
                            :description description
                            :fields fields
                            :nested nested
                            :dependencies depends})]

    (cons resource-type-schema
          binding-type-schemas)))

(defn translate-value-set [value-set]
  (let [identifier  (identifier/value-set-type (:url value-set))
        description (:description value-set)

        concepts    (value-set/value-set->concepts value-set)

        compose     (when (empty? concepts)
                      (:compose value-set))
        ;; FIXME: collect deps
        depends     []]

    (remove-empty-vals {:identifier   identifier
                        :description  description
                        :concept      concepts
                        :compose      compose
                        :dependencies depends})))
