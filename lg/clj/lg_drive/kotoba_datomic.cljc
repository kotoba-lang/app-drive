(ns lg-drive.kotoba-datomic
  "kotoba datomic XRPC client for lg-drive — clj twin of lg_drive/kotoba_datomic.py.

  Wraps `ai.etzhayyim.apps.kotoba.datomic.{transact,q,pull}` over the graph
  `drive-v1`. httpx → babashka.http-client; JSON → cheshire (ADR-2606280030).
  Endpoint = KOTOBA_XRPC_URL|KOTOBA_URL (default in-cluster service); auth =
  Bearer JWT (KOTOBA_BEARER)."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [lg-drive.edn :as edn])
  (:import [java.security MessageDigest]))

(def default-config {:xrpc-url "http://kotoba.kotoba.svc.cluster.local:8080"
                     :bearer "" :graph-label "drive-v1"})
(def ^:dynamic *http-post* nil)

(def ^:private b32-alphabet "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")

(defn- base32-nopad
  "RFC4648 base32 (no padding) of byte-array — Java stdlib has no base32."
  [^bytes data]
  (let [bits (mapcat (fn [b] (let [v (bit-and b 0xff)]
                               (map #(bit-and (bit-shift-right v %) 1) (range 7 -1 -1))))
                     (seq data))
        groups (partition-all 5 bits)]
    (apply str (map (fn [g]
                      (let [g (concat g (repeat (- 5 (count g)) 0))]
                        (nth b32-alphabet (reduce (fn [a x] (+ (* a 2) x)) 0 g))))
                    groups))))

(defn kotoba-cid
  "CIDv1 (raw / sha2-256) base32-multibase of payload bytes."
  [^bytes payload]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") payload)
        cid (byte-array (concat (map unchecked-byte [0x01 0x71 0x12 0x20]) (seq digest)))]
    (str "b" (str/lower-case (base32-nopad cid)))))

(defn graph-cid-for-label
  "Stable kotoba graph CID from a human-readable label (multibase passthrough)."
  [label]
  (if (and (str/starts-with? label "b") (re-matches #"b[a-z2-7]{58,80}" label))
    label
    (kotoba-cid (.getBytes ^String label "UTF-8"))))

(def default-graph (graph-cid-for-label (:graph-label default-config)))

(defn ^:private bare-attr
  "':cal/summary' / :cal/summary → 'cal/summary'."
  [a]
  (let [s (if (keyword? a) (subs (str a) 1) (str a))]
    (if (str/starts-with? s ":") (subs s 1) s)))

(defn datoms->attr-map
  "Fold pull datoms ({:a :v_edn :added}) into {:bare/attr value}, or nil."
  [datoms]
  (when (seq datoms)
    (let [out (reduce (fn [m d]
                        (if (or (not (map? d)) (false? (get d "added")))
                          m
                          (let [a (get d "a")]
                            (if-not a
                              m
                              (assoc m (keyword (bare-attr a))
                                     (edn/parse-edn-value (get d "v_edn" "")))))))
                      {} datoms)]
      (when (seq out) out))))

;; ── client record ────────────────────────────────────────────────────────────

(defrecord KotobaDatomic [graph config])

(defn make-client
  ([] (make-client default-config))
  ([config-or-graph]
   (if (map? config-or-graph)
     (let [cfg (merge default-config config-or-graph)]
       (->KotobaDatomic (graph-cid-for-label (:graph-label cfg)) cfg))
     (->KotobaDatomic config-or-graph default-config))))

(defn- post-json [dm path body]
  (when-not (fn? *http-post*)
    (throw (ex-info "Drive Kotoba client requires an explicit HTTP POST capability"
                    {:capability :drive/kotoba-http-post})))
  (let [{:keys [xrpc-url bearer]} (:config dm)
        headers (cond-> {"Content-Type" "application/json"}
                  (seq bearer) (assoc "Authorization" (str "Bearer " bearer)))]
  (*http-post* (str (str/replace xrpc-url #"/+$" "") "/xrpc/" path)
             {:headers headers
                  :body (json/generate-string body)
                  :throw false})))

(defn transact
  ([dm ops] (transact dm ops nil))
  ([dm ops expected-parent]
   (let [body (cond-> {:graph (:graph dm) :tx_edn (edn/encode-tx-data ops)}
                expected-parent (assoc :expected_parent expected-parent))
         resp (post-json dm "ai.etzhayyim.apps.kotoba.datomic.transact" body)]
     (when (>= (:status resp) 400)
       (throw (ex-info "kotoba transact failed" {:status (:status resp) :body (:body resp)})))
     (json/parse-string (:body resp) true))))

(defn q
  ([dm query-edn] (q dm query-edn nil))
  ([dm query-edn inputs-edn]
   (let [body (cond-> {:graph (:graph dm) :query_edn query-edn}
                inputs-edn (assoc :inputs_edn inputs-edn))
         resp (post-json dm "ai.etzhayyim.apps.kotoba.datomic.q" body)]
     (when (>= (:status resp) 400)
       (throw (ex-info "kotoba q failed" {:status (:status resp) :body (:body resp)})))
     (let [rows (get (json/parse-string (:body resp)) "rows_edn" [])]
       (mapv (fn [row] (mapv edn/parse-edn-value row)) rows)))))

(defn pull
  "Return the entity as {:bare/attr value}, or nil on miss (404)."
  [dm entity]
  (let [body {:graph (:graph dm) :entity entity}
        resp (post-json dm "ai.etzhayyim.apps.kotoba.datomic.pull" body)]
    (cond
      (= 404 (:status resp)) nil
      (>= (:status resp) 400) (throw (ex-info "kotoba pull failed"
                                              {:status (:status resp) :body (:body resp)}))
      :else (datoms->attr-map (get (json/parse-string (:body resp)) "datoms")))))
