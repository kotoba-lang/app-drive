(ns lg-drive.server
  "HTTP server for lg-drive — clj twin of lg_drive/server.py (ADR-2606280030).

  Surfaces the canonical drive XRPC methods the atproto actor-worker pipethrough
  forwards to (drive.etzhayyim.com/xrpc/... → lg-drive:8000/xrpc/...):

    GET  /health /ok
    POST /xrpc/ai.etzhayyim.apps.drive.filesCreate
    GET  /xrpc/ai.etzhayyim.apps.drive.filesGet
    GET  /xrpc/ai.etzhayyim.apps.drive.filesList
    POST /xrpc/ai.etzhayyim.apps.drive.filesUpdate
    POST /xrpc/ai.etzhayyim.apps.drive.filesDelete
    GET  /xrpc/ai.etzhayyim.apps.drive.about
    GET  /xrpc/ai.etzhayyim.apps.drive.changes

  FastAPI → babashka httpkit; pydantic body → cheshire JSON. Persistence =
  kotoba datomic (graph `drive-v1`). `route` is a pure dispatch fn (store, request)
  so the routing is unit-testable without binding a socket."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [lg-drive.handlers :as h]
            [lg-drive.kotoba-datomic :as kd]
            [lg-drive.store :as store])
  (:import [java.net URLDecoder]))

;; ── auth (Python `_enforce_auth`) ────────────────────────────────────────────

(defn enforce-auth!
  "Throw a 401 ex-info if LG_DRIVE_API_KEY is set and x-api-key mismatches."
  [expected x-api-key]
  (when (and expected (not (str/blank? expected))
               (or (str/blank? (or x-api-key "")) (not= x-api-key expected)))
      (throw (ex-info "x-api-key mismatch" {:status 401}))))

;; ── query-string parsing ──────────────────────────────────────────────────────

(defn parse-query [qs]
  (if (str/blank? qs)
    {}
    (into {}
          (for [pair (str/split qs #"&")
                :let [[k v] (str/split pair #"=" 2)]
                :when (seq k)]
            [(URLDecoder/decode k "UTF-8")
             (URLDecoder/decode (or v "") "UTF-8")]))))

;; ── pure dispatch ─────────────────────────────────────────────────────────────

(def ^:private xrpc-prefix "/xrpc/ai.etzhayyim.apps.drive.")

(defn route
  "Pure router: (route store {:method :path :query :body :x-api-key}) →
  {:status int :json map}. `query` is a param map; `body` is a parsed map."
  [st {:keys [method path query body x-api-key expected-api-key]}]
  (let [query (or query {})
        body (or body {})]
    (cond
      (and (= method :get) (#{"/health" "/ok"} path))
      {:status 200 :json {"ok" true "app" "lg-drive" "ts" (System/currentTimeMillis)}}

      (str/starts-with? path xrpc-prefix)
      (try
        (enforce-auth! expected-api-key x-api-key)
        (let [m (subs path (count xrpc-prefix))]
          (case m
            "filesCreate" {:status 200 :json (h/files-create st body)}
            "filesUpdate" {:status 200 :json (h/files-update st body)}
            "filesDelete" {:status 200 :json (h/files-delete st body)}
            "filesGet"    {:status 200 :json (h/files-get st query)}
            "filesList"   {:status 200 :json (h/files-list st query)}
            "about"       {:status 200 :json (h/about st query)}
            "changes"     {:status 200 :json (h/changes st query)}
            {:status 404 :json {"error" "method not found" "method" m}}))
        (catch clojure.lang.ExceptionInfo e
          {:status (or (:status (ex-data e)) 500) :json {"error" (.getMessage e)}}))

      :else
      {:status 404 :json {"error" "not found" "path" path}})))

;; ── httpkit adapter (-main) ────────────────────────────────────────────────────

(defn ->ring-handler
  ([st expected-api-key] (->ring-handler st expected-api-key nil))
  ([st expected-api-key http-post]
  (fn [{:keys [request-method uri query-string body headers]}]
    (binding [kd/*http-post* http-post]
    (let [method (keyword (name (or request-method :get)))
          parsed-body (when body
                        (try (json/parse-string (slurp body)) (catch Exception _ nil)))
          {:keys [status json]} (route st {:method method
                                           :path uri
                                           :query (parse-query query-string)
                                           :body parsed-body
                                           :expected-api-key expected-api-key
                                           :x-api-key (get headers "x-api-key")})]
      {:status status
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string json)})))))

(defn run-server-with [run-server port st expected-api-key http-post]
  (when-not (fn? run-server)
    (throw (ex-info "server capability not configured" {:capability :run-server})))
  (when-not (and (integer? port) (<= 1 port 65535))
    (throw (ex-info "invalid server port" {:port port})))
  (run-server (->ring-handler st expected-api-key http-post) {:port port}))

(defn -main [& _]
  (throw (ex-info "host adapter must provide server, port, store and API key"
                  {:capability :run-server})))
