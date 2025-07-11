(ns type-schema.log)

(defn info [verbose & msgs]
  (when verbose
    (apply println "INFO: " msgs)))

(defn warn [& msg]
  (binding [*out* *err*]
    (apply println "WARN:" msg)))
