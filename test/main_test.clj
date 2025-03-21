(ns main-test
  (:require
   [clojure.test :refer [deftest is]]
   [main]))

(deftest smoke-test
  (is (= :ok (main/process-package "hl7.fhir.r4.core@4.0.1" "output")))
  (is (= :ok (main/process-package "hl7.fhir.r5.core" "output")))
  (is (= :ok (main/process-package "hl7.fhir.us.core@6.1.0" "output"))))
