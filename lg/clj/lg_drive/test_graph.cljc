(ns lg-drive.test-graph
  "langgraph-clj StateGraph + server-routing tests for lg-drive (ADR-2606280030).
  Verifies the health graph compiles + invokes (START→probe→END) and the pure
  `route` dispatch over the FakeDriveStore (health + xrpc create/get + auth)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [lg-drive.graphs.health :as health]
            [lg-drive.server :as server]
            [lg-drive.kotoba-datomic :as kd]
            [lg-drive.store :as store]))

(deftest test-health-graph-invoke
  (let [out (g/invoke health/GRAPH {})]
    (is (true? (:ok out)))
    (is (= "0.1.0" (:version out)))
    (is (integer? (:ts out)))))

(deftest test-route-health
  (let [st (store/fake-store)
        res (server/route st {:method :get :path "/health"})]
    (is (= 200 (:status res)))
    (is (true? (get-in res [:json "ok"])))
    (is (= "lg-drive" (get-in res [:json "app"])))))

(deftest test-route-xrpc-create-get
  (let [st (store/fake-store)
        created (server/route st {:method :post
                                  :path "/xrpc/ai.etzhayyim.apps.drive.filesCreate"
                                  :body {"name" "r.txt" "sizeBytes" 5}})
        fid (get-in created [:json "fileId"])
        got (server/route st {:method :get
                              :path "/xrpc/ai.etzhayyim.apps.drive.filesGet"
                              :query {"fileId" fid}})]
    (is (= 200 (:status created)))
    (is (= "r.txt" (get-in created [:json "file" "name"])))
    (is (true? (get-in got [:json "found"])))))

(deftest test-parse-query
  (is (= {"fileId" "abc" "q" "a b"} (server/parse-query "fileId=abc&q=a%20b")))
  (is (= {} (server/parse-query nil))))

(deftest test-route-unknown-method
  (let [st (store/fake-store)
        res (server/route st {:method :get
                              :path "/xrpc/ai.etzhayyim.apps.drive.nope"})]
    (is (= 404 (:status res)))
    (is (str/includes? (get-in res [:json "error"]) "not found"))))

(deftest test-explicit-kotoba-capability-and-config
  (let [request (atom nil)
        dm (kd/make-client {:xrpc-url "http://kotoba.internal/"
                            :bearer "bound" :graph-label "drive-safe"})]
    (binding [kd/*http-post* (fn [url opts]
                               (reset! request [url opts])
                               {:status 200 :body "{\"rows_edn\":[]}"})]
      (is (= [] (kd/q dm "[:find ?e]")))
      (is (= "http://kotoba.internal/xrpc/ai.etzhayyim.apps.kotoba.datomic.q"
             (first @request)))
      (is (= "Bearer bound" (get-in @request [1 :headers "Authorization"]))))))

(deftest test-explicit-auth-and-health-version
  (let [res (server/route (store/fake-store)
                          {:method :get
                           :path "/xrpc/ai.etzhayyim.apps.drive.filesList"
                           :expected-api-key "secret" :x-api-key "wrong"})]
    (is (= 401 (:status res))))
  (is (= "explicit" (:version (g/invoke health/GRAPH
                                         {:host-config {:version "explicit"}})))))
