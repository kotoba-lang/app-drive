#!/usr/bin/env bb
;; lg-drive — bb-native test runner (Clojure / babashka; no shell). ADR-2606280030.
;;
;; Per the repo-wide rule (root CLAUDE.md §"Operational code = clj/bb"): first-party
;; tooling is clj/bb, NOT shell. This is the clj twin of `python3 -m pytest tests/`.
;;
;;   cd 60-apps/etzhayyim-project-drive/lg && bb run_tests.clj
;;
;; The handler suite needs no external deps; the graph/server suite loads
;; langgraph-clj (resolved via this dir's bb.edn :deps).
(require '[clojure.test :as t])

(def suites
  '[lg-drive.test-handlers
    lg-drive.test-graph])

(apply require suites)

(let [{:keys [fail error]} (apply t/run-tests suites)]
  (if (zero? (+ fail error))
    (println "── lg-drive (clj): ALL suites green ──")
    (do (println "── lg-drive (clj): FAILURES above ──")
        (System/exit 1))))
